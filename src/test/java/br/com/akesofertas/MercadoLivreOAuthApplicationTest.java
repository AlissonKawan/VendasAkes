package br.com.akesofertas;

import br.com.akesofertas.client.TelegramClient;
import br.com.akesofertas.controller.OfertaController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"mercadolivre.app-id=", "mercadolivre.client-secret=", "mercadolivre.redirect-uri="})
@ActiveProfiles("oauth")
@AutoConfigureMockMvc
class MercadoLivreOAuthApplicationTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void perfilOAuthIniciaSemTelegramEExplicaConfiguracaoAusente() throws Exception {
        assertThat(context.getBeansOfType(TelegramClient.class)).isEmpty();
        assertThat(context.getBeansOfType(OfertaController.class)).isEmpty();
        mvc.perform(get("/mercadolivre/auth")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem").value(
                        "Defina MERCADOLIVRE_APP_ID, MERCADOLIVRE_CLIENT_SECRET e MERCADOLIVRE_REDIRECT_URI."));
    }
}
