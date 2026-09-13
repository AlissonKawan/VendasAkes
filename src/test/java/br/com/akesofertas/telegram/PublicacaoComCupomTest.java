package br.com.akesofertas.telegram;

import br.com.akesofertas.afiliados.*;
import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.afiliados.rejeicao.OfertaAfiliadoRejeitadaService;
import br.com.akesofertas.cupons.domain.*;
import br.com.akesofertas.publicacao.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicacaoComCupomTest {
    MensagemOfertaService mensagens = new MensagemOfertaService();

    @Test void semCupomMantemMensagemExata() {
        assertEquals("🔥 <b>20% OFF</b>\n\n💡 <b>Produto</b>\n\n❌ De: <s>R$ 250,00</s>\n✅ Por: <b>R$ 200,00</b>\n\n🏆 Destaque",
                mensagens.montarMensagem(pendente()));
        assertNull(pendente().comCupom(null).cupom());
    }

    @Test void mensagemFixaIncluiAliasDescontoEstimativaEPreservaBlocoOriginal() {
        String mensagem = mensagens.montarMensagem(pendente().comCupom(cupom(false, "#AKESPROMO")));
        assertTrue(mensagem.contains("🎟️ Cupom: AKESPROMO"));
        assertTrue(mensagem.indexOf("✅ Por:") < mensagem.indexOf("🎟️ Cupom: AKESPROMO"));
        assertFalse(mensagem.contains("#AKESPROMO"));
        assertTrue(mensagem.contains("💸 R$ 20,00 OFF"));
        assertTrue(mensagem.contains("Com cupom: <b>R$ 180,00</b>"));
    }

    @Test void percentualFormatadoSemCalcularNaMensagem() {
        assertTrue(mensagens.montarMensagem(pendente().comCupom(cupom(true, "#AKES20"))).contains("💸 20% OFF"));
    }

    @Test void aliasEscapadoComoHtml() {
        assertTrue(mensagens.montarMensagem(pendente().comCupom(cupom(false, "#A<&>"))).contains("A&lt;&amp;&gt;"));
    }

    @Test void publicadorTransportaCupomAteTelegramSemMudarLinkOuRegistro() {
        var banco = mock(OfertaPublicadaService.class);
        var links = mock(AffiliateLinkService.class);
        var telegram = mock(TelegramService.class);
        var rejeitadas = mock(OfertaAfiliadoRejeitadaService.class);
        Instant agora = Instant.parse("2026-09-09T14:00:00Z");
        var oferta = new OfertaAfiliado("MLB1", null, "Produto", new BigDecimal("250"), new BigDecimal("200"),
                "20% OFF", null, "Destaque", "https://www.mercadolivre.com.br/p/MLB9?wid=MLB1", "https://http2.mlstatic.com/foto.jpg", cupomConfirmado(agora));
        when(links.gerarResultados(List.of(oferta.url()))).thenReturn(List.of(new ResultadoLinkAfiliado(oferta.url(), true, "https://meli.la/abc", true, null, null)));
        when(banco.registrarPendente("ML", oferta, "https://meli.la/abc")).thenReturn(pendente());
        var publicador = new PublicadorOfertaService(banco, links, telegram, rejeitadas,
                Duration.ofMinutes(2), Clock.fixed(agora, java.time.ZoneOffset.UTC));
        assertEquals(ResultadoPublicacaoOferta.ENVIADA, publicador.processarOferta("ML", oferta));
        verify(links).gerarResultados(List.of(oferta.url()));
        verify(banco).registrarPendente("ML", oferta, "https://meli.la/abc");
        verify(telegram).enviarOferta(pendente().comCupom(oferta.cupom()), oferta.imagemUrl());
        verify(banco).marcarComoEnviada(1L);
        assertNull(pendente().cupom());
    }

    @Test void publicadorRemoveCupomSemConfidenceHighMasMantemOferta() {
        var banco = mock(OfertaPublicadaService.class);
        var links = mock(AffiliateLinkService.class);
        var telegram = mock(TelegramService.class);
        var rejeitadas = mock(OfertaAfiliadoRejeitadaService.class);
        var oferta = new OfertaAfiliado("MLB1", null, "Produto", null, new BigDecimal("200"),
                null, null, null, "https://produto", null, cupom(false, null));
        var semCupom = oferta.comCupom(null);
        when(banco.registrarPendente("ML", semCupom, "https://meli.la/abc")).thenReturn(pendente());

        var publicador = new PublicadorOfertaService(banco, links, telegram, rejeitadas);
        assertEquals(ResultadoPublicacaoOferta.ENVIADA,
                publicador.publicarComLink("ML", oferta, "https://meli.la/abc"));
        verify(telegram).enviarOferta(pendente(), null);
    }

    @Test void publicadorRemoveCupomHighComSnapshotExpiradoMasMantemOferta() {
        var banco = mock(OfertaPublicadaService.class);
        var links = mock(AffiliateLinkService.class);
        var telegram = mock(TelegramService.class);
        var rejeitadas = mock(OfertaAfiliadoRejeitadaService.class);
        Instant agora = Instant.parse("2026-09-09T14:00:00Z");
        var oferta = new OfertaAfiliado("MLB1", null, "Produto", null, new BigDecimal("200"),
                null, null, null, "https://produto", null,
                cupomConfirmado(agora.minus(Duration.ofMinutes(3))));
        var semCupom = oferta.comCupom(null);
        when(banco.registrarPendente("ML", semCupom, "https://meli.la/abc")).thenReturn(pendente());

        var publicador = new PublicadorOfertaService(banco, links, telegram, rejeitadas,
                Duration.ofMinutes(2), Clock.fixed(agora, java.time.ZoneOffset.UTC));
        assertEquals(ResultadoPublicacaoOferta.ENVIADA,
                publicador.publicarComLink("ML", oferta, "https://meli.la/abc"));
        verify(banco).registrarPendente("ML", semCupom, "https://meli.la/abc");
        verify(telegram).enviarOferta(pendente(), null);
    }

    @Test void cupomNaoContornaDeduplicacaoFornecedorItemId() {
        var banco = mock(OfertaPublicadaService.class);
        var links = mock(AffiliateLinkService.class);
        var telegram = mock(TelegramService.class);
        var rejeitadas = mock(OfertaAfiliadoRejeitadaService.class);
        when(banco.bloqueadaParaPublicacao("ML", "MLB1")).thenReturn(true);
        var oferta = new OfertaAfiliado("MLB1", null, "Produto", null, new BigDecimal("200"), null, null, null, "url")
                .comCupom(cupom(false, "#AKESPROMO"));
        assertEquals(ResultadoPublicacaoOferta.IGNORADA, new PublicadorOfertaService(banco, links, telegram, rejeitadas).processarOferta("ML", oferta));
        verify(banco).bloqueadaParaPublicacao("ML", "MLB1");
        verifyNoInteractions(links, telegram);
    }

    @Test void telegramExistenteEnviaFotoComBlocoCupomEBotaoAfiliado() {
        var http = mock(org.springframework.web.client.RestTemplate.class);
        when(http.postForEntity(any(java.net.URI.class), any(), eq(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok("{\"ok\":true}"));
        var telegram = new TelegramService("123:teste", "@teste", mensagens, http);
        var oferta = pendente().comCupom(cupom(false, "#AKESPROMO"));
        telegram.enviarOferta(oferta, "https://http2.mlstatic.com/foto.jpg");
        var pedido = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(http).postForEntity(eq(java.net.URI.create("https://api.telegram.org/bot123:teste/sendPhoto")), pedido.capture(), eq(String.class));
        var dados = (java.util.Map<?, ?>) pedido.getValue();
        assertEquals("HTML", dados.get("parse_mode"));
        assertEquals("https://http2.mlstatic.com/foto.jpg", dados.get("photo"));
        assertEquals(mensagens.montarMensagem(oferta), dados.get("caption"));
        assertEquals(java.util.Map.of("inline_keyboard", List.of(List.of(java.util.Map.of(
                "text", "🛒 COMPRAR AGORA", "url", "https://meli.la/abc")))), dados.get("reply_markup"));
        assertFalse(dados.containsKey("text"));
    }

    private OfertaPublicada pendente() {
        return new OfertaPublicada(1L, "ML", "MLB1", null, "Produto", "url", "https://meli.la/abc",
                new BigDecimal("250"), new BigDecimal("200"), "20% OFF", null, "Destaque",
                StatusPublicacao.PENDENTE_ENVIO, Instant.EPOCH, null, null);
    }
    private CupomAplicavel cupom(boolean percentual, String alias) {
        var condicoes = new CondicoesCupom(1L, null, null, new BigDecimal("150"),
                percentual ? new BigDecimal("20") : null, percentual ? null : new BigDecimal("20"),
                new BigDecimal("20"), null, null, null, null, "termos");
        var calculo = new CalculadoraDescontoCupom().calcular(new BigDecimal("200"), condicoes);
        return new CupomAplicavel(1L, alias, "Cupom", condicoes, calculo.descontoAplicado(), calculo.precoEstimado());
    }

    private CupomAplicavel cupomConfirmado(Instant verifiedAt) {
        CupomAplicavel base = cupom(false, "#AKESPROMO");
        return new CupomAplicavel(base.couponId(), base.codigoExibivel(), base.title(), base.condicoes(),
                base.descontoAplicado(), base.precoEstimado(), base.compraMinima(), base.instrucaoAtivacao(),
                br.com.akesofertas.cupons.evidence.ValidationConfidence.HIGH, verifiedAt,
                "COUPON", null);
    }
}
