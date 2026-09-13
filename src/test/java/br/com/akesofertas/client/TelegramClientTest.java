package br.com.akesofertas.client;

import br.com.akesofertas.exception.TelegramException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramClientTest {
    private MockRestServiceServer server;
    private TelegramClient client;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TelegramClient("test-token", "@test", builder);
    }

    @Test
    void enviaTextoHtml() {
        server.expect(requestTo("https://api.telegram.org/bottest-token/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"chat_id":"@test","parse_mode":"HTML","text":"Oferta"}
                        """))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":42}}", MediaType.APPLICATION_JSON));
        assertThat(client.enviar("Oferta", null)).isEqualTo(42);
        server.verify();
    }

    @Test
    void enviaFotoComLegenda() {
        server.expect(requestTo("https://api.telegram.org/bottest-token/sendPhoto"))
                .andExpect(content().json("""
                        {"chat_id":"@test","parse_mode":"HTML","caption":"Oferta",
                         "photo":"https://exemplo.com/foto.jpg"}
                        """))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":43}}", MediaType.APPLICATION_JSON));
        assertThat(client.enviar("Oferta", "https://exemplo.com/foto.jpg")).isEqualTo(43);
        server.verify();
    }

    @Test
    void informaErroHttpSemExporToken() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                .body("{\"ok\":false,\"description\":\"Forbidden test-token\"}"));
        assertThatThrownBy(() -> client.enviar("Oferta", null)).isInstanceOf(TelegramException.class)
                .hasMessageContaining("Forbidden").hasMessageNotContaining("test-token");
    }

    @Test
    void rejeitaOkFalseMesmoComHttp200() {
        server.expect(anything()).andRespond(withSuccess("{\"ok\":false,\"description\":\"chat not found\"}",
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.enviar("Oferta", null)).isInstanceOf(TelegramException.class)
                .hasMessageContaining("chat not found");
    }

    @Test
    void rejeitaRespostaSemMessageId() {
        server.expect(anything()).andRespond(withSuccess("{\"ok\":true,\"result\":{}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.enviar("Oferta", null)).isInstanceOf(TelegramException.class)
                .hasMessageContaining("message_id");
    }

    @Test
    void trataRespostaNaoJson() {
        server.expect(anything()).andRespond(withSuccess("<html>erro</html>", MediaType.TEXT_HTML));
        assertThatThrownBy(() -> client.enviar("Oferta", null)).isInstanceOf(TelegramException.class)
                .hasMessageNotContaining("test-token");
    }

    @Test
    void trataTimeoutSemRepetirEnvio() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("test-token")));
        assertThatThrownBy(() -> client.enviar("Oferta", null)).isInstanceOf(TelegramException.class)
                .hasMessageContaining("Verifique a conexão e o canal").hasMessageNotContaining("test-token");
        server.verify();
    }

    @Test
    void exigeVariaveisDeAmbiente() {
        assertThatThrownBy(() -> new TelegramClient("", "@test")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_BOT_TOKEN");
        assertThatThrownBy(() -> new TelegramClient("test-token", " ")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_CHAT_ID");
    }
}
