package br.com.akesofertas.controller;

import br.com.akesofertas.service.MercadoLivreAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MercadoLivreAuthControllerTest {
    private static final String REDIRECT = "https://app.example/mercadolivre/callback";
    private static final String TOKEN_JSON = """
            {"access_token":"access-test","refresh_token":"refresh-test","token_type":"Bearer",
             "expires_in":21600,"user_id":123,"scope":"read offline_access"}
            """;
    private MockRestServiceServer server;
    private MockMvc mvc;
    private MockHttpSession session;
    private String state;
    private String challenge;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        mvc = MockMvcBuilders.standaloneSetup(new MercadoLivreAuthController(
                new MercadoLivreAuthService("12345", "secret-test", REDIRECT, true, builder))).build();
        session = new MockHttpSession();
    }

    private void iniciar() throws Exception {
        var result = mvc.perform(get("/mercadolivre/auth").session(session))
                .andExpect(status().isFound()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).startsWith("https://auth.mercadolivre.com.br/authorization?")
                .doesNotContain("secret-test");
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(query.getFirst("client_id")).isEqualTo("12345");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
        state = query.getFirst("state");
        challenge = query.getFirst("code_challenge");
        assertThat(state).hasSize(43);
    }

    private void esperarTroca() {
        server.expect(requestTo("https://api.mercadolibre.com/oauth/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                            .getBodyAsString();
                    String verifier = java.util.Arrays.stream(body.split("&"))
                            .filter(part -> part.startsWith("code_verifier="))
                            .map(part -> part.substring("code_verifier=".length())).findFirst().orElseThrow();
                    String hash;
                    try {
                        hash = Base64.getUrlEncoder().withoutPadding().encodeToString(
                                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                    assertThat(hash).isEqualTo(challenge);
                    var form = new LinkedMultiValueMap<String, String>();
                    form.add("grant_type", "authorization_code");
                    form.add("client_id", "12345");
                    form.add("client_secret", "secret-test");
                    form.add("code", "code-test");
                    form.add("redirect_uri", REDIRECT);
                    form.add("code_verifier", verifier);
                    org.springframework.test.web.client.match.MockRestRequestMatchers.content().formData(form).match(request);
                }).andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
    }

    private void callback() throws Exception {
        mvc.perform(get("/mercadolivre/callback").session(session).param("code", "code-test").param("state", state))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/mercadolivre/me"))
                .andExpect(content().string("")).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void autenticaArmazenaTokensEConsultaMeSemExporCredenciais() throws Exception {
        iniciar();
        String sessionIdAntes = session.getId();
        esperarTroca();
        server.expect(requestTo("https://api.mercadolibre.com/users/me"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-test"))
                .andRespond(withSuccess("{\"id\":123,\"nickname\":\"USUARIO_TESTE\"}", MediaType.APPLICATION_JSON));
        callback();
        assertThat(session.getId()).isNotEqualTo(sessionIdAntes);
        mvc.perform(get("/mercadolivre/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(session.getAttribute("mercadolivre.tokens").toString()).doesNotContain("access-test", "refresh-test");
        server.verify();
    }

    @Test
    void rejeitaStateAlteradoSemChamarApi() throws Exception {
        iniciar();
        mvc.perform(get("/mercadolivre/callback").session(session).param("code", "code-test").param("state", "outro"))
                .andExpect(status().isBadRequest());
        server.verify();
    }

    @Test
    void rejeitaCallbackEmOutroNavegador() throws Exception {
        iniciar();
        mvc.perform(get("/mercadolivre/callback").session(new MockHttpSession())
                        .param("code", "code-test").param("state", state))
                .andExpect(status().isBadRequest());
        server.verify();
    }

    @Test
    void rejeitaCallbackSemSessao() throws Exception {
        mvc.perform(get("/mercadolivre/callback").param("code", "code-test").param("state", "qualquer"))
                .andExpect(status().isBadRequest());
        server.verify();
    }

    @Test
    void rejeitaCodeAusente() throws Exception {
        iniciar();
        mvc.perform(get("/mercadolivre/callback").session(session).param("state", state))
                .andExpect(status().isBadRequest());
        server.verify();
    }

    @Test
    void trataConsentimentoNegadoEConsomeState() throws Exception {
        iniciar();
        mvc.perform(get("/mercadolivre/callback").session(session).param("state", state).param("error", "access_denied"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").value(
                        "Autorização não concedida. Inicie novamente em /mercadolivre/auth."));
        assertThat(session.getAttribute("mercadolivre.oauth")).isNull();
        server.verify();
    }

    @Test
    void callbackNaoPodeSerReutilizado() throws Exception {
        iniciar();
        esperarTroca();
        callback();
        mvc.perform(get("/mercadolivre/callback").session(session).param("code", "code-test").param("state", state))
                .andExpect(status().isBadRequest());
        server.verify();
    }

    @Test
    void meExigeSessaoAutenticada() throws Exception {
        mvc.perform(get("/mercadolivre/me")).andExpect(status().isUnauthorized());
        server.verify();
    }

    @Test
    void sanitizaErroDoServidorDeTokens() throws Exception {
        iniciar();
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant secret-test code-test\"}"));
        var result = mvc.perform(get("/mercadolivre/callback").session(session)
                        .param("code", "code-test").param("state", state))
                .andExpect(status().isBadGateway()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret-test", "code-test");
        assertThat(session.getAttribute("mercadolivre.tokens")).isNull();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":10}"})
    void naoArmazenaRespostaDeTokenInvalida(String body) throws Exception {
        iniciar();
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        mvc.perform(get("/mercadolivre/callback").session(session).param("code", "code-test").param("state", state))
                .andExpect(status().isBadGateway());
        assertThat(session.getAttribute("mercadolivre.tokens")).isNull();
        server.verify();
    }

    @Test
    void timeoutNaoRepeteCodeAutomaticamente() throws Exception {
        iniciar();
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("secret-test")));
        mvc.perform(get("/mercadolivre/callback").session(session).param("code", "code-test").param("state", state))
                .andExpect(status().isBadGateway());
        assertThat(session.getAttribute("mercadolivre.oauth")).isNull();
        server.verify();
    }

    @Test
    void tokenRevogadoRemoveCredenciaisDaSessao() throws Exception {
        iniciar();
        esperarTroca();
        server.expect(requestTo("https://api.mercadolibre.com/users/me")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        callback();
        mvc.perform(get("/mercadolivre/me").session(session)).andExpect(status().isBadGateway());
        assertThat(session.getAttribute("mercadolivre.tokens")).isNull();
        server.verify();
    }
}
