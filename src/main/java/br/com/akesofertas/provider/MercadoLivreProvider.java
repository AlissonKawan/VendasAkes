package br.com.akesofertas.provider;

import br.com.akesofertas.client.MercadoLivreProdutosClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class MercadoLivreProvider implements ProdutoProvider {
    private final MercadoLivreProdutosClient client;

    public MercadoLivreProvider(MercadoLivreProdutosClient client) {
        this.client = client;
    }

    @Override
    public String codigo() { return "mercadolivre"; }

    @Override
    public List<ProdutoEncontrado> buscar(String termo, int limite) {
        return client.buscar(termo, limite).stream().limit(limite).map(this::converter).toList();
    }

    private ProdutoEncontrado converter(MercadoLivreProdutosClient.ProdutoCatalogo produto) {
        if (produto == null || produto.id() == null || !produto.id().matches("MLB[0-9]+")
                || produto.name() == null || produto.name().isBlank() || !"active".equals(produto.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "API retornou um produto de catálogo inválido.");
        }
        String imagem = null;
        if (produto.pictures() != null && !produto.pictures().isEmpty() && produto.pictures().getFirst() != null) {
            var foto = produto.pictures().getFirst();
            imagem = foto.secureUrl() != null ? foto.secureUrl() : foto.url();
        }
        // URL da página do catálogo, derivada do ID; ainda não seleciona um anúncio/vendedor.
        return new ProdutoEncontrado(codigo(), produto.id(), produto.name(),
                "https://www.mercadolivre.com.br/p/" + produto.id(), imagem);
    }
}
