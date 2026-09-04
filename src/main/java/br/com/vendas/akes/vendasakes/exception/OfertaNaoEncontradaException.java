package br.com.vendas.akes.vendasakes.exception;

public class OfertaNaoEncontradaException extends RuntimeException {

    public OfertaNaoEncontradaException(Long id) {
        super("Oferta nao encontrada com o id " + id);
    }
}
