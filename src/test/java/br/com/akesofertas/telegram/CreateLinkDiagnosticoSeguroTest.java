package br.com.akesofertas.telegram;

import br.com.akesofertas.afiliados.AffiliateLinkResponse;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CreateLinkDiagnosticoSeguroTest {
    @Test
    void mostraErroRealSemQueryFragmentoOuLongUrl() {
        var resposta = new AffiliateLinkResponse(200, List.of(new AffiliateLinkResponse.Link(
                null, null, null,
                "https://www.mercadolivre.com.br/produto/p/MLB1?segredo=1#sessao",
                111, "URL not allowed in affiliates program", 200)), 1, 0, 1);
        var bytes = new ByteArrayOutputStream();

        EnviarOfertaTelegramDiagnostico.imprimirFalhasCreateLink(
                new PrintStream(bytes, true, StandardCharsets.UTF_8), resposta);

        String texto = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(texto.contains("www.mercadolivre.com.br/produto/p/MLB1"));
        assertTrue(texto.contains("error_code: 111"));
        assertTrue(texto.contains("message: URL not allowed in affiliates program"));
        assertTrue(texto.contains("status: 200"));
        assertFalse(texto.contains("segredo"));
        assertFalse(texto.contains("sessao"));
        assertFalse(texto.contains("long_url"));
    }
}
