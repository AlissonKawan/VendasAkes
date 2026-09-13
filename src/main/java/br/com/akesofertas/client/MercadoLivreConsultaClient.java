package br.com.akesofertas.client;

import br.com.akesofertas.service.MercadoLivreAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.function.Supplier;

/** Transporte de leitura da etapa 2, exclusivamente para api.mercadolibre.com. */
@Component
public class MercadoLivreConsultaClient {
    private final RestClient http;
    private final Supplier<String> accessToken;

    @Autowired
    public MercadoLivreConsultaClient(MercadoLivreAuthService auth, HttpServletRequest request) {
        this(() -> auth.obterAccessToken(request.getSession(false)), builder());
    }

    public MercadoLivreConsultaClient(Supplier<String> accessToken, RestClient.Builder builder) {
        this.accessToken = accessToken;
        this.http = builder.baseUrl("https://api.mercadolibre.com").build();
    }

    JsonNode get(String caminho, String id) {
        String recurso = caminho.replace("{id}", id);
        // Os caminhos são constantes dos clientes de catálogo/preços, nunca URLs do usuário.
        String token = accessToken.get();
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autentique a conta em /mercadolivre/auth.");
        }
        try {
            var resposta = http.get().uri(caminho, id)
                    .headers(headers -> headers.setBearerAuth(token)).retrieve().toEntity(JsonNode.class);
            JsonNode resultado = resposta.getBody();
            if (resposta.getStatusCode().value() != 200 || resultado == null || !resultado.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta inválida da consulta oficial " + recurso + ".");
            }
            return resultado;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String motivo = switch (status) {
                case 401 -> "Token recusado. Autentique novamente em /mercadolivre/auth.";
                case 403 -> "Acesso recusado. Verifique a permissão da aplicação para esse recurso.";
                case 404 -> "Recurso não encontrado nessa consulta. Confirme o ID do catálogo/anúncio e a disponibilidade do recurso.";
                case 429 -> "Limite da API atingido. Aguarde antes de consultar novamente.";
                default -> "Não foi possível concluir a consulta.";
            };
            // Mantém apenas código e mensagem local. Nunca copia body, headers ou token do erro.
            throw new ConsultaException(status, "API oficial retornou HTTP " + status + " em " + recurso
                    + codigoRemoto(exception) + ". " + motivo);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Falha de comunicação com a API oficial em " + recurso + ".");
        }
    }

    private static String codigoRemoto(RestClientResponseException exception) {
        try {
            String body = exception.getResponseBodyAsString();
            if (body.length() > 4096) return "";
            String codigo = new ObjectMapper().readTree(body).path("error").asText("");
            // Somente códigos conhecidos: não repassa message, cause nem valores arbitrários.
            return java.util.Set.of("not_found", "resource_not_found", "forbidden", "not_authorized",
                    "unauthorized", "invalid_token", "bad_request", "too_many_requests", "internal_server_error")
                    .contains(codigo) ? " (error=" + codigo + ")" : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    static void validarId(String id) {
        if (id == null || !id.matches("MLB[0-9]{1,20}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um ID brasileiro no formato MLB seguido de números.");
        }
    }

    private static RestClient.Builder builder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory);
    }

    public static class ConsultaException extends ResponseStatusException {
        private final int statusRemoto;
        public ConsultaException(int statusRemoto, String mensagem) {
            super(statusRemoto == 401 ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_GATEWAY, mensagem);
            this.statusRemoto = statusRemoto;
        }
        public int statusRemoto() { return statusRemoto; }
    }
}
