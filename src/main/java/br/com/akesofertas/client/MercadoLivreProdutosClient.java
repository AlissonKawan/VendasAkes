package br.com.akesofertas.client;

import br.com.akesofertas.service.MercadoLivreAuthService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

@Component
public class MercadoLivreProdutosClient {
    private final RestClient http;
    private final Supplier<String> accessToken;

    @Autowired
    public MercadoLivreProdutosClient(MercadoLivreAuthService auth, HttpServletRequest request) {
        // Nesta etapa manual, aproveita a sessão OAuth do navegador que chama a busca.
        // O futuro agendador precisará de uma fonte de tokens independente de HttpSession.
        this(() -> auth.obterAccessToken(request.getSession(false)), builder());
    }

    public MercadoLivreProdutosClient(Supplier<String> accessToken, RestClient.Builder builder) {
        this.accessToken = accessToken;
        this.http = builder.baseUrl("https://api.mercadolibre.com").build();
    }

    public List<ProdutoCatalogo> buscar(String termo, int limite) {
        String token = accessToken.get();
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autentique a conta em /mercadolivre/auth.");
        }
        try {
            // Parâmetros são codificados pelo RestClient; o termo não vira parte da URI base.
            Resultado resultado = http.get()
                    .uri("/products/search?status=active&site_id=MLB&q={termo}&limit={limite}", termo, limite)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(Resultado.class);
            if (resultado == null || resultado.results() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "API de produtos retornou uma resposta inválida.");
            }
            return resultado.results();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            HttpStatus resposta = status == 401 ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_GATEWAY;
            String motivo = switch (status) {
                case 401 -> "Token recusado pela API. Autentique novamente em /mercadolivre/auth.";
                case 403 -> "API oficial recusou a busca (HTTP 403). Verifique as permissões e o acesso da aplicação a /products/search.";
                case 429 -> "Limite da API oficial atingido (HTTP 429). Aguarde antes de tentar novamente.";
                default -> "Falha na busca oficial de produtos (HTTP " + status + ").";
            };
            // Não expõe o body/headers da exceção e não tenta outras rotas para contornar 403.
            throw new ResponseStatusException(resposta, motivo);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha de comunicação com a API oficial de produtos.");
        }
    }

    private static RestClient.Builder builder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resultado(List<ProdutoCatalogo> results) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProdutoCatalogo(String id, String name, String status, List<Imagem> pictures) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Imagem(String url, @JsonProperty("secure_url") String secureUrl) {}
}
