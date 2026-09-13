package br.com.akesofertas.client;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Component
public class MercadoLivreCatalogItemsClient {
    private final MercadoLivreConsultaClient http;
    public MercadoLivreCatalogItemsClient(MercadoLivreConsultaClient http) { this.http = http; }

    public JsonNode consultar(String productId) {
        MercadoLivreConsultaClient.validarId(productId);
        JsonNode resposta = http.get("/products/{id}/items", productId);
        if (!resposta.path("results").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogo não retornou results como uma lista de anúncios.");
        }
        // Preserva os campos reais antes de definir uma seleção de candidatos/vencedor.
        return resposta;
    }

    public JsonNode consultarProduto(String productId) {
        MercadoLivreConsultaClient.validarId(productId);
        JsonNode resposta = http.get("/products/{id}", productId);
        if (!productId.equals(resposta.path("id").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Detalhes do catálogo não correspondem ao produto solicitado.");
        }
        return resposta;
    }
}
