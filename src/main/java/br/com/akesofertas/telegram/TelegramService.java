package br.com.akesofertas.telegram;

import br.com.akesofertas.publicacao.OfertaPublicada;
import br.com.akesofertas.exception.TelegramException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.time.Duration;

import java.util.HashMap;
import java.util.Map;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramService {
    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private final URI endpointMensagem;
    private final URI endpointFoto;
    private final String chatId;
    private final RestTemplate restTemplate;
    private final MensagemOfertaService mensagemOfertaService;

    @Autowired
    public TelegramService(
            @Value("${telegram.bot-token}") String botToken,
            @Value("${telegram.chat-id}") String chatId,
            MensagemOfertaService mensagemOfertaService) {
        this(botToken, chatId, mensagemOfertaService, criarHttp());
    }

    TelegramService(String botToken, String chatId, MensagemOfertaService mensagemOfertaService,
                    RestTemplate restTemplate) {
        String token = obrigatorio(botToken, "TELEGRAM_BOT_TOKEN");
        this.chatId = obrigatorio(chatId, "TELEGRAM_CHAT_ID");
        // Impede caracteres que alterariam o caminho, query ou fragmento da URI.
        if (!token.matches("[A-Za-z0-9_:-]+")) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN inválido");
        }
        this.endpointMensagem = URI.create("https://api.telegram.org/bot" + token + "/sendMessage");
        this.endpointFoto = URI.create("https://api.telegram.org/bot" + token + "/sendPhoto");
        this.restTemplate = restTemplate;
        this.mensagemOfertaService = mensagemOfertaService;
    }

    public void enviarOferta(OfertaPublicada oferta) {
        enviarOferta(oferta, null);
    }

    public void enviarOferta(OfertaPublicada oferta, String imagemUrl) {
        boolean enviarFoto = urlImagemDiretaValida(imagemUrl);
        if (!enviarFoto) log.info("Telegram: imagem ausente ou inválida; envio textual para itemId={}", oferta.itemId());
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("chat_id", chatId);
            request.put("parse_mode", "HTML");
            request.put(enviarFoto ? "caption" : "text", mensagemOfertaService.montarMensagem(oferta));
            if (enviarFoto) request.put("photo", imagemUrl.trim());
            request.put("reply_markup", botoes(oferta));
            if (enviarFoto) log.info("Rastreamento imagem: itemId={} etapa=antes-sendPhoto imagemPresente=true imagemUrl={}",
                    oferta.itemId(), imagemUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    enviarFoto ? endpointFoto : endpointMensagem, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new TelegramException("Falha ao enviar Telegram: HTTP " + response.getStatusCode().value());
            }
            var body = new ObjectMapper().readTree(response.getBody());
            if (body == null || !body.path("ok").isBoolean() || !body.path("ok").asBoolean()) {
                throw new TelegramException("Telegram não confirmou o envio (ok=true ausente)");
            }
        } catch (RestClientResponseException e) {
            // HTTP 400 confirma que a foto não foi publicada; o upload multipart pode ser tentado sem duplicar mensagem.
            if (enviarFoto && e.getStatusCode().value() == 400) {
                log.warn("Telegram recusou a URL da imagem; tentando upload multipart para itemId={}", oferta.itemId());
                enviarFotoMultipart(oferta, imagemUrl);
                return;
            }
            throw new TelegramException("Falha ao enviar Telegram: HTTP " + e.getStatusCode().value());
        } catch (TelegramException e) {
            throw e;
        } catch (Exception e) {
            // Não propagar URI, corpo remoto ou causa: podem conter credenciais.
            throw new TelegramException("Não foi possível confirmar o envio ao Telegram; verifique a conexão e a resposta antes de tentar novamente");
        }
    }

    private void enviarFotoMultipart(OfertaPublicada oferta, String imagemUrl) {
        try {
            URI download = URI.create(imagemUrl.endsWith(".webp")
                    ? imagemUrl.substring(0, imagemUrl.length() - 5) + ".jpg" : imagemUrl);
            ResponseEntity<byte[]> imagem = restTemplate.getForEntity(download, byte[].class);
            byte[] bytes = imagem.getBody();
            MediaType tipo = imagem.getHeaders().getContentType();
            if (!imagem.getStatusCode().is2xxSuccessful() || bytes == null || bytes.length == 0
                    || bytes.length > 10 * 1024 * 1024 || tipo == null || !"image".equals(tipo.getType())) {
                throw new TelegramException("Imagem do produto não pôde ser preparada para envio");
            }

            var recurso = new ByteArrayResource(bytes) {
                @Override public String getFilename() { return "oferta.jpg"; }
            };
            var cabecalhoArquivo = new HttpHeaders();
            cabecalhoArquivo.setContentType(tipo);
            var formulario = new LinkedMultiValueMap<String, Object>();
            formulario.add("chat_id", chatId);
            formulario.add("caption", mensagemOfertaService.montarMensagem(oferta));
            formulario.add("parse_mode", "HTML");
            formulario.add("reply_markup", new ObjectMapper().writeValueAsString(botoes(oferta)));
            formulario.add("photo", new HttpEntity<>(recurso, cabecalhoArquivo));
            var cabecalhos = new HttpHeaders();
            cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
            log.info("Rastreamento imagem: itemId={} etapa=antes-sendPhoto-multipart imagemPresente=true", oferta.itemId());
            ResponseEntity<String> resposta = restTemplate.postForEntity(endpointFoto,
                    new HttpEntity<>(formulario, cabecalhos), String.class);
            confirmar(resposta);
        } catch (TelegramException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TelegramException("Não foi possível enviar a imagem do produto ao Telegram");
        }
    }

    private static void confirmar(ResponseEntity<String> response) throws Exception {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new TelegramException("Falha ao enviar Telegram: HTTP " + response.getStatusCode().value());
        }
        var body = new ObjectMapper().readTree(response.getBody());
        if (body == null || !body.path("ok").isBoolean() || !body.path("ok").asBoolean()) {
            throw new TelegramException("Telegram não confirmou o envio (ok=true ausente)");
        }
    }

    private static Map<String, Object> botoes(OfertaPublicada oferta) {
        var linhaComprar = Map.<String, Object>of("text", "🛒 COMPRAR AGORA", "url", oferta.urlAfiliado());
        return Map.of("inline_keyboard", java.util.List.of(java.util.List.of(linhaComprar)));
    }

    private static boolean urlHttpValida(String valor) {
        if (valor == null || valor.isBlank()) return false;
        try {
            URI uri = URI.create(valor.trim());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean urlImagemDiretaValida(String valor) {
        if (!urlHttpValida(valor)) return false;
        URI uri = URI.create(valor.trim());
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        return "https".equalsIgnoreCase(uri.getScheme())
                && (host.equals("mlstatic.com") || host.endsWith(".mlstatic.com"));
    }

    private static String obrigatorio(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(nome + " não configurado");
        }
        return valor.trim();
    }

    private static RestTemplate criarHttp() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return new RestTemplate(factory);
    }
}

