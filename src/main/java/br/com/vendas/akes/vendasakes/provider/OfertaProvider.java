package br.com.vendas.akes.vendasakes.provider;

import br.com.vendas.akes.vendasakes.model.Marketplace;

import java.util.List;

public interface OfertaProvider {

    Marketplace marketplace();

    List<OfertaCapturada> buscarOfertas(String termoDeBusca);
}
