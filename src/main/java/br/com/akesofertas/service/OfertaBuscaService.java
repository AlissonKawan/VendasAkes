package br.com.akesofertas.service;

import br.com.akesofertas.provider.OfertaEncontrada;
import br.com.akesofertas.provider.OfertaProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
public class OfertaBuscaService {
    private final ProdutoBuscaService produtos;
    private final List<OfertaProvider> providers;

    public OfertaBuscaService(ProdutoBuscaService produtos, List<OfertaProvider> providers) {
        this.produtos = produtos;
        this.providers = List.copyOf(providers);
    }

    public Resultado buscar(String fornecedor, String termo, int limite) {
        var provider = providers.stream().filter(p -> p.codigo().equals(fornecedor)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fornecedor de ofertas não disponível."));
        var candidatos = produtos.buscar(fornecedor, termo, limite);
        var ofertas = new ArrayList<OfertaEncontrada>();
        var avaliados = new ArrayList<Candidato>();
        var vistos = new HashSet<String>();
        for (var produto : candidatos) {
            // Evita consultar duas vezes o mesmo catálogo dentro desta requisição, sem persistência.
            if (!vistos.add(produto.id())) continue;
            var resultado = provider.avaliar(produto);
            boolean utilizavel = resultado.oferta() != null;
            if (utilizavel) ofertas.add(resultado.oferta());
            avaliados.add(new Candidato(produto.id(), utilizavel, resultado.motivo()));
        }
        return new Resultado(avaliados.size(), List.copyOf(ofertas), List.copyOf(avaliados));
    }

    public record Resultado(int candidatosAvaliados, List<OfertaEncontrada> ofertas, List<Candidato> candidatos) {}
    public record Candidato(String produtoId, boolean ofertaUtilizavel, String motivo) {}
}
