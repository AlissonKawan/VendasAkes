package br.com.akesofertas.afiliados.ofertas;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MercadoLivreAffiliateOfferMapperTest {
    // Campos do polycard real fornecido pelo usuário; sem unique_id, cookies, tokens ou rastreamento privado.
    static final String RESPOSTA = """
            {"polycard_client_model":{"polycards":[{
              "metadata":{"id":"MLB4363134873","product_id":"MLB63137487","type":"product",
                "url":"www.mercadolivre.com.br/principia-kit-anti-manchas-essencial-gl-02/p/MLB63137487",
                "url_params":"?pdp_filters=item_id%3AMLB4363134873&extra_comm=true&brand_comm=false",
                "extra_commission":"true","brand_commission":"false"},
              "components":[
                {"type":"title","title":{"text":"Principia Kit Anti-manchas Essencial Gl-02"}},
                {"type":"highlight","highlight":{"text":"MAIS VENDIDO"}},
                {"type":"chip","id":"affiliates_commission_chip","chip":{"pill":{"text":"{ganancia} {extra}"},"label":{"text":"20%"}}},
                {"type":"price","price":{
                  "previous_price":{"value":226,"currency":"BRL"},"current_price":{"value":112.88,"currency":"BRL"},
                  "discount_label":{"text":"50% OFF no Crédito disponível"}}}
              ]}]}}
            """;
    private final MercadoLivreAffiliateOfferMapper mapper = new MercadoLivreAffiliateOfferMapper();

    @Test
    void mapeiaContratoInformadoSemCalcularDescontoOuSomarComissoes() {
        var ofertas = mapper.mapear(RESPOSTA);
        assertEquals(1, ofertas.size());
        var oferta = ofertas.getFirst();
        assertEquals("MLB4363134873", oferta.itemId());
        assertEquals("MLB63137487", oferta.produtoId());
        assertEquals("Principia Kit Anti-manchas Essencial Gl-02", oferta.titulo());
        assertEquals(new BigDecimal("226"), oferta.precoAnterior());
        assertEquals(new BigDecimal("112.88"), oferta.precoAtual());
        assertEquals("50% OFF no Crédito disponível", oferta.desconto());
        assertEquals("20%", oferta.comissao());
        assertEquals("MAIS VENDIDO", oferta.destaque());
        assertNull(oferta.imagemUrl());
        assertEquals("https://www.mercadolivre.com.br/principia-kit-anti-manchas-essencial-gl-02/p/MLB63137487?pdp_filters=item_id%3AMLB4363134873&extra_comm=true&brand_comm=false", oferta.url());
    }


    @Test
    void camposOpcionaisAusentesContinuamNulosSemInventarUrlPrecoOuTitulo() {
        var oferta = mapper.mapear("{\"polycard_client_model\":{\"polycards\":[{\"metadata\":{\"id\":\"MLB123\"}}]}}").getFirst();
        assertEquals("MLB123", oferta.itemId());
        assertNull(oferta.produtoId()); assertNull(oferta.titulo()); assertNull(oferta.precoAnterior()); assertNull(oferta.precoAtual());
        assertNull(oferta.desconto()); assertNull(oferta.comissao()); assertNull(oferta.destaque()); assertNull(oferta.url());
    }

    @Test
    void descartaCardsSemAnuncioMasPreservaLoteVazioLegitimo() {
        assertTrue(mapper.mapear("{\"polycard_client_model\":{\"polycards\":[{}, {\"metadata\":{\"product_id\":\"MLB123\"}}]}}").isEmpty());
        assertTrue(mapper.mapear("{\"polycard_client_model\":{\"polycards\":[]}}").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> mapper.mapear("{}"));
        assertThrows(IllegalArgumentException.class, () -> mapper.mapear("<html>login</html>"));
        assertThrows(IllegalArgumentException.class, () -> mapper.mapear("null"));
    }

    @Test
    void naoConfundeOutroChipNemConvertePrecoTextualEmNumero() {
        var oferta = mapper.mapear(RESPOSTA.replace("affiliates_commission_chip", "outro_chip").replace("112.88", "\"112,88\"")).getFirst();
        assertNull(oferta.comissao());
        assertNull(oferta.precoAtual());
        assertNull(mapper.mapear(RESPOSTA.replace("112.88", "-5")).getFirst().precoAtual());
    }

    @Test
    void aceitaComissaoNoLabelOuPillCasoContenhaPercentual() {
        // Label tem %, pill não tem
        var ofertaLabel = mapper.mapear(RESPOSTA.replace("\"label\":{\"text\":\"20%\"}", "\"label\":{\"text\":\"20%\"}").replace("\"pill\":{\"text\":\"{ganancia} {extra}\"}", "\"pill\":{\"text\":\"GANHOS\"}")).getFirst();
        assertEquals("20%", ofertaLabel.comissao());

        // Pill tem %, label não tem
        var ofertaPill = mapper.mapear(RESPOSTA.replace("\"label\":{\"text\":\"20%\"}", "\"label\":{\"text\":\"ALTO\"}").replace("\"pill\":{\"text\":\"{ganancia} {extra}\"}", "\"pill\":{\"text\":\"GANHOS 15%\"}")).getFirst();
        assertEquals("GANHOS 15%", ofertaPill.comissao());

        // Nenhum tem %
        var ofertaNenhum = mapper.mapear(RESPOSTA.replace("\"label\":{\"text\":\"20%\"}", "\"label\":{\"text\":\"20\"}").replace("\"pill\":{\"text\":\"{ganancia} {extra}\"}", "\"pill\":{\"text\":\"GANHOS\"}")).getFirst();
        assertNull(ofertaNenhum.comissao());
    }

    @Test
    void mantemUrlPublicaPreservandoParamsEFragmentosGarantindoItemId() {
        String base = "https://www.mercadolivre.com.br/p/MLB123";
        // A) type=product com item_id em pdp_filters
        assertEquals(base + "?pdp_filters=item_id%3AMLB123&token=SEGREDO", 
            MercadoLivreAffiliateOfferMapper.urlProduto(base, "?pdp_filters=item_id%3AMLB123&token=SEGREDO", null, "MLB123", "product"));
            
        // B) type=product com wid no fragment
        assertEquals(base + "?token=SEGREDO#wid=MLB123", 
            MercadoLivreAffiliateOfferMapper.urlProduto(base, "?token=SEGREDO", "#wid=MLB123", "MLB123", "product"));

        // C) type=item com MLB-1234567890 no path e itemId MLB1234567890
        String baseUrlItem = "https://produto.mercadolivre.com.br/MLB-1234567890-camiseta-tech";
        assertEquals(baseUrlItem + "?token=SEGREDO", 
            MercadoLivreAffiliateOfferMapper.urlProduto(baseUrlItem, "?token=SEGREDO", null, "MLB1234567890", "item"));
            
        // type=item validando também path formato sem hífen
        String baseUrlItemDireto = "https://produto.mercadolivre.com.br/MLB1234567890-camiseta";
        assertEquals(baseUrlItemDireto, 
            MercadoLivreAffiliateOfferMapper.urlProduto(baseUrlItemDireto, null, null, "MLB1234567890", "item"));

        // D) domínio inválido → null
        assertNull(MercadoLivreAffiliateOfferMapper.urlProduto("https://mercadolivre.com.br.evil.test/p/MLB123", null, null, "MLB123", "product"));
        assertNull(MercadoLivreAffiliateOfferMapper.urlProduto("javascript:alert(1)", null, null, "MLB123", "product"));
        assertNull(MercadoLivreAffiliateOfferMapper.urlProduto("https://segredo@www.mercadolivre.com.br/p/MLB123", null, null, "MLB123", "product"));
        
        // E) itemId diferente do path/query/fragment → null
        assertNull(MercadoLivreAffiliateOfferMapper.urlProduto(base, "?pdp_filters=item_id%3AMLB999", null, "MLB456", "product"));
        assertNull(MercadoLivreAffiliateOfferMapper.urlProduto(baseUrlItem, null, null, "MLB999999", "item"));

        // F) campos opcionais ausentes
        assertNull(MercadoLivreAffiliateOfferMapper.urlProduto(null, null, null, "MLB123", "product"));
    }
}
