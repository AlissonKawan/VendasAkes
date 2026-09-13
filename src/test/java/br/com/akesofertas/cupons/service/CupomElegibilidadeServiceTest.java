package br.com.akesofertas.cupons.service;

import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.cupons.domain.CupomAplicavel;
import br.com.akesofertas.cupons.domain.MercadoLivreCupom;
import br.com.akesofertas.cupons.domain.TipoAtivacaoCupom;
import br.com.akesofertas.cupons.domain.TipoDescontoCupom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CupomElegibilidadeServiceTest {

    private CupomElegibilidadeService service;
    private final Instant agora = Instant.parse("2026-09-08T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = new CupomElegibilidadeService();
    }

    @Test
    void cupomPercentualCalculaCorretamenteComTeto() {
        var cupom = new MercadoLivreCupom(
                "1001",
                "20% OFF Eletrônicos",
                "MELI20",
                null,
                TipoAtivacaoCupom.CODIGO,
                TipoDescontoCupom.PERCENTUAL,
                new BigDecimal("20"),
                new BigDecimal("100.00"), // compra mínima 100
                new BigDecimal("50.00"),  // teto de 50
                agora.plusSeconds(3600),
                "ACTIVE",
                null,
                Set.of("MLB123456"),
                List.of()
        );

        // Produto custa 300: 20% seria 60, mas teto é 50 -> desconto 50, preço estimado 250
        var oferta = new OfertaAfiliado("MLB123456", null, "Headset Gamer", null,
                new BigDecimal("300.00"), null, null, null, "https://link", null, null);

        Optional<CupomAplicavel> resultado = service.avaliar(oferta, cupom, agora);

        assertTrue(resultado.isPresent());
        CupomAplicavel aplicavel = resultado.get();
        assertEquals(new BigDecimal("50.00"), aplicavel.descontoAplicado());
        assertEquals(new BigDecimal("250.00"), aplicavel.precoEstimado());
        assertTrue(aplicavel.temCodigoExibivel());
        assertEquals("MELI20", aplicavel.codigoExibivel());
    }

    @Test
    void cupomValorFixoCalculaPrecoEstimado() {
        var cupom = new MercadoLivreCupom(
                "1002",
                "R$ 20 OFF em Áudio",
                null, // sem código
                "token_longo_base64_invisivel==",
                TipoAtivacaoCupom.APLICAVEL,
                TipoDescontoCupom.VALOR_FIXO,
                new BigDecimal("20.00"),
                new BigDecimal("100.00"),
                new BigDecimal("20.00"),
                agora.plusSeconds(3600),
                "ACTIVE",
                null,
                Set.of("MLB999"),
                List.of()
        );

        // Produto custa 219 -> desconto 20, preco estimado 199
        var oferta = new OfertaAfiliado("MLB999", null, "Headset XYZ", null,
                new BigDecimal("219.00"), null, null, null, "https://link", null, null);

        Optional<CupomAplicavel> resultado = service.avaliar(oferta, cupom, agora);

        assertTrue(resultado.isPresent());
        CupomAplicavel aplicavel = resultado.get();
        assertEquals(new BigDecimal("20.00"), aplicavel.descontoAplicado());
        assertEquals(new BigDecimal("199.00"), aplicavel.precoEstimado());
        assertFalse(aplicavel.temCodigoExibivel());
        assertNull(aplicavel.codigoExibivel());
        assertEquals("Ative o cupom no Mercado Livre", aplicavel.instrucaoAtivacao());
    }

    @Test
    void rejeitaProdutoAbaixoDaCompraMinima() {
        var cupom = new MercadoLivreCupom(
                "1003",
                "R$ 50 OFF",
                "CUPOM50",
                null,
                TipoAtivacaoCupom.CODIGO,
                TipoDescontoCupom.VALOR_FIXO,
                new BigDecimal("50.00"),
                new BigDecimal("200.00"), // compra mínima 200
                new BigDecimal("50.00"),
                agora.plusSeconds(3600),
                "ACTIVE",
                null,
                Set.of("MLB123"),
                List.of()
        );

        // Preço 150 < 200 compra mínima
        var oferta = new OfertaAfiliado("MLB123", null, "Produto", null,
                new BigDecimal("150.00"), null, null, null, "https://link", null, null);

        Optional<CupomAplicavel> resultado = service.avaliar(oferta, cupom, agora);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void rejeitaCupomExpirado() {
        var cupom = new MercadoLivreCupom(
                "1004",
                "10% OFF",
                "MELI10",
                null,
                TipoAtivacaoCupom.CODIGO,
                TipoDescontoCupom.PERCENTUAL,
                new BigDecimal("10"),
                null,
                null,
                agora.minusSeconds(60), // expirado há 1 minuto
                "ACTIVE",
                null,
                Set.of("MLB123"),
                List.of()
        );

        var oferta = new OfertaAfiliado("MLB123", null, "Produto", null,
                new BigDecimal("100.00"), null, null, null, "https://link", null, null);

        Optional<CupomAplicavel> resultado = service.avaliar(oferta, cupom, agora);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void rejeitaProdutoNaoPresenteNaListaObjetivaMesmoComTituloParecido() {
        // Título do cupom menciona "Smartphone", título da oferta menciona "Smartphone"
        // MAS o itemId MLB999999 NÃO está em itemIdsElegiveis
        var cupom = new MercadoLivreCupom(
                "1005",
                "10% OFF em Smartphones",
                "SMART10",
                null,
                TipoAtivacaoCupom.CODIGO,
                TipoDescontoCupom.PERCENTUAL,
                new BigDecimal("10"),
                new BigDecimal("500.00"),
                new BigDecimal("200.00"),
                agora.plusSeconds(3600),
                "ACTIVE",
                null,
                Set.of("MLB111111", "MLB222222"), // NÃO contém MLB999999
                List.of()
        );

        var oferta = new OfertaAfiliado("MLB999999", null, "Smartphone Samsung Galaxy A55", null,
                new BigDecimal("1500.00"), null, null, null, "https://link", null, null);

        Optional<CupomAplicavel> resultado = service.avaliar(oferta, cupom, agora);
        assertTrue(resultado.isEmpty(), "NUNCA deve associar por palavras-chave do título sem evidência objetiva!");
    }

    @Test
    void sanitizarCodigoExibivelRemoveTokensLongosEBase64() {
        assertNull(MercadoLivreCupom.sanitizarCodigoExibivel(""));
        assertNull(MercadoLivreCupom.sanitizarCodigoExibivel("   "));
        assertNull(MercadoLivreCupom.sanitizarCodigoExibivel(null));
        assertNull(MercadoLivreCupom.sanitizarCodigoExibivel("CWeO-Hjsgnx9CvZCrFYWu3R3Pn4TN2DUNDZmJSy8ZY9dbrCMa6gGAIySEqVSnmj0n1MQDsV9351ZaNVK3zAkiA=="));
        assertNull(MercadoLivreCupom.sanitizarCodigoExibivel("token_com_mais_de_vinte_caracteres_12345"));

        assertEquals("MELI20", MercadoLivreCupom.sanitizarCodigoExibivel("meli20"));
        assertEquals("PROMO10", MercadoLivreCupom.sanitizarCodigoExibivel("  PROMO10  "));
    }

    @Test
    void trataNulosSemExcecao() {
        assertTrue(service.avaliar(null, null, agora).isEmpty());
        var oferta = new OfertaAfiliado(null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(service.avaliar(oferta, null, agora).isEmpty());
    }

    @Test
    void inactiveNaoEAtivoEActiveEAtivo() {
        MercadoLivreCupom active = cupomComStatus("ACTIVE");
        MercadoLivreCupom inactive = cupomComStatus("INACTIVE");

        assertTrue(active.ativo());
        assertFalse(inactive.ativo());
    }

    @Test
    void campaignIdInvalidoNuncaViraUm() {
        MercadoLivreCupom cupom = new MercadoLivreCupom(
                "invalido", "R$ 20 OFF", null, null, TipoAtivacaoCupom.APLICAVEL,
                TipoDescontoCupom.VALOR_FIXO, new BigDecimal("20.00"), null, null,
                agora.plusSeconds(3600), "ACTIVE", null, Set.of("MLB123"), List.of());
        OfertaAfiliado oferta = new OfertaAfiliado("MLB123", null, "Produto", null,
                new BigDecimal("100.00"), null, null, null, "https://link", null, null);

        assertTrue(service.avaliar(oferta, cupom, agora).isEmpty());
    }

    private MercadoLivreCupom cupomComStatus(String status) {
        return new MercadoLivreCupom(
                "123", "R$ 20 OFF", null, null, TipoAtivacaoCupom.APLICAVEL,
                TipoDescontoCupom.VALOR_FIXO, new BigDecimal("20.00"), null, null,
                agora.plusSeconds(3600), status, null, Set.of("MLB123"), List.of());
    }
}
