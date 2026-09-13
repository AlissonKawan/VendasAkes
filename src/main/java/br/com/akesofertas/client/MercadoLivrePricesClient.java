package br.com.akesofertas.client;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Component
public class MercadoLivrePricesClient {
    private final MercadoLivreConsultaClient http;
    public MercadoLivrePricesClient(MercadoLivreConsultaClient http) { this.http = http; }

    public JsonNode consultar(String itemId) {
        MercadoLivreConsultaClient.validarId(itemId);
        JsonNode resposta = http.get("/items/{id}/prices", itemId);
        if (!resposta.path("prices").isArray() || !itemId.equals(resposta.path("id").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Preços não correspondem ao anúncio solicitado ou prices não é uma lista.");
        }
        // Não escolhe min(amount) nem substitui regular_amount ausente por um preço standard.
        return resposta;
    }
}
