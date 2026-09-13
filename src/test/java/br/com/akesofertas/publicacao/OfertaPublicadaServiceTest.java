package br.com.akesofertas.publicacao;

import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import({OfertaPublicadaService.class, JpaOfertaPublicadaRepositoryAdapter.class})
class OfertaPublicadaServiceTest {

    @Autowired
    private OfertaPublicadaService service;

    @Autowired
    private SpringDataOfertaPublicadaRepository jpaRepository;

    private OfertaAfiliado criarOferta(String itemId) {
        return new OfertaAfiliado(itemId, "prod1", "Produto Teste", null, BigDecimal.TEN, "10%", "5%", "destaque", "https://produto.mercadolivre.com.br/MLB-" + itemId);
    }

    @Test
    void itemIdNuloInvalido() {
        assertThrows(IllegalArgumentException.class, () -> service.bloqueadaParaPublicacao("MERCADO_LIVRE", null));
        assertThrows(IllegalArgumentException.class, () -> service.bloqueadaParaPublicacao("MERCADO_LIVRE", " "));
        assertThrows(IllegalArgumentException.class, () -> service.registrarPendente("MERCADO_LIVRE", criarOferta(null), "https://meli.la/123"));
    }

    @Test
    void primeiraPassagemSalvaPendente() {
        OfertaAfiliado oferta = criarOferta("123");
        assertFalse(service.bloqueadaParaPublicacao("MERCADO_LIVRE", "123"));
        
        OfertaPublicada pub = service.registrarPendente("MERCADO_LIVRE", oferta, "https://meli.la/123");
        
        assertNotNull(pub);
        assertNotNull(pub.id());
        assertEquals("MERCADO_LIVRE", pub.fornecedor());
        assertEquals("123", pub.itemId());
        assertEquals(StatusPublicacao.PENDENTE_ENVIO, pub.status());
        assertNull(pub.dataEnvio());
        assertTrue(service.bloqueadaParaPublicacao("MERCADO_LIVRE", "123"));
    }

    @Test
    void mesmaCombinacaoNaoDuplica() {
        OfertaAfiliado oferta = criarOferta("123");
        service.registrarPendente("MERCADO_LIVRE", oferta, "https://meli.la/123");
        
        assertTrue(service.bloqueadaParaPublicacao("MERCADO_LIVRE", "123"));
        OfertaPublicada rep = service.registrarPendente("MERCADO_LIVRE", oferta, "https://meli.la/123");
        assertNull(rep); // Service impede antes de bater no banco
    }
    
    @Test
    void constraintUnicaImpedeDuplicacaoEmConcorrencia() {
        OfertaAfiliado oferta = criarOferta("123");
        service.registrarPendente("MERCADO_LIVRE", oferta, "https://meli.la/123");
        
        // Forçando inserção direta para simular concorrência burlando o if do service
        OfertaPublicadaEntity entity = new OfertaPublicadaEntity("MERCADO_LIVRE", "123", null, "Tit", "url", "urlaf", null, BigDecimal.TEN, null, null, null);
        assertThrows(DataIntegrityViolationException.class, () -> {
            jpaRepository.saveAndFlush(entity);
        });
    }

    @Test
    void marketplacesDiferentesPodemUsarMesmoItemId() {
        OfertaAfiliado ofertaML = criarOferta("123");
        OfertaAfiliado ofertaShopee = criarOferta("123");
        
        service.registrarPendente("MERCADO_LIVRE", ofertaML, "https://meli.la/123");
        
        assertTrue(service.bloqueadaParaPublicacao("MERCADO_LIVRE", "123"));
        assertFalse(service.bloqueadaParaPublicacao("SHOPEE", "123"));
        
        OfertaPublicada pubShopee = service.registrarPendente("SHOPEE", ofertaShopee, "https://shopee.br/123");
        assertNotNull(pubShopee);
    }
    
    @Test
    void transicaoPendenteParaEnviada() {
        OfertaPublicada pub = service.registrarPendente("MERCADO_LIVRE", criarOferta("123"), "link");
        assertNull(pub.dataEnvio());
        
        OfertaPublicada enviada = service.marcarComoEnviada(pub.id());
        assertEquals(StatusPublicacao.ENVIADA, enviada.status());
        assertNotNull(enviada.dataEnvio());
    }
    
    @Test
    void transicaoPendenteParaErro() {
        OfertaPublicada pub = service.registrarPendente("MERCADO_LIVRE", criarOferta("123"), "link");
        
        OfertaPublicada comErro = service.marcarErroEnvio(pub.id(), "Falha de rede");
        assertEquals(StatusPublicacao.ERRO_ENVIO, comErro.status());
        assertEquals("Falha de rede", comErro.mensagemErro());
        assertNull(comErro.dataEnvio());
    }

    @Test
    void camposOpcionaisSaoPersistidosCorretamente() {
        OfertaAfiliado oferta = new OfertaAfiliado("123", null, "Produto Teste", null, BigDecimal.TEN, null, null, null, "https://url.com");
        OfertaPublicada pub = service.registrarPendente("MERCADO_LIVRE", oferta, "https://meli.la/123");
        assertNotNull(pub);
        assertNull(pub.produtoId());
        assertNull(pub.precoAnterior());
        assertNull(pub.desconto());
        assertNull(pub.comissao());
        assertNull(pub.destaque());
    }

