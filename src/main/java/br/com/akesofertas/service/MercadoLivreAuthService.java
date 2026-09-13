package br.com.akesofertas.service;

import br.com.akesofertas.dto.MercadoLivreTokenResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class MercadoLivreAuthService {
    private static final String FLUXO = "mercadolivre.oauth";
    private static final String TOKENS = "mercadolivre.tokens";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String appId;
    private final String clientSecret;
    private final String redirectUri;
    private final boolean pkce;
    private final RestClient http;

    @Autowired
    public MercadoLivreAuthService(
            @Value("${mercadolivre.app-id:}") String appId,
            @Value("${mercadolivre.client-secret:}") String clientSecret,
            @Value("${mercadolivre.redirect-uri:}") String redirectUri,
            @Value("${mercadolivre.pkce-enabled:true}") boolean pkce) {
        this(appId, clientSecret, redirectUri, pkce, httpBuilder());
    }

    // Receber o builder também permite simular o Mercado Livre nos testes.
    public MercadoLivreAuthService(String appId, String clientSecret, String redirectUri,
                                   boolean pkce, RestClient.Builder builder) {
        this.appId = appId.trim();
        this.clientSecret = clientSecret.trim();
        this.redirectUri = redirectUri.trim();
        this.pkce = pkce;
        this.http = builder.baseUrl("https://api.mercadolibre.com").build();
    }

    public URI iniciar(HttpSession session) {
        validarConfiguracao();
        // state associa o retorno à sessão que iniciou o login, evitando callbacks forjados.
        String state = aleatorio();
        String verifier = pkce ? aleatorio() : null;
        session.setAttribute(FLUXO, new Fluxo(state, verifier, Instant.now().plusSeconds(600)));
        var url = UriComponentsBuilder.fromUriString("https://auth.mercadolivre.com.br/authorization")
                .queryParam("response_type", "code")
                .queryParam("client_id", appId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state);
        if (pkce) {
            // Apenas o hash vai ao navegador; o segredo verifier permanece no servidor.
            url.queryParam("code_challenge", desafio(verifier)).queryParam("code_challenge_method", "S256");
        }
        return url.build().encode().toUri();
    }

    public void concluir(HttpSession session, String code, String state, String error) {
        Fluxo fluxo = consumirFluxo(session, state);
        if (error != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Autorização não concedida. Inicie novamente em /mercadolivre/auth.");
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O callback não recebeu o parâmetro code.");
        }

        // A troca usa formulário HTTP, não JSON. O client_secret nunca vai ao navegador.
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", appId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        if (fluxo.verifier() != null) {
            form.add("code_verifier", fluxo.verifier());
        }
        try {
            MercadoLivreTokenResponse tokens = http.post().uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(MercadoLivreTokenResponse.class);
            validarTokens(tokens);
            // Ambos os tokens ficam somente na memória desta sessão, junto com a validade.
            session.setAttribute(TOKENS, new Credenciais(tokens, Instant.now().plusSeconds(tokens.expiresIn())));
        } catch (RestClientResponseException exception) {
            // Não repassa corpo/URL da exceção: eles podem conter informações sensíveis.
            throw erroRemoto(exception, "Falha ao trocar code por token. Confira credenciais, redirect URI e PKCE.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível concluir a troca de token. Inicie uma nova autorização.");
        }
    }

    public Map<?, ?> consultarUsuario(HttpSession session) {
        String accessToken = obterAccessToken(session);
        try {
            // Esta é a chamada real de teste: GET /users/me com Authorization: Bearer ACCESS_TOKEN.
            Map<?, ?> usuario = http.get().uri("/users/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(Map.class);
            if (usuario == null || !usuario.containsKey("id")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta inválida de /users/me.");
            }
            return usuario;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401) {
                session.removeAttribute(TOKENS);
            }
            throw erroRemoto(exception, "Não foi possível consultar /users/me. Confira a autorização da conta.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha de comunicação com /users/me.");
        }
    }

    // Uso interno exclusivo dos clientes da API oficial; nunca retornar por um controller.
    public String obterAccessToken(HttpSession session) {
        Credenciais credenciais = session == null ? null : (Credenciais) session.getAttribute(TOKENS);
        if (credenciais == null || !Instant.now().isBefore(credenciais.expiraEm())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Sessão não autenticada ou token expirado. Acesse /mercadolivre/auth.");
        }
        return credenciais.tokens().accessToken();
    }

    private Fluxo consumirFluxo(HttpSession session, String state) {
        if (session == null) {
            throw callbackInvalido();
        }
        // Consome o state uma única vez; duas requisições não podem reutilizar o mesmo fluxo.
        synchronized (session) {
            Fluxo fluxo = (Fluxo) session.getAttribute(FLUXO);
            if (fluxo == null || state == null || !fluxo.state().equals(state)
                    || !Instant.now().isBefore(fluxo.expiraEm())) {
                throw callbackInvalido();
            }
            session.removeAttribute(FLUXO);
            return fluxo;
        }
    }

    private ResponseStatusException callbackInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "state inválido, expirado ou sessão ausente. Reinicie em /mercadolivre/auth no mesmo navegador.");
    }

    private void validarConfiguracao() {
        if (appId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Defina MERCADOLIVRE_APP_ID, MERCADOLIVRE_CLIENT_SECRET e MERCADOLIVRE_REDIRECT_URI.");
        }
        try {
            URI uri = URI.create(redirectUri);
            if (("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getFragment() == null && uri.getUserInfo() == null) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // A resposta abaixo explica a configuração esperada.
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "MERCADOLIVRE_REDIRECT_URI deve ser uma URL HTTP/HTTPS válida cadastrada no Mercado Livre.");
    }

    private void validarTokens(MercadoLivreTokenResponse tokens) {
        if (tokens == null || tokens.accessToken() == null || tokens.accessToken().isBlank()
                || tokens.refreshToken() == null || tokens.refreshToken().isBlank()
                || tokens.expiresIn() <= 0 || !"bearer".equalsIgnoreCase(tokens.tokenType())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Resposta de token incompleta. Confira as permissões da aplicação, incluindo offline_access.");
        }
    }

    private ResponseStatusException erroRemoto(RestClientResponseException exception, String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                mensagem + " Mercado Livre retornou HTTP " + exception.getStatusCode().value() + ".");
    }

    private static String aleatorio() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String desafio(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível");
        }
    }

    private static RestClient.Builder httpBuilder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory);
    }

    // Objetos internos nunca são retornados pelos endpoints.
    private record Fluxo(String state, String verifier, Instant expiraEm) {}
    private record Credenciais(MercadoLivreTokenResponse tokens, Instant expiraEm) {}
}
