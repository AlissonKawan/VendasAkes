package br.com.akesofertas.cupons.service;

import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.cupons.domain.MercadoLivreCupom;
import br.com.akesofertas.cupons.domain.ProdutoElegivelCupom;
import br.com.akesofertas.cupons.domain.TipoAtivacaoCupom;
import br.com.akesofertas.cupons.domain.TipoDescontoCupom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MercadoLivreCouponServiceTest {

    private final Instant agora = Instant.parse("2026-09-08T12:00:00Z");
    private final Clock clock = Clock.fixed(agora, ZoneId.of("UTC"));
    private final CupomElegibilidadeService elegibilidadeService = new CupomElegibilidadeService();
    private MercadoLivreCouponService service;

    @BeforeEach
    void setUp() {
        service = new MercadoLivreCouponService(elegibilidadeService, Duration.ofMinutes(30), clock, 2);
    }

    @Test
    void enriqueceOfertaQuandoItemConstaEmItemIdsElegiveis() {
        var cupom = new MercadoLivreCupom(
                "100", "10% OFF Geral", "MELI10", null,
                TipoAtivacaoCupom.CODIGO, TipoDescontoCupom.PERCENTUAL,
                new BigDecimal("10.00"), new BigDecimal("50.00"), new BigDecimal("30.00"),
                agora.plusSeconds(3600), "ACTIVE", null,
                Set.of("MLB123"), List.of()
        );

        service.salvarCuponsEmCache(List.of(cupom));

        var oferta = new OfertaAfiliado("MLB123", null, "Teclado Mecanico", null,
                new BigDecimal("100.00"), null, null, null, "https://url", null, null);

        OfertaAfiliado enriquecida = service.enriquecer(oferta);

        assertNotNull(enriquecida.cupom());
        assertEquals("MELI10", enriquecida.cupom().codigoExibivel());
        assertEquals(new BigDecimal("10.00"), enriquecida.cupom().descontoAplicado());
        assertEquals(new BigDecimal("90.00"), enriquecida.cupom().precoEstimado());
    }

    @Test
    void naoEnriqueceQuandoNaoHaEvidenciaObjetiva() {
        var cupom = new MercadoLivreCupom(
                "100", "10% OFF Geral", "MELI10", null,
                TipoAtivacaoCupom.CODIGO, TipoDescontoCupom.PERCENTUAL,
                new BigDecimal("10.00"), null, null,
                agora.plusSeconds(3600), "ACTIVE", null,
                Set.of("MLB999"), List.of() // NÃO contém MLB123
        );

        service.salvarCuponsEmCache(List.of(cupom));

        var oferta = new OfertaAfiliado("MLB123", null, "Geral Item", null,
                new BigDecimal("100.00"), null, null, null, "https://url", null, null);

        OfertaAfiliado resultado = service.enriquecer(oferta);

        assertNull(resultado.cupom());
        assertSame(oferta, resultado);
    }

    @Test
    void enriqueceViaContainerQuandoJaEstaEmCache() {
        var cupom = new MercadoLivreCupom(
                "200", "R$ 20 OFF em Casa", null, "token==",
                TipoAtivacaoCupom.APLICAVEL, TipoDescontoCupom.VALOR_FIXO,
                new BigDecimal("20.00"), new BigDecimal("100.00"), new BigDecimal("20.00"),
                agora.plusSeconds(3600), "ACTIVE", "https://lista.mercadolivre.com.br/_Container_MLB123",
                Set.of(), List.of()
        );

        service.salvarCuponsEmCache(List.of(cupom));

        // Preenche o cache do container previamente
        service.salvarContainerEmCache(new MercadoLivreCouponService.ContainerCacheEntry(
                200L, "https://lista.mercadolivre.com.br/_Container_MLB123",
                List.of(new ProdutoElegivelCupom(200L, "MLB777", "Mesa", "https://link", new BigDecimal("150.00"))),
                agora, agora.plus(Duration.ofMinutes(30)), false, null));

        var oferta = new OfertaAfiliado("MLB777", null, "Mesa", null,
                new BigDecimal("150.00"), null, null, null, "https://url", null, null);

        OfertaAfiliado enriquecida = service.enriquecer(oferta);

        assertNotNull(enriquecida.cupom());
        assertEquals(new BigDecimal("20.00"), enriquecida.cupom().descontoAplicado());
        assertEquals(new BigDecimal("130.00"), enriquecida.cupom().precoEstimado());
        assertFalse(enriquecida.cupom().temCodigoExibivel());
    }

    @Test
    void limparCacheRemoveCuponsEContainers() {
        var cupom = new MercadoLivreCupom(
                "100", "10% OFF Geral", "MELI10", null,
                TipoAtivacaoCupom.CODIGO, TipoDescontoCupom.PERCENTUAL,
                new BigDecimal("10.00"), new BigDecimal("50.00"), new BigDecimal("30.00"),
                agora.plusSeconds(3600), "ACTIVE", null,
                Set.of("MLB123"), List.of()
        );

        service.salvarCuponsEmCache(List.of(cupom));
        assertEquals(1, service.totalCuponsCache());

        service.limparCache();
        assertEquals(0, service.totalCuponsCache());

        var oferta = new OfertaAfiliado("MLB123", null, "Teclado Mecanico", null,
                new BigDecimal("100.00"), null, null, null, "https://url", null, null);
        assertNull(service.enriquecer(oferta).cupom());
    }
}
