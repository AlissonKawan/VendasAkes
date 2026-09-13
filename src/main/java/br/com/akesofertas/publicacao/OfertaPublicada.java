package br.com.akesofertas.publicacao;

import java.math.BigDecimal;
import java.time.Instant;
import br.com.akesofertas.cupons.domain.CupomAplicavel;

public record OfertaPublicada(
    Long id,
    String fornecedor,
    String itemId,
    String produtoId,
    String titulo,
    String urlOriginal,
    String urlAfiliado,
    BigDecimal precoAnterior,
    BigDecimal precoAtual,
    String desconto,
    String comissao,
    String destaque,
    StatusPublicacao status,
    Instant dataCriacao,
    Instant dataEnvio,
    String mensagemErro,
    CupomAplicavel cupom
) {
    public OfertaPublicada(Long id, String fornecedor, String itemId, String produtoId, String titulo,
                          String urlOriginal, String urlAfiliado, BigDecimal precoAnterior, BigDecimal precoAtual,
                          String desconto, String comissao, String destaque, StatusPublicacao status,
                          Instant dataCriacao, Instant dataEnvio, String mensagemErro) {
        this(id, fornecedor, itemId, produtoId, titulo, urlOriginal, urlAfiliado, precoAnterior, precoAtual,
                desconto, comissao, destaque, status, dataCriacao, dataEnvio, mensagemErro, null);
    }

    /** Cópia de transporte para Telegram; não altera a entidade persistida. */
    public OfertaPublicada comCupom(CupomAplicavel cupom) {
        if (cupom == null) return this;
        return new OfertaPublicada(id, fornecedor, itemId, produtoId, titulo, urlOriginal, urlAfiliado,
                precoAnterior, precoAtual, desconto, comissao, destaque, status, dataCriacao, dataEnvio,
                mensagemErro, cupom);
    }
}

