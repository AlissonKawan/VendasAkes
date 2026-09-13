package br.com.akesofertas.telegram;

import br.com.akesofertas.exception.TelegramException;
import br.com.akesofertas.publicacao.OfertaPublicada;
import br.com.akesofertas.publicacao.StatusPublicacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketTimeoutException;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramServiceTest {
    private static final String TOKEN = "123456:TEST_ONLY_fake-token";
    private RestTemplate http;
    private MockRestServiceServer server;
    private MensagemOfertaService mensagens;
    private TelegramService telegram;
    private OfertaPublicada oferta;

    @BeforeEach
    void setup() {
        http = new RestTemplate();
        server = MockRestServiceServer.bindTo(http).build();
        mensagens = mock(MensagemOfertaService.class);
        oferta = new OfertaPublicada(1L, "ML", "MLB1", null, "Oferta", "https://produto.test/1",
                "https://meli.la/1", BigDecimal.TEN, BigDecimal.ONE, "90% OFF", null, null,
                StatusPublicacao.PENDENTE_ENVIO, Instant.now(), null, null);
        when(mensagens.montarMensagem(oferta)).thenReturn("Oferta");
        telegram = new TelegramService(TOKEN, "@test", mensagens, http);
    }

    @Test
    void imagemValidaUsaSendPhotoComHtmlEBotoes() {
        server.expect(request -> {
            // Booleanos evitam que o framework imprima a URI com token em uma falha.
            assertTrue(request.getURI().toString().equals("https://api.telegram.org/bot" + TOKEN + "/sendPhoto"), "Endpoint incorreto");
            assertTrue(request.getMethod().name().equals("POST"));
        }).andExpect(content().json("""
                {"chat_id":"@test","parse_mode":"HTML","caption":"Oferta",
                 "photo":"https://http2.mlstatic.com/foto.jpg","reply_markup":{"inline_keyboard":[
                   [{"text":"🛒 COMPRAR AGORA","url":"https://meli.la/1"}]
                 ]}}
                """))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":1}}", MediaType.APPLICATION_JSON));
        telegram.enviarOferta(oferta, "https://http2.mlstatic.com/foto.jpg");
        server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "sem-url", "javascript:alert(1)", "https://usuario@img.test/a.jpg"})
    void imagemAusenteOuInvalidaUsaSendMessage(String imagem) {
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendMessage"))
                .andExpect(content().json("""
                        {"chat_id":"@test","parse_mode":"HTML","text":"Oferta",
                         "reply_markup":{"inline_keyboard":[
                           [{"text":"🛒 COMPRAR AGORA","url":"https://meli.la/1"}]
                         ]}}
                        """))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":1}}", MediaType.APPLICATION_JSON));
        telegram.enviarOferta(oferta, imagem);
        server.verify();
    }

    @Test
    void imagemRemotaRecusadaComHttp400FazFallbackTextual() {
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendPhoto"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"description\":\"wrong type of the web page content\"}"));
        server.expect(requestTo("https://http2.mlstatic.com/foto.jpg"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_JPEG));
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendPhoto"))
                .andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith("multipart/form-data")))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":2}}", MediaType.APPLICATION_JSON));
        telegram.enviarOferta(oferta, "https://http2.mlstatic.com/foto.webp");
        server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void tokenAusenteFalhaNaConstrucaoSemHttp(String token) {
        RestTemplate semHttp = mock(RestTemplate.class);
        var erro = assertThrows(IllegalStateException.class,
                () -> new TelegramService(token, "@test", mensagens, semHttp));
        assertTrue("TELEGRAM_BOT_TOKEN não configurado".equals(erro.getMessage()));
        verifyNoInteractions(semHttp);
        seguro(erro);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void chatAusenteFalhaNaConstrucaoSemHttp(String chat) {
        RestTemplate semHttp = mock(RestTemplate.class);
        var erro = assertThrows(IllegalStateException.class,
                () -> new TelegramService(TOKEN, chat, mensagens, semHttp));
        assertTrue("TELEGRAM_CHAT_ID não configurado".equals(erro.getMessage()));
        verifyNoInteractions(semHttp);
        seguro(erro);
    }

    @Test
    void tokenComCaracteresDeUriFalhaSemExporValor() {
        String invalido = TOKEN + "#privado";
        var erro = assertThrows(IllegalStateException.class,
                () -> new TelegramService(invalido, "@test", mensagens, http));
        seguro(erro);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 404})
    void erroHttpNaoExpoeUriNemResposta(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("{\"ok\":false,\"description\":\"" + TOKEN + "\"}")
                .contentType(MediaType.APPLICATION_JSON));
        var erro = assertThrows(TelegramException.class, () -> telegram.enviarOferta(oferta));
        seguro(erro);
        assertTrue(erro.getMessage().contains("HTTP " + status));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"ok\":false}", "{}", "null", "", "<html>erro</html>", "{\"ok\":\"true\"}"})
    void respostaSemConfirmacaoNaoEhSucesso(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        seguro(assertThrows(TelegramException.class, () -> telegram.enviarOferta(oferta)));
        server.verify();
    }

    @Test
    void tokenNuncaApareceEmExceptions() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("https://api.telegram.org/bot" + TOKEN + "/sendMessage")));
        seguro(assertThrows(TelegramException.class, () -> telegram.enviarOferta(oferta)));
        server.verify(); // Uma tentativa, sem repetição automática.
    }

    private void seguro(Throwable erro) {
        StringWriter trace = new StringWriter();
        erro.printStackTrace(new PrintWriter(trace));
        assertFalse(trace.toString().contains(TOKEN), "Credencial exposta na exception");
        assertNull(erro.getCause());
        assertEquals(0, erro.getSuppressed().length);
    }
}
