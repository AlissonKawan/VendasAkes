package br.com.akesofertas.service;

import br.com.akesofertas.client.MercadoLivreCatalogItemsClient;
import br.com.akesofertas.client.MercadoLivreConsultaClient;
import br.com.akesofertas.client.MercadoLivrePricesClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class CatalogoDiagnosticoService {
    private final MercadoLivreCatalogItemsClient catalogo;
    private final MercadoLivrePricesClient precos;

    public CatalogoDiagnosticoService(MercadoLivreCatalogItemsClient catalogo, MercadoLivrePricesClient precos) {
        this.catalogo = catalogo;
        this.precos = precos;
    }

    public Diagnostico consultar(String productId, int limite) {
        if (limite < 1 || limite > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O diagnóstico aceita limite entre 1 e 5 anúncios.");
        }
        JsonNode resposta;
        try {
            resposta = catalogo.consultar(productId);
        } catch (MercadoLivreConsultaClient.ConsultaException exception) {
            if (exception.statusRemoto() != 404) throw exception;
            // Um 404 nos anúncios não comprova que o produto inexiste. Consulta os
            // metadados do MESMO catálogo para investigar, sem obter anúncios por outra rota.
            return new Diagnostico(productId, List.of(), null, null, List.of(), false,
                    "Produto sem listagem consultável por /products/{id}/items (404). Diagnóstico opcional; não invalida a busca por buy_box_winner.",
                    404, exception.getReason(), detalhesProduto(productId));
        }
        List<Anuncio> amostra = new ArrayList<>();
        Set<String> consultados = new HashSet<>();
        boolean concluido = true;
        // Inspeciona na ordem da API, sem ranquear, paginar ou escolher um vencedor.
        for (JsonNode anuncio : resposta.path("results")) {
            if (amostra.size() == limite) break;
            String itemId = anuncio.path("item_id").asText("");
            if (!itemId.matches("MLB[0-9]{1,20}") || !consultados.add(itemId)) {
                amostra.add(new Anuncio(itemId, campos(anuncio), anuncio, null, null,
                        "Anúncio sem item_id válido ou repetido; preços não consultados."));
                concluido = false;
                continue;
            }
            try {
                amostra.add(new Anuncio(itemId, campos(anuncio), anuncio, precos.consultar(itemId), 200, null));
            } catch (ResponseStatusException exception) {
                Integer remoto = exception instanceof MercadoLivreConsultaClient.ConsultaException erro ? erro.statusRemoto() : null;
                amostra.add(new Anuncio(itemId, campos(anuncio), anuncio, null, remoto, exception.getReason()));
                concluido = false;
                // Não insiste após recusa/timeout; preserva a evidência parcial e informa a interrupção.
                break;
            }
        }
        return new Diagnostico(productId, campos(resposta), resposta.get("paging"), resposta.path("results").size(),
                List.copyOf(amostra), concluido, "Amostra de diagnóstico na ordem da API; nenhum vencedor ou desconto calculado.",
                200, null, null);
    }

    private DetalhesProduto detalhesProduto(String productId) {
        try {
            JsonNode produto = catalogo.consultarProduto(productId);
            Map<String, JsonNode> resumo = new LinkedHashMap<>();
            for (String campo : List.of("id", "name", "status", "domain_id", "parent_id", "children_ids",
                    "buy_box_winner", "buy_box_activation_date", "settings", "permalink")) {
                if (produto.has(campo)) resumo.put(campo, produto.get(campo));
            }
            return new DetalhesProduto(200, campos(produto), resumo, null);
        } catch (ResponseStatusException exception) {
            Integer remoto = exception instanceof MercadoLivreConsultaClient.ConsultaException erro ? erro.statusRemoto() : null;
            return new DetalhesProduto(remoto, List.of(), Map.of(), exception.getReason());
        }
    }

    private List<String> campos(JsonNode node) {
        return node.properties().stream().map(java.util.Map.Entry::getKey).sorted().toList();
    }

    public record Diagnostico(String produtoId, List<String> camposCatalogo, JsonNode paginacao,
                              Integer anunciosNaPagina, List<Anuncio> anuncios, boolean amostraConcluida, String observacao,
                              int httpCatalogo, String erroCatalogo, DetalhesProduto detalhesProduto) {}
    public record Anuncio(String anuncioId, List<String> camposAnuncio, JsonNode dadosOficiaisAnuncio,
                           JsonNode dadosOficiaisPrecos, Integer httpPrecos, String erroPrecos) {}
    public record DetalhesProduto(Integer http, List<String> campos, Map<String, JsonNode> dadosOficiais, String erro) {}
}
