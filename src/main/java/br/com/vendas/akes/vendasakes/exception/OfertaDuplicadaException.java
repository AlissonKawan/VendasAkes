package br.com.vendas.akes.vendasakes.exception;

import br.com.vendas.akes.vendasakes.model.Marketplace;

public class OfertaDuplicadaException extends RuntimeException {

    public OfertaDuplicadaException(Marketplace marketplace, String marketplaceProductId) {
        super("Ja existe uma oferta para " + marketplace + " com o produto " + marketplaceProductId);
    }
}
