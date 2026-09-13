package br.com.akesofertas.controller;

import br.com.akesofertas.client.TelegramClient;
import br.com.akesofertas.exception.TelegramException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OfertaControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean TelegramClient telegram;

    private static final String OFERTA = """
            {"titulo":"Mouse","precoAtual":139.90,"loja":"Loja",
             "urlProduto":"https://exemplo.com/produto"}
            """;

    @Test
    void publicaOferta() throws Exception {
        when(telegram.enviar(anyString(), isNull())).thenReturn(42L);
        mvc.perform(post("/api/ofertas/publicar").contentType(MediaType.APPLICATION_JSON).content(OFERTA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.messageId").value(42));
        verify(telegram).enviar(contains("R$ 139,90"), isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "null",
            "{\"titulo\":\" \",\"precoAtual\":-1,\"loja\":\"\",\"urlProduto\":\"\"}",
            "{\"titulo\":\"Mouse\",\"precoAtual\":\"abc\",\"loja\":\"Loja\",\"urlProduto\":\"https://exemplo.com\"}"})
    void rejeitaPayloadInvalido(String json) throws Exception {
        mvc.perform(post("/api/ofertas/publicar").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.sucesso").value(false));
        verifyNoInteractions(telegram);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///tmp/a", "https://", "[https://exemplo.com](https://exemplo.com)"})
    void rejeitaUrlInvalida(String url) throws Exception {
        mvc.perform(post("/api/ofertas/publicar").contentType(MediaType.APPLICATION_JSON)
                        .content(OFERTA.replace("https://exemplo.com/produto", url)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(telegram);
    }

    @Test
    void publicaOfertaComImagem() throws Exception {
        String imagem = "https://exemplo.com/mouse.jpg";
        String json = OFERTA.replace("}", ",\"imagemUrl\":\"" + imagem + "\"}");
        when(telegram.enviar(anyString(), eq(imagem))).thenReturn(43L);
        mvc.perform(post("/api/ofertas/publicar").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.messageId").value(43));
        verify(telegram).enviar(contains("R$ 139,90"), eq(imagem));
    }

    @Test
    void propagaErroTelegram() throws Exception {
        when(telegram.enviar(anyString(), isNull())).thenThrow(new TelegramException("Telegram: chat not found"));
        mvc.perform(post("/api/ofertas/publicar").contentType(MediaType.APPLICATION_JSON).content(OFERTA))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(jsonPath("$.mensagem").value("Telegram: chat not found"));
    }
}
