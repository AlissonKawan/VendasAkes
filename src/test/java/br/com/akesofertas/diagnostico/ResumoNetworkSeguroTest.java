package br.com.akesofertas.diagnostico;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class ResumoNetworkSeguroTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void preservaCamposQuantidadesEAmostrasSemCredenciais() {
        var resultado = json.valueToTree(ResumoNetworkSeguro.resumir("""
                {"results":[{"item_id":"MLB123","product_id":"MLB456","price":99.90,"original_price":120,
                  "currency_id":"BRL","coupon":{"code":"CUPOM-PRIVADO"},
                  "permalink":"https://www.mercadolivre.com.br/produto/p/MLB456?access_token=SEGREDO#SEGREDO"}],
                 "paging":{"total":52,"offset":0,"limit":20,"next_cursor":"CURSOR-PRIVADO"},
                 "token":{"results":[{"id":"MLB999","price":777}]},"password":"SENHA-PRIVADA",
                 "CHAVE-PRIVADA":{"ssid":"COOKIE-PRIVADO"}}
                """));
        String texto = resultado.toString();
        for (String segredo : new String[]{"CUPOM-PRIVADO", "SEGREDO", "CURSOR-PRIVADO", "SENHA-PRIVADA", "CHAVE-PRIVADA", "COOKIE-PRIVADO", "MLB999"}) {
            assertFalse(texto.contains(segredo), segredo);
        }
        assertEquals(1, resultado.path("amostrasComId").size());
        assertEquals("MLB123", resultado.path("amostrasComId").get(0).path("item_id").asText());
        assertEquals(99.90, resultado.path("amostrasComId").get(0).path("price").doubleValue());
        assertEquals("https://www.mercadolivre.com.br/p/MLB456", resultado.path("amostrasComId").get(0)
                .path("permalink").path("referenciaDerivada").asText());
        assertEquals(52, resultado.path("estrutura").path("paging").path("total").intValue());
        assertEquals(1, resultado.path("listas").get(0).path("quantidade").intValue());
        assertTrue(texto.contains("coupon"));
        assertTrue(texto.contains("next_cursor"));
    }

    @Test
    void urlsEParametrosNuncaCopiamValoresDesconhecidos() {
        String endpoint = "https://www.mercadolivre.com.br/api/SECRET_PATH/offers?token=SECRET_QUERY&offset=20";
        assertEquals("https://www.mercadolivre.com.br/api/{segmento_omitido}/offers", ResumoNetworkSeguro.endpoint(endpoint));
        assertEquals(java.util.List.of("token", "offset"), ResumoNetworkSeguro.parametros(endpoint));
        assertFalse(ResumoNetworkSeguro.parametros(endpoint + "&PRIVATE_NAME=foo").toString().contains("PRIVATE_NAME"));
        assertEquals("https://[subdominio omitido]/api", ResumoNetworkSeguro.endpoint("https://SECRET.mercadolivre.com.br/api"));
        assertEquals("[endpoint omitido]", ResumoNetworkSeguro.endpoint("https://mercadolivre.com.br.evil.test/api"));
        assertNull(ResumoNetworkSeguro.referenciaProduto("https://www.mercadolivre.com.br.evil.test/p/MLB123"));
    }

    @Test
    void omiteHtmlELimitaCorposEAmostrasMasPreservaTotal() {
        assertFalse(ResumoNetworkSeguro.resumir("<html>SEGREDO</html>").toString().contains("SEGREDO"));
        assertEquals("omitido por tamanho", ResumoNetworkSeguro.resumir("x".repeat(ResumoNetworkSeguro.MAX_BODY + 1)).get("formato"));
        String resultados = String.join(",", java.util.Collections.nCopies(50, "{\"id\":\"MLB123\"}"));
        var resumo = json.valueToTree(ResumoNetworkSeguro.resumir("{\"results\":[" + resultados + "]}"));
        assertEquals(50, resumo.path("listas").get(0).path("quantidade").intValue());
        assertEquals(20, resumo.path("amostrasComId").size());
    }

    @Test
    void detectaLoteParcialReordenadoSemAssociarPelaPosicaoOuExporLinks() {
        String entrada = "{\"urls\":[\"URL-A\",\"URL-B\"],\"tag\":\"telegram\",\"csrf\":\"SEGREDO\"}";
        String resposta = """
                {"status":200,"urls":[
                  {"created":true,"origin_url":"URL-B","short_url":"https://meli.la/SEGREDO"},
                  {"created":false,"origin_url":"URL-A","short_url":null}]}
                """;
        var lote = json.valueToTree(ResumoNetworkSeguro.lote(entrada, resposta));
        assertEquals(2, lote.path("quantidadeEnviada").intValue());
        assertTrue(lote.path("dentroDoLimiteDiagnostico").booleanValue());
        assertEquals(1, lote.path("resultados").get(0).path("indicesEntradaPorOriginUrl").get(0).intValue());
        assertTrue(lote.path("resultados").get(0).path("shortUrlMeliLa").booleanValue());
        assertFalse(lote.path("resultados").get(1).path("created").booleanValue());
        assertFalse(lote.toString().contains("SEGREDO"));
        assertFalse(lote.toString().contains("URL-A"));
    }

    @Test
    void loteAcimaDe20NaoEhProcessadoENaoProvaLimiteDoServidor() {
        String urls = String.join(",", java.util.Collections.nCopies(21, "\"https://example.com\""));
        var lote = ResumoNetworkSeguro.lote("{\"urls\":[" + urls + "]}", "{\"urls\":[]}");
        assertEquals(21, lote.get("quantidadeEnviada"));
        assertEquals(false, lote.get("dentroDoLimiteDiagnostico"));
        assertFalse(lote.containsKey("resultados"));
        assertEquals(false, ResumoNetworkSeguro.lote("{\"urls\":[]}", "{\"urls\":[]}").get("dentroDoLimiteDiagnostico"));
    }

    @Test
    void limiteDe20AceitoEDuplicatasNaoGeramAssociacaoFalsa() {
        String urls = String.join(",", java.util.Collections.nCopies(20, "\"URL-A\""));
        var lote = json.valueToTree(ResumoNetworkSeguro.lote("{\"urls\":[" + urls + "]}",
                "{\"urls\":[{\"created\":true,\"origin_url\":\"URL-A\",\"short_url\":\"https://meli.la/abc\"}]}"));
        assertTrue(lote.path("dentroDoLimiteDiagnostico").booleanValue());
        assertEquals(20, lote.path("resultados").get(0).path("indicesEntradaPorOriginUrl").size());
        assertFalse(ResumoNetworkSeguro.linkCurtoValido("https://meli.la.evil.test/abc"));
        assertFalse(ResumoNetworkSeguro.linkCurtoValido("https://meli.la/abc?token=segredo"));
    }

    @Test
    void limitaCapturaAoFeedGeradorEXhrSemLoginOutrosSitesOuApisDeConta() {
        String hub = "https://www.mercadolivre.com.br/afiliados/hub?is_affiliate=true#menu-user";
        String feed = "https://www.mercadolivre.com.br/api/offers";
        assertTrue(DiagnosticoOfertasNetwork.elegivel(hub, feed, "fetch"));
        assertTrue(DiagnosticoOfertasNetwork.elegivel(hub, feed, "xhr"));
        assertFalse(DiagnosticoOfertasNetwork.elegivel(hub, feed, "document"));
        assertFalse(DiagnosticoOfertasNetwork.elegivel("https://www.mercadolivre.com.br/login", feed, "fetch"));
        assertFalse(DiagnosticoOfertasNetwork.elegivel(hub, "https://www.mercadolivre.com.br/users/me", "fetch"));
        assertFalse(DiagnosticoOfertasNetwork.elegivel(hub, "https://evil.test/api/offers", "fetch"));
        assertFalse(DiagnosticoOfertasNetwork.paginaPermitida("https://www.mercadolivre.com.br/afiliados/hub-malicioso"));
        assertFalse(DiagnosticoOfertasNetwork.paginaPermitida("http://www.mercadolivre.com.br/afiliados/hub"));
    }
}
