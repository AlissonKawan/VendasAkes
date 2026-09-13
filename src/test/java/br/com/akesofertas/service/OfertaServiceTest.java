package br.com.akesofertas.service;

import br.com.akesofertas.client.TelegramClient;
import br.com.akesofertas.dto.OfertaRequest;
import br.com.akesofertas.exception.OfertaInvalidaException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfertaServiceTest {
    private final TelegramClient telegram = mock(TelegramClient.class);
    private final OfertaService service = new OfertaService(telegram);

    @Test
    void formataMoedaEOpcionaisEEscapaHtml() {
        String mensagem = service.formatar(new OfertaRequest("<Mouse> & teclado", new BigDecimal("1234.90"),
                new BigDecimal("1999.90"), "Loja & Cia", "https://exemplo.com/?a=1&b=2", "<CUPOM>", null));
        assertThat(mensagem).contains("R$ 1.234,90", "R$ 1.999,90", "&lt;Mouse&gt; &amp; teclado",
                "Loja &amp; Cia", "&lt;CUPOM&gt;", "?a=1&amp;b=2", "Akes Ofertas | Ofertas &amp; Cupons");
    }

    @Test
    void omiteCamposOpcionaisVazios() {
        String mensagem = service.formatar(oferta("https://exemplo.com", " "));
        assertThat(mensagem).doesNotContain("Preço anterior", "Cupom:", "null");
    }

    @Test
    void rejeitaLegendaGrandeAntesDeEnviar() {
        assertThatThrownBy(() -> service.publicar(oferta("https://exemplo.com/" + "a".repeat(1000),
                "https://exemplo.com/foto.jpg"))).isInstanceOf(OfertaInvalidaException.class)
                .hasMessageContaining("1024");
        verifyNoInteractions(telegram);
    }

    @Test
    void permiteTextoMaiorQueLegenda() {
        OfertaRequest oferta = oferta("https://exemplo.com/" + "a".repeat(1000), null);
        service.publicar(oferta);
        verify(telegram).enviar(service.formatar(oferta), null);
    }

    @Test
    void rejeitaImagemInvalida() {
        assertThatThrownBy(() -> service.publicar(oferta("https://exemplo.com", "ftp://exemplo.com/foto")))
                .isInstanceOf(OfertaInvalidaException.class).hasMessageContaining("imagemUrl");
        verifyNoInteractions(telegram);
    }

    private OfertaRequest oferta(String url, String imagem) {
        return new OfertaRequest("Mouse", new BigDecimal("139.90"), null, "Loja", url, " ", imagem);
    }
}
