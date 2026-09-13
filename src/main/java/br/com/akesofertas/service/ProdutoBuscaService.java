package br.com.akesofertas.service;

import br.com.akesofertas.provider.ProdutoEncontrado;
import br.com.akesofertas.provider.ProdutoProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProdutoBuscaService {
    private final List<ProdutoProvider> providers;

    // O Spring descobre novos providers: não será necessário adicionar ifs por marketplace.
    public ProdutoBuscaService(List<ProdutoProvider> providers) { this.providers = List.copyOf(providers); }

    public List<ProdutoEncontrado> buscar(String fornecedor, String termo, int limite) {
        if (termo == null || termo.isBlank() || termo.trim().length() > 120 || limite < 1 || limite > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe q com 1 a 120 caracteres e limite entre 1 e 20.");
        }
        ProdutoProvider provider = providers.stream().filter(p -> p.codigo().equals(fornecedor)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fornecedor não disponível."));
        return provider.buscar(termo.trim(), limite);
    }
}
