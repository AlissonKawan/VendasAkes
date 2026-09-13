package br.com.akesofertas.afiliados;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AffiliateLinkServiceTest {
    private static final String PRODUTO = "https://produto.mercadolivre.com.br/MLB-123456789-produto-_JM";

    @Test
    void enviaContratoObservadoEExtraiShortUrlDoJson() {
        ObjectMapper json = new ObjectMapper();
        AtomicReference<AffiliateLinkRequest> recebido = new AtomicReference<>();
        AffiliateLinkService service = new AffiliateLinkService(pedido -> {
            recebido.set(pedido);
            return json.readValue("""
                    {"status":200,"urls":[{"created":true,"tag":"telegram",
                     "short_url":"https://meli.la/EXEMPLO","origin_url":"https://produto.mercadolivre.com.br/MLB-123456789-produto-_JM","campo_novo":1}]}
                    """, AffiliateLinkResponse.class);
        }, "telegram");
        
        Map<String, String> gerados = service.gerarLinks(List.of(PRODUTO));
        assertEquals("https://meli.la/EXEMPLO", gerados.get(PRODUTO));
        
        assertEquals(json.readTree("{\"urls\":[\"" + PRODUTO + "\"],\"tag\":\"telegram\"}"),
                json.valueToTree(recebido.get()));
    }

    @Test
    void rejeitaUrlForaDoMercadoLivreAntesDeChamarCliente() {
        AffiliateLinkService service = new AffiliateLinkService(pedido -> {
            fail("Não deveria chamar o cliente para URL inválida");
            return null;
        }, "telegram");
        for (String url : List.of("http://localhost/produto", "https://www.mercadolivre.com.br.evil.test/produto",
                "https://segredo@www.mercadolivre.com.br/produto", "https://meli.la/ja-afiliado", "")) {
            assertThrows(AffiliateLinkException.class, () -> service.gerarLinks(List.of(url)));
        }
        assertTrue(service.gerarLinks(null).isEmpty());
    }

    @Test
    void lidaComFalhaGeralDaResposta() {
        for (AffiliateLinkResponse resposta : List.of(
                new AffiliateLinkResponse(400, List.of(), 0, 0, 0), 
                new AffiliateLinkResponse(200, null, 0, 0, 0))) {
            AffiliateLinkService service = new AffiliateLinkService(pedido -> resposta, "telegram");
            assertThrows(AffiliateLinkException.class, () -> service.gerarLinks(List.of(PRODUTO)));
        }
        assertThrows(AffiliateLinkException.class,
                () -> new AffiliateLinkService(pedido -> null, "telegram").gerarLinks(List.of(PRODUTO)));
    }

    @Test
    void lidaComFalhaParcialEmLinkRetornado() {
        // Tag diferente
        AffiliateLinkService serviceTag = new AffiliateLinkService(pedido -> resposta(true, "outra-tag", "https://meli.la/EXEMPLO"), "telegram");
        assertTrue(serviceTag.gerarLinks(List.of(PRODUTO)).isEmpty());
        
        // Sem path
        AffiliateLinkService servicePath = new AffiliateLinkService(pedido -> resposta(true, "telegram", "https://meli.la/"), "telegram");
        assertTrue(servicePath.gerarLinks(List.of(PRODUTO)).isEmpty());
        
        // Sem HTTPS
        AffiliateLinkService serviceHttps = new AffiliateLinkService(pedido -> resposta(true, "telegram", "http://meli.la/EXEMPLO"), "telegram");
        assertTrue(serviceHttps.gerarLinks(List.of(PRODUTO)).isEmpty());
        
        // created=false - a nova regra aceita para diagnóstico
        AffiliateLinkService serviceCreated = new AffiliateLinkService(pedido -> resposta(false, "telegram", "https://meli.la/EXEMPLO"), "telegram");
        assertFalse(serviceCreated.gerarLinks(List.of(PRODUTO)).isEmpty());
    }

    @Test
    void preservaCamposReaisDeErroPorUrl() {
        AffiliateLinkResponse resposta = new ObjectMapper().readValue("""
                {"status":200,"total_items":1,"total_success":0,"total_error":1,
                 "urls":[{"origin_url":"https://www.mercadolivre.com.br/produto/p/MLB1",
                 "error_code":111,"message":"URL not allowed in affiliates program","status":200}]}
                """, AffiliateLinkResponse.class);

        AffiliateLinkResponse.Link falha = resposta.urls().getFirst();
        assertEquals(111, falha.errorCode());
        assertEquals("URL not allowed in affiliates program", falha.message());
        assertEquals(200, falha.status());
        assertNull(falha.created());
        assertNull(falha.shortUrl());
        assertNull(falha.tag());
    }

    @Test
    void loteParcialPreservaSucessosEErrosSemAssociarPorIndice() {
        String outro = "https://www.mercadolivre.com.br/outro/p/MLB2";
        var resposta = new AffiliateLinkResponse(200, List.of(
                new AffiliateLinkResponse.Link(true, "telegram", "https://meli.la/OK", outro,
                        null, null, null),
                new AffiliateLinkResponse.Link(null, null, null, PRODUTO, 111,
                        "URL not allowed in affiliates program", 200)), 2, 1, 1);
        var service = new AffiliateLinkService(pedido -> resposta, "telegram");

        List<ResultadoLinkAfiliado> resultados = service.gerarResultados(List.of(PRODUTO, outro));

        assertEquals(2, resultados.size());
        assertEquals(111, resultados.stream().filter(r -> PRODUTO.equals(r.originUrl()))
                .findFirst().orElseThrow().errorCode());
        assertEquals("https://meli.la/OK", resultados.stream().filter(r -> outro.equals(r.originUrl()))
                .findFirst().orElseThrow().shortUrl());
        assertEquals(Map.of(outro, "https://meli.la/OK"), service.gerarLinks(List.of(PRODUTO, outro)));
    }

    @Test
    void correlacionaOriginUrlQuandoPortalRemoveFragmento() {
        String solicitada = PRODUTO + "?extra_comm=false#polycard_client=affiliates&wid=MLB123";
        String retornada = PRODUTO + "?extra_comm=false";
        var resposta = new AffiliateLinkResponse(200, List.of(
                new AffiliateLinkResponse.Link(true, "telegram", "https://meli.la/OK", retornada,
                        null, null, null)), 1, 1, 0);

        ResultadoLinkAfiliado resultado = new AffiliateLinkService(pedido -> resposta, "telegram")
                .gerarResultados(List.of(solicitada)).getFirst();

        assertTrue(resultado.sucesso());
        assertEquals(solicitada, resultado.originUrl());
        assertEquals("https://meli.la/OK", resultado.shortUrl());
    }

    private static AffiliateLinkResponse resposta(boolean criado, String tag, String curto) {
        return new AffiliateLinkResponse(200,
                List.of(new AffiliateLinkResponse.Link(criado, tag, curto, PRODUTO, null, null, null)),
                1, 1, 0);
    }
}
