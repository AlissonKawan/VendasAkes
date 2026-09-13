package br.com.akesofertas.provider;

import java.util.List;

/** Cada marketplace traduz a sua API para o mesmo modelo, sem expor credenciais. */
public interface ProdutoProvider {
    String codigo();
    List<ProdutoEncontrado> buscar(String termo, int limite);
}
