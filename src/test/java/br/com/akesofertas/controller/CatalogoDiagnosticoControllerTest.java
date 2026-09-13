package br.com.akesofertas.controller;

import br.com.akesofertas.client.MercadoLivreCatalogItemsClient;
import br.com.akesofertas.client.MercadoLivreConsultaClient;
import br.com.akesofertas.client.MercadoLivrePricesClient;
import br.com.akesofertas.service.CatalogoDiagnosticoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogoDiagnosticoControllerTest {
    private static final String BASE = "https://api.mercadolibre.com";
    private static final String ENDPOINT = "/api/produtos/MLB47622919/diagnostico-ofertas";
    // Fixture simulada; não representa uma consulta real desta conta/produto.
    private static final String CATALOGO = """
            {"paging":{"total":2,"offset":0,"limit":100},"results":[
             {"item_id":"MLB123","seller_id":123,"price":180,"condition":"new","campo_adicional":"visivel"},
             {"item_id":"MLB456","price":90,"currency_id":"BRL"}]}
            """;
    private MockRestServiceServer server;
    private MockMvc mvc;

    @BeforeEach
    void setup() { configurar("access-simulado"); }

    private void configurar(String token) {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var http = new MercadoLivreConsultaClient(() -> token, builder);
        var service = new CatalogoDiagnosticoService(new MercadoLivreCatalogItemsClient(http), new MercadoLivrePricesClient(http));
        mvc = MockMvcBuilders.standaloneSetup(new CatalogoDiagnosticoController(service)).build();
    }

    private void catalogo(String json) {
        server.expect(requestTo(BASE + "/products/MLB47622919/items"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization", "Bearer access-simulado"))
                .andExpect(headerDoesNotExist("Cookie"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void preservaCamposECondicoesSemEscolherMenorPrecoOuInventarDesconto() throws Exception {
        catalogo(CATALOGO);
        server.expect(requestTo(BASE + "/items/MLB123/prices"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization", "Bearer access-simulado"))
                .andRespond(withSuccess("""
                    {"id":"MLB123","prices":[
                     {"id":"1","type":"standard","amount":180,"regular_amount":null,"currency_id":"BRL",
                      "conditions":{"context_restrictions":[],"start_time":null,"end_time":null}},
                     {"id":"2","type":"promotion","amount":120,"regular_amount":180,"currency_id":"BRL",
                      "conditions":{"context_restrictions":["channel_marketplace","buyer_loyalty_6"],
                                    "start_time":"2099-01-01T00:00:00Z","end_time":"2099-02-01T00:00:00Z"}}]}
                    """, MediaType.APPLICATION_JSON));
        mvc.perform(get(ENDPOINT).param("limite", "1")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.anunciosNaPagina").value(2))
                .andExpect(jsonPath("$.anuncios.length()").value(1))
                .andExpect(jsonPath("$.anuncios[0].anuncioId").value("MLB123"))
                .andExpect(jsonPath("$.anuncios[0].camposAnuncio", org.hamcrest.Matchers.hasItem("campo_adicional")))
                .andExpect(jsonPath("$.anuncios[0].dadosOficiaisAnuncio.status").doesNotExist())
                .andExpect(jsonPath("$.anuncios[0].dadosOficiaisPrecos.prices[0].regular_amount").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.anuncios[0].dadosOficiaisPrecos.prices[1].conditions.context_restrictions[1]").value("buyer_loyalty_6"))
                .andExpect(jsonPath("$.anuncios[0].percentualDesconto").doesNotExist())
                .andExpect(jsonPath("$.amostraConcluida").value(true));
        server.verify();
    }

    @Test
    void recusaDePrecosInterrompeAmostraSemPerderAnunciosNemExporBody() throws Exception {
        catalogo(CATALOGO);
        server.expect(requestTo(BASE + "/items/MLB123/prices"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("access-simulado segredo").contentType(MediaType.TEXT_PLAIN));
        mvc.perform(get(ENDPOINT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.amostraConcluida").value(false))
                .andExpect(jsonPath("$.anuncios.length()").value(1))
                .andExpect(jsonPath("$.anuncios[0].httpPrecos").value(403))
                .andExpect(jsonPath("$.anuncios[0].dadosOficiaisAnuncio.item_id").value("MLB123"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("segredo"))));
        server.verify();
    }

    @Test
    void naoConfundeOutroItemNemRespostaSemPrecosComSucesso() throws Exception {
        catalogo(CATALOGO);
        server.expect(requestTo(BASE + "/items/MLB123/prices"))
                .andRespond(withSuccess("{\"id\":\"MLB999\",\"prices\":[]}", MediaType.APPLICATION_JSON));
        mvc.perform(get(ENDPOINT)).andExpect(status().isOk()).andExpect(jsonPath("$.amostraConcluida").value(false));
        server.verify();
    }

    @Test
    void ausenciaDeAnunciosEhResultadoVazioSemPrecosFicticios() throws Exception {
        catalogo("{\"results\":[]}");
        mvc.perform(get(ENDPOINT)).andExpect(status().isOk()).andExpect(jsonPath("$.anuncios.length()").value(0));
        server.verify();
    }

    @Test
    void contratoDeCatalogoInvalidoNaoViraSucessoVazio() throws Exception {
        catalogo("{\"outro_campo\":[]}");
        mvc.perform(get(ENDPOINT)).andExpect(status().isBadGateway());
        server.verify();
    }

    @Test
    void naoConsultaPrecosParaIdentificadorInvalido() throws Exception {
        catalogo("{\"results\":[{\"item_id\":\"https://example.com\"}]}");
        mvc.perform(get(ENDPOINT)).andExpect(status().isOk()).andExpect(jsonPath("$.amostraConcluida").value(false));
        server.verify();
    }

    @Test
    void erro404IdentificaRecursoECodigoSemRepassarMensagemPrivada() throws Exception {
        server.expect(requestTo(BASE + "/products/MLB47622919/items"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"not_found\",\"message\":\"segredo-na-mensagem\"}"));
        server.expect(requestTo(BASE + "/products/MLB47622919"))
                .andRespond(withSuccess("{\"id\":\"MLB47622919\",\"name\":\"Exemplo\",\"status\":\"active\",\"children_ids\":[\"MLB123\"]}", MediaType.APPLICATION_JSON));
        mvc.perform(get(ENDPOINT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.httpCatalogo").value(404))
                .andExpect(jsonPath("$.anunciosNaPagina").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.amostraConcluida").value(false))
                .andExpect(jsonPath("$.detalhesProduto.http").value(200))
                .andExpect(jsonPath("$.detalhesProduto.dadosOficiais.children_ids[0]").value("MLB123"))
                .andExpect(jsonPath("$.erroCatalogo", org.hamcrest.Matchers.containsString("/products/MLB47622919/items")))
                .andExpect(jsonPath("$.erroCatalogo", org.hamcrest.Matchers.containsString("error=not_found")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("segredo-na-mensagem"))));
        server.verify();
    }

    @Test
    void preservaFalhaDeDetalhesSemInsistirOuConsultarPrecos() throws Exception {
        server.expect(requestTo(BASE + "/products/MLB47622919/items")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/products/MLB47622919")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        mvc.perform(get(ENDPOINT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.httpCatalogo").value(404))
                .andExpect(jsonPath("$.detalhesProduto.http").value(403))
                .andExpect(jsonPath("$.amostraConcluida").value(false));
        server.verify();
    }

    @Test
    void validaEntradaESessaoSemChamadaExterna() throws Exception {
        mvc.perform(get("/api/produtos/invalido/diagnostico-ofertas")).andExpect(status().isBadRequest());
        mvc.perform(get(ENDPOINT).param("limite", "6")).andExpect(status().isBadRequest());
        mvc.perform(get(ENDPOINT).param("limite", "0")).andExpect(status().isBadRequest());
        server.verify();
        configurar(null);
        mvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
        server.verify();
    }
}
