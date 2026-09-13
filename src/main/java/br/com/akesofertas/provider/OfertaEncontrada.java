package br.com.akesofertas.provider;

import java.math.BigDecimal;
import java.util.List;

/** Uma oferta confirmada nas consultas oficiais; valores desconhecidos permanecem null. */
public record OfertaEncontrada(String fornecedor, String produtoId, String anuncioId,
                               String titulo, String urlProduto, String imagemUrl, Long vendedorId,
                               BigDecimal precoAtual, BigDecimal precoRegular, BigDecimal precoOriginalCatalogo,
                               String moeda, boolean precoPromocional, BigDecimal percentualDesconto,
                               String precoId, Entrega entrega, Long quantidadeDisponivel) {
    // Quantidade pública pode ser referencial; não representa uma contagem exata de estoque.
    public record Entrega(Boolean freteGratis, Boolean retiradaNaLoja, String modalidade,
                          String tipoLogistica, List<String> tags) {}
}
