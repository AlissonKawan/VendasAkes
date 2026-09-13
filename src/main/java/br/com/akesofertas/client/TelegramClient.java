package br.com.akesofertas.client;

import br.com.akesofertas.exception.TelegramException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramClient {
    private final RestClient http;
    private final String token;
    private final String chatId;

    @Autowired
    public TelegramClient(@Value("${telegram.bot-token:}") String token,
                          @Value("${telegram.chat-id:}") String chatId) {
        this(token, chatId, builder());
    }

    public TelegramClient(String token, String chatId, RestClient.Builder builder) {
        this.token = obrigatorio(token, "TELEGRAM_BOT_TOKEN");
        this.chatId = obrigatorio(chatId, "TELEGRAM_CHAT_ID");
        this.http = builder.baseUrl("https://api.telegram.org/bot" + this.token).build();
    }

    public long enviar(String mensagem, String imagemUrl) {
        boolean foto = imagemUrl != null && !imagemUrl.isBlank();
        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("parse_mode", "HTML");
        payload.put(foto ? "caption" : "text", mensagem);
        if (foto) {
            payload.put("photo", imagemUrl);
        }
        try {
            return http.post().uri(foto ? "/sendPhoto" : "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON).body(payload)
                    .exchange((request, response) -> {
                        TelegramResponse body = response.bodyTo(TelegramResponse.class);
                        if (!response.getStatusCode().is2xxSuccessful() || body == null || !body.ok()) {
                            String descricao = body == null ? null : body.description();
                            throw new TelegramException(descricao == null || descricao.isBlank()
                                    ? "Telegram recusou a publicação (HTTP " + response.getStatusCode().value() + ")"
                                    : "Telegram: " + descricao.replace(token, "[REDACTED]"));
                        }
                        if (body.result() == null || body.result().messageId() == null) {
                            throw new TelegramException("Telegram retornou uma resposta sem message_id");
                        }
                        return body.result().messageId();
                    });
        } catch (RestClientException exception) {
            // Não expõe a URL da requisição, que contém o token.
            throw new TelegramException("Não foi possível confirmar a publicação no Telegram. "
                    + "Verifique a conexão e o canal antes de tentar novamente.");
        }
    }

    private static RestClient.Builder builder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory);
    }

    private static String obrigatorio(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Defina a variável de ambiente " + nome + " antes de iniciar");
        }
        return valor.trim();
    }

    public record TelegramResponse(boolean ok, String description, TelegramMessage result) {}
    public record TelegramMessage(@JsonProperty("message_id") Long messageId) {}
}
