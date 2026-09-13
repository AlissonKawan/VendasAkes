package br.com.akesofertas.afiliados.ofertas;

import java.math.BigDecimal;
import br.com.akesofertas.cupons.domain.CupomAplicavel;

/** Valores exibidos pelo Hub; desconto e comissão são textos, não percentuais inferidos. */
public record OfertaAfiliado(String itemId, String produtoId, String titulo, BigDecimal precoAnterior,
                            BigDecimal precoAtual, String desconto, String comissao, String destaque, String url,
                            String imagemUrl, CupomAplicavel cupom, OrigemOferta origem) {
    public OfertaAfiliado {
        origem = origem != null ? origem : OrigemOferta.HUB_AFILIADOS;
    }

    public OfertaAfiliado(String itemId, String produtoId, String titulo, BigDecimal precoAnterior,
                          BigDecimal precoAtual, String desconto, String comissao, String destaque, String url,
                          String imagemUrl, CupomAplicavel cupom) {
        this(itemId, produtoId, titulo, precoAnterior, precoAtual, desconto, comissao, destaque, url,
                imagemUrl, cupom, OrigemOferta.HUB_AFILIADOS);
    }

    public OfertaAfiliado(String itemId, String produtoId, String titulo, BigDecimal precoAnterior,
                          BigDecimal precoAtual, String desconto, String comissao, String destaque, String url,
                          String imagemUrl) {
        this(itemId, produtoId, titulo, precoAnterior, precoAtual, desconto, comissao, destaque, url, imagemUrl,
                null, OrigemOferta.HUB_AFILIADOS);
    }

    public OfertaAfiliado comCupom(CupomAplicavel cupom) {
        return new OfertaAfiliado(itemId, produtoId, titulo, precoAnterior, precoAtual, desconto, comissao,
                destaque, url, imagemUrl, cupom, origem);
    }

    public OfertaAfiliado comOrigem(OrigemOferta novaOrigem) {
        return new OfertaAfiliado(itemId, produtoId, titulo, precoAnterior, precoAtual, desconto, comissao,
                destaque, url, imagemUrl, cupom, novaOrigem);
    }

    public OfertaAfiliado(String itemId, String produtoId, String titulo, BigDecimal precoAnterior,
                          BigDecimal precoAtual, String desconto, String comissao, String destaque, String url) {
        this(itemId, produtoId, titulo, precoAnterior, precoAtual, desconto, comissao, destaque, url, null,
                null, OrigemOferta.HUB_AFILIADOS);
    }

    public boolean temCupom() {
        return cupom != null;
    }
}
