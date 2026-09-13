package br.com.akesofertas.cupons.domain;

/** Indica inconsistência entre a campanha consultada e o texto recebido. */
public final class CondicoesCupomParsingException extends IllegalArgumentException {
    public CondicoesCupomParsingException(String message) {
        super(message);
    }
}
