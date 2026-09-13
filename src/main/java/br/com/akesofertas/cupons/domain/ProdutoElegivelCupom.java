package br.com.akesofertas.cupons.domain;

import java.math.BigDecimal;

/** Anúncio exato relacionado a uma campanha de cupom. */
public record ProdutoElegivelCupom(
        Long couponId,
        String itemId,
        String productId,
        String title,
        String productUrl,
        String imageUrl,
        BigDecimal precoAtual,
        FonteDescobertaProdutoCupom fonteItemId) {

    public ProdutoElegivelCupom(Long couponId, String itemId, String title, String productUrl,
                                BigDecimal precoAtual) {
        this(couponId, itemId, null, title, productUrl, null, precoAtual,
                FonteDescobertaProdutoCupom.HREF_WID);
    }

    public ProdutoElegivelCupom(Long couponId, String itemId, String title, String productUrl) {
        this(couponId, itemId, null, title, productUrl, null, null,
                FonteDescobertaProdutoCupom.HREF_WID);
    }
}
