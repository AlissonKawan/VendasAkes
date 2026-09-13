package br.com.akesofertas.controller;

import br.com.akesofertas.client.MercadoLivreProdutosClient;
import br.com.akesofertas.provider.MercadoLivreProvider;
import br.com.akesofertas.provider.ProdutoEncontrado;
import br.com.akesofertas.provider.ProdutoProvider;
import br.com.akesofertas.service.ProdutoBuscaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProdutoControllerTest {
    private static final String ENDPOINT = "https://api.mercadolibre.com/products/search?status=active&site_id=MLB&q=mouse&limit=5";
    private static final String CATALOGO = """
            {"results":[{"id":"MLB123456789","name":"Mouse de exemplo","status":"active",
              "pictures":[{"url":"http://http2.mlstatic.com/exemplo.jpg","secure_url":"https://http2.mlstatic.com/exemplo.jpg"}]}]}
            """;
    private MockRestServiceServer server;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        mvc = mvc(new MercadoLivreProdutosClient(() -> "token-simulado", builder));
    }

    private MockMvc mvc(MercadoLivreProdutosClient client) {
        return MockMvcBuilders.standaloneSetup(new ProdutoController(new ProdutoBuscaService(
                List.of(new MercadoLivreProvider(client))))).build();
    }

    @Test
    void buscaSomenteNaApiOficialComBearerERetornaModeloComum() throws Exception {
        server.expect(requestTo(ENDPOINT)).andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization", "Bearer token-simulado"))
                .andExpect(headerDoesNotExist("Cookie"))
                .andRespond(withSuccess(CATALOGO, MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/produtos").param("q", "mouse"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].fornecedor").value("mercadolivre"))
                .andExpect(jsonPath("$[0].id").value("MLB123456789"))
                .andExpect(jsonPath("$[0].urlProduto").value("https://www.mercadolivre.com.br/p/MLB123456789"))
                .andExpect(jsonPath("$[0].imagemUrl").value("https://http2.mlstatic.com/exemplo.jpg"))
                .andExpect(jsonPath("$[0].precoAtual").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("token-simulado"))));
        server.verify();
    }

    @Test
    void codificaTermoSemInjetarParametrosExtras() throws Exception {
        server.expect(requestTo("https://api.mercadolibre.com/products/search?status=active&site_id=MLB&q=mouse%20%26%20limit%3D999&limit=5"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/produtos").param("q", "mouse & limit=999"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        server.verify();
    }

    @Test
    void dadosInvalidosNaoChegamAoMercadoLivre() throws Exception {
        mvc.perform(get("/api/produtos")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/produtos").param("q", " ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/produtos").param("q", "x".repeat(121))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/produtos").param("q", "mouse").param("limite", "21")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/produtos").param("q", "mouse").param("limite", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/produtos").param("q", "mouse").param("limite", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/produtos").param("q", "mouse").param("fornecedor", "amazon"))
                .andExpect(status().isBadRequest());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void propagaErroLegivelSemCredenciaisENaoTentaOutraRota(int codigo) throws Exception {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.valueOf(codigo))
                .body("segredo-na-resposta-remota").contentType(MediaType.TEXT_PLAIN));
        mvc.perform(get("/api/produtos").param("q", "mouse"))
                .andExpect(status().is(codigo == 401 ? 401 : 502))
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("segredo-na-resposta-remota"))));
        server.verify();
    }

    @Test
    void exigeAutenticacaoAntesDaChamada() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer semChamadas = MockRestServiceServer.bindTo(builder).build();
        mvc = mvc(new MercadoLivreProdutosClient(() -> null, builder));
        mvc.perform(get("/api/produtos").param("q", "mouse")).andExpect(status().isUnauthorized());
        semChamadas.verify();
    }

    @Test
    void respostaSemResultadosNaoViraListaVaziaDeSucesso() throws Exception {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/produtos").param("q", "mouse")).andExpect(status().isBadGateway());
        server.verify();
    }

    @Test
    void timeoutNaoDisparaRetry() throws Exception {
        server.expect(requestTo(ENDPOINT)).andRespond(withException(new SocketTimeoutException("segredo")));
        mvc.perform(get("/api/produtos").param("q", "mouse")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensagem").value("Falha de comunicação com a API oficial de produtos."));
        server.verify();
    }

    @Test
    void novoProviderParticipaSemAlterarServicoDeBusca() {
        ProdutoProvider futuro = new ProdutoProvider() {
            public String codigo() { return "outro"; }
            public List<ProdutoEncontrado> buscar(String termo, int limite) {
                return List.of(new ProdutoEncontrado(codigo(), "123", termo, null, null));
            }
        };
        assertEquals("outro", new ProdutoBuscaService(List.of(futuro)).buscar("outro", "mouse", 5).getFirst().fornecedor());
    }
}