    @Test
    void enviadaHaCincoMinutosFicaBloqueada() {
        assertBloqueioPorIdade("cinco", Duration.ofMinutes(5), true);
    }

    @Test
    void enviadaHaVinteETresHorasECinquentaENoveMinutosFicaBloqueada() {
        assertBloqueioPorIdade("quase24", Duration.ofHours(23).plusMinutes(59), true);
    }

    @Test
    void enviadaHaExatamenteVinteEQuatroHorasFicaElegivel() {
        assertBloqueioPorIdade("exatas24", Duration.ofHours(24), false);
    }

    @Test
    void enviadaHaVinteECincoHorasFicaElegivel() {
        assertBloqueioPorIdade("vintecinco", Duration.ofHours(25), false);
    }

    @Test
    void enviadaHaDoisDiasFicaElegivel() {
        assertBloqueioPorIdade("doisdias", Duration.ofDays(2), false);
    }

    @Test
    void erroEnvioSemDataFicaElegivel() {
        Instant agora = Instant.parse("2026-09-08T12:00:00Z");
        var noInstante = serviceNoInstante(agora.minusSeconds(60));
        OfertaPublicada pendente = noInstante.registrarPendente("MERCADO_LIVRE", criarOferta("erro"), "link-antigo");
        noInstante.marcarErroEnvio(pendente.id(), "falha segura");

        assertFalse(serviceNoInstante(agora).bloqueadaParaPublicacao("MERCADO_LIVRE", "erro"));
    }

    @Test
    void republicacaoAposJanelaAtualizaMesmaLinhaEDados() {
        Instant primeiroEnvio = Instant.parse("2026-09-07T10:00:00Z");
        var primeiroService = serviceNoInstante(primeiroEnvio);
        OfertaPublicada primeira = primeiroService.registrarPendente(
                "MERCADO_LIVRE", criarOferta("republicar"), "link-antigo");
        primeiroService.marcarComoEnviada(primeira.id());

        var novaOferta = new OfertaAfiliado("republicar", "prod2", "Título atualizado",
                new BigDecimal("20.00"), new BigDecimal("8.00"), "60%", "7%", "novo destaque",
                "https://produto.mercadolivre.com.br/novo");
        var segundoService = serviceNoInstante(primeiroEnvio.plus(Duration.ofHours(25)));
        OfertaPublicada republicada = segundoService.registrarPendente(
                "MERCADO_LIVRE", novaOferta, "link-novo");

        assertNotNull(republicada);
        assertEquals(primeira.id(), republicada.id());
        assertEquals(StatusPublicacao.PENDENTE_ENVIO, republicada.status());
        assertNull(republicada.dataEnvio());
        assertEquals("link-novo", republicada.urlAfiliado());
        assertEquals("Título atualizado", republicada.titulo());
        assertEquals(new BigDecimal("8.00"), republicada.precoAtual());
        assertEquals(1, jpaRepository.count());
    }

    @Test
    void enviadaSemDataFicaBloqueadaParaInvestigacao() {
        OfertaPublicadaRepository repository = mock(OfertaPublicadaRepository.class);
        OfertaPublicadaEntity entity = mock(OfertaPublicadaEntity.class);
        when(entity.toDomain()).thenReturn(new OfertaPublicada(1L, "MERCADO_LIVRE", "sem-data",
                null, "Título", "url", "link", null, BigDecimal.TEN, null, null, null,
                StatusPublicacao.ENVIADA, Instant.parse("2026-09-01T00:00:00Z"), null, null));
        when(repository.buscarPorFornecedorEItemId("MERCADO_LIVRE", "sem-data"))
                .thenReturn(java.util.Optional.of(entity));

        var serviceDefensivo = new OfertaPublicadaService(repository, Duration.ofHours(24),
                Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC));

        assertTrue(serviceDefensivo.bloqueadaParaPublicacao("MERCADO_LIVRE", "sem-data"));
    }

    @Test
    void cicloConsecutivoDepoisDoEnvioNaoCriaDuplicata() {
        Instant agora = Instant.parse("2026-09-08T12:00:00Z");
        var noInstante = serviceNoInstante(agora);
        OfertaPublicada pendente = noInstante.registrarPendente(
                "MERCADO_LIVRE", criarOferta("consecutivo"), "link-1");
        noInstante.marcarComoEnviada(pendente.id());

        assertTrue(noInstante.bloqueadaParaPublicacao("MERCADO_LIVRE", "consecutivo"));
        assertNull(noInstante.registrarPendente("MERCADO_LIVRE", criarOferta("consecutivo"), "link-2"));
        assertEquals(1, jpaRepository.count());
    }

    private void assertBloqueioPorIdade(String itemId, Duration idade, boolean esperado) {
        Instant agora = Instant.parse("2026-09-08T12:00:00Z");
        var noEnvio = serviceNoInstante(agora.minus(idade));
        OfertaPublicada pendente = noEnvio.registrarPendente(
                "MERCADO_LIVRE", criarOferta(itemId), "link");
        noEnvio.marcarComoEnviada(pendente.id());

        assertEquals(esperado,
                serviceNoInstante(agora).bloqueadaParaPublicacao("MERCADO_LIVRE", itemId));
    }

    private OfertaPublicadaService serviceNoInstante(Instant instante) {
        return new OfertaPublicadaService(new JpaOfertaPublicadaRepositoryAdapter(jpaRepository),
                Duration.ofHours(24), Clock.fixed(instante, ZoneOffset.UTC));
    }
}

