package br.com.akesofertas.controller;

import br.com.akesofertas.client.*;
import br.com.akesofertas.provider.*;
import br.com.akesofertas.service.OfertaBuscaService;
import br.com.akesofertas.service.ProdutoBuscaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OfertaBuscaControllerTest {
    // Todos os preços e anúncios abaixo são fixtures simuladas, não consultas reais.
    private static final String BASE = "https://api.mercadolibre.com";
    private static final String ENDPOINT = "/api/ofertas/buscar";
    private static final String VENCEDOR = """
            {"item_id":"MLB123","seller_id":123,"price":139.90,"original_price":199.90,
             "currency_id":"BRL","available_quantity":5,
             "shipping":{"mode":"me2","free_shipping":true,"tags":["fulfillment"],"logistic_type":"fulfillment"}}
            """;
    private static final String PRECO = """
            {"id":"p1","type":"promotion","amount":139.90,"regular_amount":199.90,"currency_id":"BRL",
             "conditions":{"context_restrictions":["channel_marketplace"],"start_time":null,"end_time":null}}
            """;
    private MockRestServiceServer server;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var http = new MercadoLivreConsultaClient(() -> "token-simulado", builder);
        var produtos = new ProdutoBuscaService(List.of(new MercadoLivreProvider(new MercadoLivreProdutosClient(() -> "token-simulado", builder))));
        var provider = new MercadoLivreOfertaProvider(new MercadoLivreCatalogItemsClient(http), new MercadoLivrePricesClient(http));
        mvc = MockMvcBuilders.standaloneSetup(new OfertaBuscaController(new OfertaBuscaService(produtos, List.of(provider)))).build();
    }

    private void resposta(String path, String json) {
        server.expect(requestTo(BASE + path)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-simulado"))
                .andExpect(headerDoesNotExist("Cookie"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void busca(String... ids) {
        String resultados = String.join(",", java.util.Arrays.stream(ids)
                .map(id -> "{\"id\":\"" + id + "\",\"name\":\"Produto simulado\",\"status\":\"active\"}").toList());
        resposta("/products/search?status=active&site_id=MLB&q=mouse&limit=10", "{\"results\":[" + resultados + "]}");
    }

    private void produto(String id, String vencedor) {
        resposta("/products/" + id, "{\"id\":\"" + id + "\",\"status\":\"active\",\"permalink\":\"https://www.mercadolivre.com.br/p/" + id
                + "\",\"buy_box_winner\":" + vencedor + "}");
    }

    private void precos(String entradas) { resposta("/items/MLB123/prices", "{\"id\":\"MLB123\",\"prices\":[" + entradas + "]}"); }

    @Test
    void continuaAposBuyBoxNulaEConfirmaVencedorSemConsultarConcorrentes() throws Exception {
        busca("MLB47622919", "MLB456");
        resposta("/products/MLB47622919", "{\"id\":\"MLB47622919\",\"status\":\"active\",\"permalink\":\"\",\"buy_box_winner\":null}");
        produto("MLB456", VENCEDOR);
        precos(PRECO);
        mvc.perform(get(ENDPOINT).param("q", "mouse")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.candidatosAvaliados").value(2))
                .andExpect(jsonPath("$.candidatos[0].ofertaUtilizavel").value(false))
                .andExpect(jsonPath("$.candidatos[0].motivo", containsString("buy_box_winner nulo")))
                .andExpect(jsonPath("$.ofertas.length()").value(1))
                .andExpect(jsonPath("$.ofertas[0].anuncioId").value("MLB123"))
                .andExpect(jsonPath("$.ofertas[0].vendedorId").value(123))
                .andExpect(jsonPath("$.ofertas[0].precoAtual").value(139.90))
                .andExpect(jsonPath("$.ofertas[0].precoRegular").value(199.90))
                .andExpect(jsonPath("$.ofertas[0].percentualDesconto").value(30.02))
                .andExpect(jsonPath("$.ofertas[0].precoPromocional").value(true))
                .andExpect(jsonPath("$.ofertas[0].entrega.freteGratis").value(true))
                .andExpect(jsonPath("$.ofertas[0].quantidadeDisponivel").value(5));
        // Qualquer GET /products/{id}/items inesperado faz o MockRestServiceServer falhar.
        server.verify();
    }

    @Test
    void ambosCatalogosReaisSemVencedorRetornamDescartesSemFalhaGlobal() throws Exception {
        busca("MLB47622919", "MLB73798525");
        produto("MLB47622919", "null");
        produto("MLB73798525", "null");
        mvc.perform(get(ENDPOINT).param("q", "mouse")).andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatosAvaliados").value(2)).andExpect(jsonPath("$.ofertas").isEmpty());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recurso404DescartaSomenteCandidatoEContinua(boolean emPrecos) throws Exception {
        busca("MLB111", "MLB222");
        if (emPrecos) produto("MLB111", VENCEDOR);
        server.expect(requestTo(BASE + (emPrecos ? "/items/MLB123/prices" : "/products/MLB111")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        produto("MLB222", VENCEDOR);
        precos(PRECO);
        mvc.perform(get(ENDPOINT).param("q", "mouse")).andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatos[0].motivo", containsString("404")))
                .andExpect(jsonPath("$.ofertas[0].produtoId").value("MLB222"));
        server.verify();
    }

    @Test
    void naoInventaOriginalDescontoEstoqueOuFreteAusentes() throws Exception {
        busca("MLB111");
        produto("MLB111", "{\"item_id\":\"MLB123\",\"seller_id\":123,\"price\":139.90,\"currency_id\":\"BRL\"}");
        precos(PRECO.replace("promotion", "standard").replace("199.90", "null"));
        mvc.perform(get(ENDPOINT).param("q", "mouse")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ofertas.length()").value(1))
                .andExpect(jsonPath("$.ofertas[0].precoRegular").value(nullValue()))
                .andExpect(jsonPath("$.ofertas[0].percentualDesconto").value(nullValue()))
                .andExpect(jsonPath("$.ofertas[0].quantidadeDisponivel").value(nullValue()))
                .andExpect(jsonPath("$.ofertas[0].entrega").value(nullValue()))
                .andExpect(jsonPath("$.ofertas[0].precoPromocional").value(false));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"buyer_loyalty_6", "channel_mshops", "user_type_business", "contexto_desconhecido"})
    void naoConsideraPrecoRestritoComoOfertaGeral(String contexto) throws Exception {
        busca("MLB111");
        produto("MLB111", VENCEDOR);
        precos(PRECO.replace("\"channel_marketplace\"", "\"channel_marketplace\",\"" + contexto + "\""));
        mvc.perform(get(ENDPOINT).param("q", "mouse")).andExpect(status().isOk()).andExpect(jsonPath("$.ofertas").isEmpty());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"divergente", "moeda", "original", "ambiguo", "futuro", "expirado", "quantidade", "condicoes", "vazio", "tipo"})
    void descartaPrecosNaoConfiaveis(String caso) throws Exception {
        busca("MLB111");
        produto("MLB111", VENCEDOR);
        String valores = switch (caso) {
            case "divergente" -> PRECO.replace("139.90", "120");
            case "moeda" -> PRECO.replace("BRL", "USD");
            case "original" -> PRECO.replace("199.90", "299.90");
            case "ambiguo" -> PRECO + "," + PRECO.replace("p1", "p2");
            case "futuro" -> PRECO.replace("\"start_time\":null", "\"start_time\":\"2099-01-01T00:00:00Z\"");
            case "expirado" -> PRECO.replace("\"end_time\":null", "\"end_time\":\"2000-01-01T00:00:00Z\"");
            case "quantidade" -> PRECO.replace("\"start_time\":null", "\"min_purchase_unit\":2");
            case "condicoes" -> PRECO.replace("\"context_restrictions\":[\"channel_marketplace\"]", "\"context_restrictions\":null");
            case "tipo" -> PRECO.replace("promotion", "outro");
            default -> "";
        };
        precos(valores);
        mvc.perform(get(ENDPOINT).param("q", "mouse")).andExpect(status().isOk()).andExpect(jsonPath("$.ofertas").isEmpty())
                .andExpect(jsonPath("$.candidatos[0].motivo", containsString("Preço não confirmado")));
        server.verify();
    }

    @Test
    void naoEscolheMenorPrecoRestritoNemUsaStandardComoPrecoAnterior() throws Exception {
        busca("MLB111");
        produto("MLB111", VENCEDOR.replace("199.90", "null"));
        String atual = PRECO.replace("199.90", "null");
        String restrito = PRECO.replace("p1", "p2").replace("139.90", "90")
                .replace("channel_marketplace", "buyer_loyalty_6");
        String standard = PRECO.replace("p1", "p3").replace("promotion", "standard").replace("139.90", "199.90");
        precos(atual + "," + restrito + "," + standard);
        mvc.perform(get(ENDPOINT).param("q", "mouse")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ofertas[0].precoAtual").value(139.90))
                .andExpect(jsonPath("$.ofertas[0].precoRegular").value(nullValue()))
                .andExpect(jsonPath("$.ofertas[0].percentualDesconto").value(nullValue()));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"item", "seller", "estoque", "url"})
    void vencedorInvalidoNaoConsultaPrecos(String caso) throws Exception {
        busca("MLB111");
        if ("url".equals(caso)) resposta("/products/MLB111", "{\"id\":\"MLB111\",\"status\":\"active\",\"permalink\":\"\",\"buy_box_winner\":" + VENCEDOR + "}");
        else produto("MLB111", switch (caso) {
            case "item" -> VENCEDOR.replace("MLB123", "https://example.com");
            case "seller" -> VENCEDOR.replace("\"seller_id\":123", "\"seller_id\":null");
            default -> VENCEDOR.replace("\"available_quantity\":5", "\"available_quantity\":0");
        });
        mvc.perform(get(ENDPOINT).param("q", "mouse")).andExpect(status().isOk()).andExpect(jsonPath("$.ofertas").isEmpty());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void falhaRealNaoViraBuscaVaziaNemContinuaChamadas(int codigo) throws Exception {
        busca("MLB111", "MLB222");
        produto("MLB111", VENCEDOR);
        server.expect(requestTo(BASE + "/items/MLB123/prices"))
                .andRespond(withStatus(HttpStatus.valueOf(codigo)).body("segredo-remoto"));
        mvc.perform(get(ENDPOINT).param("q", "mouse")).andExpect(status().is(codigo == 401 ? 401 : 502))
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(content().string(not(containsString("segredo-remoto"))));
        server.verify();
    }

    @Test
    void validaEntradaENaoConsultaMesmoCatalogoDuasVezes() throws Exception {
        mvc.perform(get(ENDPOINT)).andExpect(status().isBadRequest());
        mvc.perform(get(ENDPOINT).param("q", "mouse").param("limite", "21")).andExpect(status().isBadRequest());
        mvc.perform(get(ENDPOINT).param("q", "mouse").param("fornecedor", "desconhecido")).andExpect(status().isBadRequest());
        busca("MLB111", "MLB111");
        produto("MLB111", "null");
        mvc.perform(get(ENDPOINT).param("q", "mouse")).andExpect(status().isOk()).andExpect(jsonPath("$.candidatosAvaliados").value(1));
        server.verify();
    }
}
