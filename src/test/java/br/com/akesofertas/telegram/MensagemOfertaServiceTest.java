package br.com.akesofertas.telegram;

import br.com.akesofertas.publicacao.OfertaPublicada;
import br.com.akesofertas.publicacao.StatusPublicacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.text.NumberFormat;
import java.util.Locale;

import br.com.akesofertas.cupons.evidence.ValidationConfidence;

import static org.junit.jupiter.api.Assertions.*;

class MensagemOfertaServiceTest {

    private final MensagemOfertaService service = new MensagemOfertaService();

    private OfertaPublicada criarOferta(BigDecimal precoAnterior, BigDecimal precoAtual, String desconto, String destaque) {
        return new OfertaPublicada(1L, "MERCADO_LIVRE", "123", null, "Produto Teste", "http://original", "http://meli.la/123",
                precoAnterior, precoAtual, desconto, null, destaque, StatusPublicacao.PENDENTE_ENVIO, Instant.now(), null, null);
    }

    @Test
    void testMensagemComPrecoAnterior() {
        OfertaPublicada oferta = criarOferta(new BigDecimal("99.98"), new BigDecimal("43.77"), "56% OFF", "MAIS VENDIDO");
        String msg = service.montarMensagem(oferta);
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        assertTrue(msg.contains("🔥 <b>56% OFF</b>"));
        assertTrue(msg.contains("❌ De: <s>" + nf.format(new BigDecimal("99.98")) + "</s>"));
        assertTrue(msg.contains("✅ Por: <b>" + nf.format(new BigDecimal("43.77")) + "</b>"));
        assertTrue(msg.contains("🏆 MAIS VENDIDO"));
        assertFalse(msg.contains("http://meli.la/123"));
    }

    @Test
    void testMensagemSemPrecoAnterior() {
        OfertaPublicada oferta = criarOferta(null, new BigDecimal("43.77"), "56% OFF", "MAIS VENDIDO");
        String msg = service.montarMensagem(oferta);
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        assertFalse(msg.contains("❌ De:"));
        assertTrue(msg.contains("✅ Por: <b>" + nf.format(new BigDecimal("43.77")) + "</b>"));
        assertFalse(msg.contains("🎟️ Cupom:"));
    }

    @Test
    void testDescontoComCondicaoPreservada() {
        OfertaPublicada oferta = criarOferta(null, new BigDecimal("10.00"), "50% OFF no Pix", null);
        String msg = service.montarMensagem(oferta);
        assertTrue(msg.contains("🔥 <b>50% OFF no Pix</b>"));
    }

    @Test
    void testDestaqueNull() {
        OfertaPublicada oferta = criarOferta(null, new BigDecimal("10.00"), "50% OFF", null);
        String msg = service.montarMensagem(oferta);
        assertFalse(msg.contains("🏆"));
    }

    @Test
    void escapaHtmlDosCamposVindosDaOferta() {
        OfertaPublicada oferta = new OfertaPublicada(1L, "ML", "1", null, "TV <nova> & boa",
                "https://original", "https://meli.la/1", BigDecimal.TEN, BigDecimal.ONE,
                "90% <OFF>", null, null, StatusPublicacao.PENDENTE_ENVIO, Instant.now(), null, null);
        String msg = service.montarMensagem(oferta);
        assertTrue(msg.contains("TV &lt;nova&gt; &amp; boa"));
        assertTrue(msg.contains("90% &lt;OFF&gt;"));
    }

    @Test
    void formataCupomSemCodigoComTituloEIdExatosDaCampanha() {
        var cupom = new br.com.akesofertas.cupons.domain.CupomAplicavel(
                123L, null, "10% OFF em Beleza", null,
                new BigDecimal("20.00"), new BigDecimal("180.00"), new BigDecimal("150.00"),
                "Ative o cupom no Mercado Livre");
        var oferta = new OfertaPublicada(1L, "ML", "MLB1", null, "Perfume", "url", "https://meli.la/abc",
                new BigDecimal("200.00"), new BigDecimal("200.00"), null, null, null,
                StatusPublicacao.PENDENTE_ENVIO, Instant.now(), null, null)
                .comCupom(cupom);

        String msg = service.montarMensagem(oferta);
        assertTrue(msg.contains("🎟️ Cupom oficial: <b>10% OFF em Beleza</b>"));
        assertTrue(msg.contains("🔖 Campanha: <code>123</code>"));
        assertFalse(msg.contains("CUPOM DISPONÍVEL"));
        assertTrue(msg.contains("👉 Ative o cupom no Mercado Livre"));
        assertTrue(msg.contains("📦 Compra mínima:"));
        assertTrue(msg.contains("🔥 Com cupom: <b>R$ 180,00</b>"));
    }

    @Test
    void formataCupomComCodigoExibivel() {
        var cupom = new br.com.akesofertas.cupons.domain.CupomAplicavel(
                124L, "MELI20", "20% OFF", null,
                new BigDecimal("40.00"), new BigDecimal("160.00"), null, null);
        var oferta = new OfertaPublicada(1L, "ML", "MLB1", null, "Headset", "url", "https://meli.la/abc",
                new BigDecimal("200.00"), new BigDecimal("200.00"), null, null, null,
                StatusPublicacao.PENDENTE_ENVIO, Instant.now(), null, null)
                .comCupom(cupom);

        String msg = service.montarMensagem(oferta);
        assertTrue(msg.contains("🎟️ Cupom: MELI20"));
        assertFalse(msg.contains("CUPOM DISPONÍVEL"));
        assertTrue(msg.contains("🔥 Com cupom: <b>R$ 160,00</b>"));
        assertTrue(msg.indexOf("✅ Por:") < msg.indexOf("🎟️ Cupom: MELI20"));
    }

    @Test
    void explicitaQuandoPrecoConfirmadoExigePixMaisCupom() {
        var cupom = new br.com.akesofertas.cupons.domain.CupomAplicavel(
                13558453L, null, "Cupom", null,
                new BigDecimal("28.00"), new BigDecimal("151.90"), null, null,
                ValidationConfidence.HIGH, Instant.now(), "PIX_PLUS_COUPON", null);
        var oferta = criarOferta(new BigDecimal("499.90"), new BigDecimal("179.90"), "64% OFF", null)
                .comCupom(cupom);

        String msg = service.montarMensagem(oferta);

        assertTrue(msg.contains("🔥 <b>R$ 151,90 no Pix + cupom</b>"));
        assertFalse(msg.contains("🔥 Com cupom: <b>R$ 151,90</b>"));
    }
}


