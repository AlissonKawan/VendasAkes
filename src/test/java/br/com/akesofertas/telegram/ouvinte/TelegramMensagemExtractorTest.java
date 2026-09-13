package br.com.akesofertas.telegram.ouvinte;

import it.tdlight.jni.TdApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TelegramMensagemExtractorTest {

    @Test
    void extraiLegendaDeFotoEUrlOculta() {
        var link = new TdApi.TextEntity(3, 6,
                new TdApi.TextEntityTypeTextUrl("https://bit.ly/campanha"));
        var foto = new TdApi.MessagePhoto();
        foto.caption = new TdApi.FormattedText("Cupom 30%", new TdApi.TextEntity[]{link});

        assertEquals("Cupom 30%" + System.lineSeparator() + "https://bit.ly/campanha",
                TelegramMensagemExtractor.extrair(foto));
    }

    @Test
    void extraiMensagemDeTextoNormal() {
        var texto = new TdApi.MessageText();
        texto.text = new TdApi.FormattedText("PROMO10 10% OFF", new TdApi.TextEntity[0]);

        assertEquals("PROMO10 10% OFF", TelegramMensagemExtractor.extrair(texto));
    }

    @Test
    void mantemCadaUrlOcultaProximaDoRespectivoCupom() {
        String conteudo = "CUPOM10 10% OFF link\nCUPOM20 20% OFF link";
        int primeiroLink = conteudo.indexOf("link") + 4;
        int segundoLink = conteudo.lastIndexOf("link") + 4;
        var entities = new TdApi.TextEntity[]{
                new TdApi.TextEntity(primeiroLink - 4, 4,
                        new TdApi.TextEntityTypeTextUrl("https://bit.ly/primeiro")),
                new TdApi.TextEntity(segundoLink - 4, 4,
                        new TdApi.TextEntityTypeTextUrl("https://bit.ly/segundo"))
        };

        String extraido = TelegramMensagemExtractor.extrair(new TdApi.FormattedText(conteudo, entities));

        int primeiroCupom = extraido.indexOf("CUPOM10");
        int primeiraUrl = extraido.indexOf("https://bit.ly/primeiro");
        int segundoCupom = extraido.indexOf("CUPOM20");
        int segundaUrl = extraido.indexOf("https://bit.ly/segundo");
        org.junit.jupiter.api.Assertions.assertTrue(primeiroCupom < primeiraUrl && primeiraUrl < segundoCupom);
        org.junit.jupiter.api.Assertions.assertTrue(segundoCupom < segundaUrl);
    }

    @Test
    void ignoraTipoSemTexto() {
        assertNull(TelegramMensagemExtractor.extrair(new TdApi.MessageSticker()));
    }
}
