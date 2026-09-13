package br.com.akesofertas.telegram;

import br.com.akesofertas.afiliados.AffiliateLinkService;
import br.com.akesofertas.afiliados.ResultadoLinkAfiliado;
import br.com.akesofertas.afiliados.rejeicao.OfertaAfiliadoRejeitadaService;
import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.afiliados.ofertas.OrigemOferta;
import br.com.akesofertas.cupons.domain.CondicoesCupom;
import br.com.akesofertas.cupons.domain.CupomAplicavel;
import br.com.akesofertas.publicacao.OfertaPublicada;
import br.com.akesofertas.publicacao.OfertaPublicadaService;
import br.com.akesofertas.publicacao.StatusPublicacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PublicadorOfertaServiceTest {

    private OfertaPublicadaService publicadaService;
    private AffiliateLinkService linkService;
    private TelegramService telegramService;
    private OfertaAfiliadoRejeitadaService rejeitadaService;
    private PublicadorOfertaService publicador;

    @BeforeEach
    void setUp() {
        publicadaService = mock(OfertaPublicadaService.class);
        linkService = mock(AffiliateLinkService.class);
        telegramService = mock(TelegramService.class);
        rejeitadaService = mock(OfertaAfiliadoRejeitadaService.class);
        publicador = new PublicadorOfertaService(publicadaService, linkService, telegramService, rejeitadaService);
    }

    private OfertaAfiliado criarOferta(String itemId) {
        return new OfertaAfiliado(itemId, "prod1", "Tit", null, BigDecimal.TEN, null, null, null,
                "http://url/" + itemId, "https://img.test/" + itemId + ".jpg");
    }

    private OfertaPublicada criarPendente(String itemId) {
        return new OfertaPublicada(1L, "ML", itemId, null, "Tit", "url", "urlaf", null, BigDecimal.TEN, null, null, null, StatusPublicacao.PENDENTE_ENVIO, Instant.now(), null, null);
    }

    @Test
    void telegramSucessoMarcaEnviada() {
        OfertaAfiliado oferta = criarOferta("123");
        OfertaPublicada pendente = criarPendente("123");
        
        when(publicadaService.bloqueadaParaPublicacao("ML", "123")).thenReturn(false);
        when(linkService.gerarResultados(anyList())).thenReturn(List.of(sucesso("123")));
        when(publicadaService.registrarPendente(eq("ML"), eq(oferta), anyString())).thenReturn(pendente);
        
        publicador.processarOferta("ML", oferta);
        
        verify(telegramService).enviarOferta(pendente, oferta.imagemUrl());
        verify(publicadaService).marcarComoEnviada(1L);
        verify(publicadaService, never()).marcarErroEnvio(anyLong(), anyString());
    }

    @Test
    void telegramFalhaMarcaErroEnvio() {
        OfertaAfiliado oferta = criarOferta("123");
        OfertaPublicada pendente = criarPendente("123");
        
        when(publicadaService.bloqueadaParaPublicacao("ML", "123")).thenReturn(false);
        when(linkService.gerarResultados(anyList())).thenReturn(List.of(sucesso("123")));
        when(publicadaService.registrarPendente(eq("ML"), eq(oferta), anyString())).thenReturn(pendente);
        
        doThrow(new RuntimeException("Erro rede")).when(telegramService).enviarOferta(pendente, oferta.imagemUrl());
        
        publicador.processarOferta("ML", oferta);
        
        verify(telegramService).enviarOferta(pendente, oferta.imagemUrl());
        verify(publicadaService, never()).marcarComoEnviada(1L);
        verify(publicadaService).marcarErroEnvio(1L, "Erro rede");
    }

    @Test
    void telegramNaoChamadoSeGeracaoMeliLaFalhar() {
        OfertaAfiliado oferta = criarOferta("123");
        when(publicadaService.bloqueadaParaPublicacao("ML", "123")).thenReturn(false);
        when(linkService.gerarResultados(anyList())).thenReturn(List.of());
        
        publicador.processarOferta("ML", oferta);
        
        verify(publicadaService, never()).registrarPendente(any(), any(), any());
        verify(telegramService, never()).enviarOferta(any(), any());
    }

    @Test
    void itemDuplicadoNaoEEnviadoNovamente() {
        OfertaAfiliado oferta = criarOferta("123");
        when(publicadaService.bloqueadaParaPublicacao("ML", "123")).thenReturn(true);
        
        publicador.processarOferta("ML", oferta);
        
        verify(linkService, never()).gerarResultados(anyList());
        verify(telegramService, never()).enviarOferta(any(), any());
    }

    @Test
    void codigo111RegistraRejeicaoENaoChamaTelegram() {
        OfertaAfiliado oferta = criarOferta("111");
        when(linkService.gerarResultados(anyList())).thenReturn(List.of(
                new ResultadoLinkAfiliado(oferta.url(), false, null, null, 111,
                        "URL not allowed in affiliates program")));

        assertEquals(ResultadoPublicacaoOferta.REJEITADA_AFILIADO, publicador.processarOferta("ML", oferta));

        verify(rejeitadaService).registrarCodigo111("ML", oferta);
        verify(telegramService, never()).enviarOferta(any(), any());
        verify(publicadaService, never()).registrarPendente(any(), any(), any());
    }

    @Test
    void rejeicaoDentroDoCooldownNaoChamaCreateLink() {
        OfertaAfiliado oferta = criarOferta("111");
        when(rejeitadaService.emCooldown("ML", "111")).thenReturn(true);

        assertEquals(ResultadoPublicacaoOferta.IGNORADA, publicador.processarOferta("ML", oferta));

        verify(linkService, never()).gerarResultados(anyList());
        verify(telegramService, never()).enviarOferta(any(), any());
    }

    @Test
    void cupomDaFonteOficialNaoExigeConfirmacaoHighParaMensagem() {
        var condicoes = new CondicoesCupom(13618999L, null, null, new BigDecimal("114.00"),
                new BigDecimal("7.00"), null, null, null, null, null, null, null);
        var cupom = new CupomAplicavel(13618999L, null, "7% OFF", condicoes,
                new BigDecimal("3.66"), new BigDecimal("48.62"));
        var oferta = new OfertaAfiliado("MLB5161804513", "MLB26638960", "Produto do cupom",
                null, new BigDecimal("52.28"), null, null, null,
                "https://www.mercadolivre.com.br/p/MLB26638960", null, cupom, OrigemOferta.CUPOM);
        var pendente = criarPendente(oferta.itemId());
        when(publicadaService.registrarPendente(eq("ML"), argThat(o -> o.cupom() != null),
                contains("origin=share"))).thenReturn(pendente);

        assertEquals(ResultadoPublicacaoOferta.ENVIADA, publicador.publicarComLink("ML", oferta,
                "https://www.mercadolivre.com.br/p/MLB26638960#origin=share&sid=share"));

        var captor = org.mockito.ArgumentCaptor.forClass(OfertaPublicada.class);
        verify(telegramService).enviarOferta(captor.capture(), isNull());
        assertNotNull(captor.getValue().cupom());
        assertEquals(13618999L, captor.getValue().cupom().couponId());
    }

    private ResultadoLinkAfiliado sucesso(String itemId) {
        return new ResultadoLinkAfiliado("http://url/" + itemId, true,
                "https://meli.la/" + itemId, true, null, null);
    }
}

