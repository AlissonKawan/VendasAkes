package br.com.akesofertas.telegram;

import br.com.akesofertas.client.MercadoLivreConsultaClient;
import br.com.akesofertas.client.MercadoLivreProdutosClient;
import br.com.akesofertas.publicacao.OfertaPublicadaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class EnviarOfertaTelegramDiagnosticoTest {
    @Test
    void diagnosticoIniciaSemExigirServidorOuRequisicaoServlet() {
        // Mesma inicialização usada no diagnóstico; recursos de teste usam H2 e token fictício.
        // Não chama Hub, createLink ou Telegram.
        try (var context = EnviarOfertaTelegramDiagnostico.iniciarContexto()) {
            assertFalse(context instanceof WebServerApplicationContext);
            assertTrue(context.getBeansOfType(MercadoLivreConsultaClient.class).isEmpty());
            assertTrue(context.getBeansOfType(MercadoLivreProdutosClient.class).isEmpty());
            assertNotNull(context.getBean(OfertaPublicadaService.class));
            assertNotNull(context.getBean(TelegramService.class));
        }
    }
}
