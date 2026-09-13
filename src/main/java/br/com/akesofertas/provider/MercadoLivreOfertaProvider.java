package br.com.akesofertas.provider;

import br.com.akesofertas.client.MercadoLivreCatalogItemsClient;
import br.com.akesofertas.client.MercadoLivreConsultaClient.ConsultaException;
import br.com.akesofertas.client.MercadoLivrePricesClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;

@Component
public class MercadoLivreOfertaProvider implements OfertaProvider {
    private final MercadoLivreCatalogItemsClient catalogo;
    private final MercadoLivrePricesClient precos;

    public MercadoLivreOfertaProvider(MercadoLivreCatalogItemsClient catalogo, MercadoLivrePricesClient precos) {
        this.catalogo = catalogo;
        this.precos = precos;
    }

    @Override
    public String codigo() { return "mercadolivre"; }

    @Override
    public Avaliacao avaliar(ProdutoEncontrado produto) {
        try {
            return converter(produto, catalogo.consultarProduto(produto.id()));
        } catch (ConsultaException exception) {
            // Ausência deste recurso afeta apenas o candidato. Autorização, limite e falhas do servidor
            // continuam sendo erros explícitos, sem tentativas por outras rotas.
            if (exception.statusRemoto() == 404) return Avaliacao.descartar(exception.getReason());
            throw exception;
        }
    }

    private Avaliacao converter(ProdutoEncontrado produto, JsonNode detalhes) {
        var vencedor = detalhes.path("buy_box_winner");
        if (vencedor.isNull()) return Avaliacao.descartar("Sem oferta utilizável: buy_box_winner nulo.");
        if (!vencedor.isObject()) return Avaliacao.descartar("buy_box_winner ausente ou em formato não reconhecido.");
        if (!"active".equals(detalhes.path("status").asText())) return Avaliacao.descartar("Catálogo não está ativo.");
        String itemId = vencedor.path("item_id").asText("");
        Long vendedorId = inteiro(vencedor.path("seller_id"));
        if (!itemId.matches("MLB[0-9]{1,20}") || vendedorId == null || vendedorId <= 0) {
            return Avaliacao.descartar("Vencedor sem item_id ou seller_id válido.");
        }
        Long quantidade = inteiro(vencedor.path("available_quantity"));
        if (quantidade != null && quantidade <= 0) return Avaliacao.descartar("Vencedor sem quantidade disponível.");
        var campoQuantidade = vencedor.path("available_quantity");
        if (!campoQuantidade.isMissingNode() && !campoQuantidade.isNull() && quantidade == null) {
            return Avaliacao.descartar("Quantidade disponível em formato não reconhecido.");
        }
        String url = detalhes.path("permalink").asText("");
        if (!urlOficial(url)) return Avaliacao.descartar("Catálogo sem permalink oficial utilizável.");
        // /products/{id}/items é apenas diagnóstico opcional. O ITEM_ID vem diretamente do vencedor.
        var preco = MercadoLivrePrecoOferta.confirmar(vencedor, precos.consultar(itemId), Instant.now());
        if (preco == null) return Avaliacao.descartar("Preço não confirmado: valor, moeda, condições ou referência incompatíveis/ambíguos.");
        return new Avaliacao(new OfertaEncontrada(codigo(), produto.id(), itemId, produto.titulo(), url,
                produto.imagemUrl(), vendedorId, preco.atual(), preco.regular(), preco.originalCatalogo(),
                preco.moeda(), preco.promocional(), preco.desconto(), preco.id(), entrega(vencedor.path("shipping")), quantidade),
                "Preço da buy box confirmado na API oficial de preços.");
    }

    private static boolean urlOficial(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && "www.mercadolivre.com.br".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getPath() != null && uri.getPath().length() > 1;
        } catch (IllegalArgumentException exception) { return false; }
    }

    private static Long inteiro(JsonNode campo) {
        return campo.isIntegralNumber() && campo.canConvertToLong() ? campo.longValue() : null;
    }

    private static OfertaEncontrada.Entrega entrega(JsonNode shipping) {
        if (!shipping.isObject()) return null;
        var tags = shipping.path("tags").isArray() ? new ArrayList<String>() : null;
        if (tags != null) for (var tag : shipping.path("tags")) if (tag.isString()) tags.add(tag.asText());
        return new OfertaEncontrada.Entrega(booleano(shipping.path("free_shipping")), booleano(shipping.path("store_pick_up")),
                shipping.path("mode").asText(null), shipping.path("logistic_type").asText(null), tags);
    }

    private static Boolean booleano(JsonNode campo) { return campo.isBoolean() ? campo.booleanValue() : null; }
}
