package br.com.akesofertas.scheduler;

import br.com.akesofertas.afiliados.ResultadoLinkAfiliado;
import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.afiliados.rejeicao.OfertaAfiliadoRejeitadaService;
import br.com.akesofertas.cupons.evidence.*;
import br.com.akesofertas.publicacao.OfertaPublicadaService;
import br.com.akesofertas.telegram.PublicadorOfertaService;
import br.com.akesofertas.telegram.ResultadoPublicacaoOferta;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExecutarCicloOfertasServiceTest {
    MercadoLivreAfiliadosSessionFactory factory;
    MercadoLivreAfiliadosSessionFactory.Sessao sessao;
    OfertaPublicadaService publicadas;
    OfertaAfiliadoRejeitadaService rejeitadas;
    PublicadorOfertaService publicador;

    @BeforeEach void setup() {
        factory = mock(MercadoLivreAfiliadosSessionFactory.class);
        sessao = mock(MercadoLivreAfiliadosSessionFactory.Sessao.class);
        publicadas = mock(OfertaPublicadaService.class);
        rejeitadas = mock(OfertaAfiliadoRejeitadaService.class);
        publicador = mock(PublicadorOfertaService.class);
        when(factory.abrir()).thenReturn(sessao);
        when(publicador.publicarComLink(anyString(), any(), anyString())).thenReturn(ResultadoPublicacaoOferta.ENVIADA);
    }

    @Test void selecionaNoMaximoTresEQuantidadeConfiguradaDoisTres() {
        List<OfertaAfiliado> ofertas = ofertas(5);
        when(sessao.buscarOfertas()).thenReturn(ofertas);
        when(sessao.gerarLinks(anyList())).thenReturn(sucessos(ofertas));

        var cicloTres = ciclo(2, 3, 3, 0, ignored -> {});
        assertEquals(3, cicloTres.executarCiclo().selecionadas());
        verify(publicador, times(3)).publicarComLink(anyString(), any(), anyString());

        reset(publicador);
        when(publicador.publicarComLink(anyString(), any(), anyString())).thenReturn(ResultadoPublicacaoOferta.ENVIADA);
        var cicloDois = ciclo(2, 3, 2, 0, ignored -> {});
        assertEquals(2, cicloDois.executarCiclo().selecionadas());
        verify(publicador, times(2)).publicarComLink(anyString(), any(), anyString());
    }

    @Test void publicaUmaSeExisteSomenteUma() {
        List<OfertaAfiliado> ofertas = ofertas(1);
        when(sessao.buscarOfertas()).thenReturn(ofertas);
        when(sessao.gerarLinks(anyList())).thenReturn(sucessos(ofertas));
        ResultadoCicloOfertas resultado = ciclo(2, 3, 3, 0, ignored -> {}).executarCiclo();
        assertEquals(1, resultado.selecionadas());
        assertEquals(1, resultado.enviadas());
    }

    @Test void zeroElegiveisEncerraNormalmente() {
        when(sessao.buscarOfertas()).thenReturn(List.of());
        ResultadoCicloOfertas resultado = ciclo(2, 3, 2, 0, ignored -> {}).executarCiclo();
        assertEquals(0, resultado.selecionadas());
        verify(sessao, never()).gerarLinks(anyList());
        verifyNoInteractions(publicador);
    }

    @Test void enviadaECooldownNaoChamamCreateLink() {
        List<OfertaAfiliado> ofertas = ofertas(2);
        when(sessao.buscarOfertas()).thenReturn(ofertas);
        when(publicadas.bloqueadaParaPublicacao("MERCADO_LIVRE", ofertas.get(0).itemId())).thenReturn(true);
        when(rejeitadas.emCooldown("MERCADO_LIVRE", ofertas.get(1).itemId())).thenReturn(true);
        ResultadoCicloOfertas resultado = ciclo(2, 3, 2, 0, ignored -> {}).executarCiclo();
        assertEquals(1, resultado.jaPublicadas());
        assertEquals(1, resultado.cooldown());
        verify(sessao, never()).gerarLinks(anyList());
    }

    @Test void codigo111NaoInterrompeLote() {
        List<OfertaAfiliado> ofertas = ofertas(2);
        when(sessao.buscarOfertas()).thenReturn(ofertas);
        when(sessao.gerarLinks(anyList())).thenReturn(List.of(
                new ResultadoLinkAfiliado(ofertas.get(0).url(), false, null, null, 111, "remoto"),
                sucesso(ofertas.get(1))));
        ResultadoCicloOfertas resultado = ciclo(1, 1, 1, 0, ignored -> {}).executarCiclo();
        verify(rejeitadas).registrarCodigo111("MERCADO_LIVRE", ofertas.get(0));
        verify(publicador).publicarComLink("MERCADO_LIVRE", ofertas.get(1), "https://meli.la/1");
        assertEquals(1, resultado.enviadas());
    }

    @Test void falhaTelegramNaoImpedeProximaEAplicaDelayEntreTentativas() {
        List<OfertaAfiliado> ofertas = ofertas(2);
        when(sessao.buscarOfertas()).thenReturn(ofertas);
        when(sessao.gerarLinks(anyList())).thenReturn(sucessos(ofertas));
        when(publicador.publicarComLink("MERCADO_LIVRE", ofertas.get(0), "https://meli.la/0"))
                .thenReturn(ResultadoPublicacaoOferta.ERRO_ENVIO);
        when(publicador.publicarComLink("MERCADO_LIVRE", ofertas.get(1), "https://meli.la/1"))
                .thenReturn(ResultadoPublicacaoOferta.ENVIADA);

        AtomicInteger pausas = new AtomicInteger();
        ResultadoCicloOfertas resultado = ciclo(2, 2, 2, 1000, delay -> pausas.incrementAndGet()).executarCiclo();

        assertEquals(2, resultado.selecionadas());
        assertEquals(1, resultado.enviadas());
        assertEquals(1, resultado.errosEnvio());
        assertEquals(1, pausas.get());
    }

    @Test void doisCiclosNaoExecutamSimultaneamente() throws Exception {
        CyclicBarrier barreira = new CyclicBarrier(2);
        when(sessao.buscarOfertas()).thenAnswer(invocation -> {
            barreira.await(5, TimeUnit.SECONDS);
            Thread.sleep(50);
            return List.of();
        });

        var ciclo = ciclo(2, 3, 2, 0, ignored -> {});
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResultadoCicloOfertas> f1 = pool.submit(ciclo::executarCiclo);
            barreira.await(5, TimeUnit.SECONDS);
            Future<ResultadoCicloOfertas> f2 = pool.submit(ciclo::executarCiclo);

            ResultadoCicloOfertas r1 = f1.get(5, TimeUnit.SECONDS);
            ResultadoCicloOfertas r2 = f2.get(5, TimeUnit.SECONDS);
            assertTrue(r1.ignoradoPorConcorrencia() || r2.ignoradoPorConcorrencia());
            assertFalse(r1.ignoradoPorConcorrencia() && r2.ignoradoPorConcorrencia());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void sessaoExpiradaEncerraCicloComAvisoSeguroESemPublicar() {
        when(sessao.buscarOfertas()).thenThrow(new IllegalStateException("SESSAO_AFILIADOS_EXPIRADA: login necessário"));
        ResultadoCicloOfertas resultado = ciclo(2, 3, 2, 0, ignored -> {}).executarCiclo();
        assertTrue(resultado.sessaoExpirada());
        assertEquals(0, resultado.enviadas());
        verifyNoInteractions(publicador);
    }

    @Test void excecaoInesperadaEPropagada() {
        when(sessao.buscarOfertas()).thenThrow(new IllegalStateException("ERRO_INESPERADO"));
        assertThrows(IllegalStateException.class, () -> ciclo(2, 3, 2, 0, ignored -> {}).executarCiclo());
    }

    @Test void falhaDeCupomNaoInterrompeCreateLinkNemPublicacao() {
        var ofertas = ofertas(1);
        when(sessao.buscarOfertas()).thenReturn(ofertas);
        when(sessao.gerarLinks(anyList())).thenReturn(sucessos(ofertas));
        var ciclo = new ExecutarCicloOfertasService(factory, publicadas, rejeitadas, publicador,
                1, 1, 0, () -> 1, ignored -> {});
        assertEquals(1, ciclo.executarCiclo().enviadas());
        verify(sessao).gerarLinks(List.of(ofertas.get(0).url()));
        verify(publicador).publicarComLink("MERCADO_LIVRE", ofertas.get(0), "https://meli.la/0");
    }

    @Test void deduplicaItemRepetidoNoMesmoCiclo() {
        var oferta1 = new OfertaAfiliado("MLB1", null, "Item 1", null, BigDecimal.TEN, null, null, null, "https://item1");
        var ofertaDuplicada = new OfertaAfiliado("MLB1", null, "Item 1 Duplicado", null, BigDecimal.TEN, null, null, null, "https://item1");
        when(sessao.buscarOfertas()).thenReturn(List.of(oferta1, ofertaDuplicada));
        when(sessao.gerarLinks(anyList())).thenReturn(List.of(sucesso(oferta1)));
        when(sessao.resolverImagem(any())).thenAnswer(inv -> inv.getArgument(0));

        var ciclo = new ExecutarCicloOfertasService(factory, publicadas, rejeitadas, publicador,
                1, 2, 0, () -> 2, ignored -> {});

        var resultado = ciclo.executarCiclo();
        assertEquals(1, resultado.selecionadas());
        assertEquals(1, resultado.enviadas());
        verify(publicador, times(1)).publicarComLink(eq("MERCADO_LIVRE"), eq(oferta1), anyString());
    }

    private ExecutarCicloOfertasService ciclo(int min, int max, int quantidade, long delay,
                                               java.util.function.LongConsumer pausa) {
        return new ExecutarCicloOfertasService(factory, publicadas, rejeitadas, publicador,
                min, max, delay, () -> quantidade, pausa);
    }
    private List<OfertaAfiliado> ofertas(int total) {
        var lista = new ArrayList<OfertaAfiliado>();
        for (int i = 0; i < total; i++) lista.add(new OfertaAfiliado("MLB" + i, null, "Oferta " + i,
                null, BigDecimal.TEN, "10% OFF", null, null,
                "https://www.mercadolivre.com.br/produto-" + i + "/p/MLB" + i + "#wid=MLB" + i));
        return lista;
    }
    private List<ResultadoLinkAfiliado> sucessos(List<OfertaAfiliado> ofertas) {
        return ofertas.stream().map(this::sucesso).toList();
    }
    private ResultadoLinkAfiliado sucesso(OfertaAfiliado oferta) {
        return new ResultadoLinkAfiliado(oferta.url(), true,
                "https://meli.la/" + oferta.itemId().substring(3), true, null, null);
    }

    private OfferSnapshot snapshot(OfertaAfiliado oferta, BigDecimal givenDiscount,
                                   String rawType, Instant capturedAt) {
        String campaignId = oferta.cupom() != null && oferta.cupom().couponId() != null
                ? oferta.cupom().couponId().toString() : "13558453";
        var rules = new CouponRules(oferta.cupom() != null ? oferta.cupom().compraMinima() : new BigDecimal("150.00"),
                oferta.cupom() != null ? oferta.cupom().descontoAplicado() : new BigDecimal("20.00"), "BRL",
                capturedAt.plusSeconds(3600), false);
        var decision = new CouponDecision(campaignId, "ACTIVE", givenDiscount, rawType,
                "FIXED", new BigDecimal("20.00"), givenDiscount, rules, 1, capturedAt,
                givenDiscount.signum() > 0);
        var price = new PriceEvidence(new BigDecimal("499.90"), new BigDecimal("179.90"),
                givenDiscount.signum() > 0 ? new BigDecimal("159.90") : new BigDecimal("179.90"),
                givenDiscount.signum() > 0 ? "COUPON" : "STANDARD", "BRL",
                Set.of(EvidenceSource.SSR_STATE));
        return new OfferSnapshot(oferta.itemId(), oferta.itemId(), oferta.produtoId(),
                URI.create(oferta.url()), price,
                List.of(new CampaignParticipation(campaignId, "R$ 20 OFF", "AUTOMATIC", "ACTIVE",
                        "FIXED", "", List.of(), List.of("MLB1775107"), false, rules)),
                List.of(decision), capturedAt);
    }
}
