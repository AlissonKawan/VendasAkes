package br.com.akesofertas.cupons.client;

import br.com.akesofertas.cupons.domain.ProdutoElegivelCupom;

import java.util.List;

/** Coleta os anúncios exatos elegíveis para uma campanha. */
public interface CouponEligibleProductsClient {
    List<ProdutoElegivelCupom> buscarProdutos(long couponId, String productsUrl);
}
