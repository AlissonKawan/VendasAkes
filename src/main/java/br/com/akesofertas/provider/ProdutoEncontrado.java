package br.com.akesofertas.provider;

/** Produto de catálogo. A presença aqui ainda não significa que seja uma oferta. */
public record ProdutoEncontrado(String fornecedor, String id, String titulo,
                                String urlProduto, String imagemUrl) {}
