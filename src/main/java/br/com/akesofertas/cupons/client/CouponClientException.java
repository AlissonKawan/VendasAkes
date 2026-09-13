package br.com.akesofertas.cupons.client;

/** Falha de infraestrutura sem carregar corpo, cookies ou dados da sessão. */
public class CouponClientException extends RuntimeException {
    private final TipoErroInfraestruturaCupom tipo;
    private final Integer httpStatus;

    public CouponClientException(TipoErroInfraestruturaCupom tipo, Integer httpStatus, String message) {
        super(message);
        this.tipo = tipo;
        this.httpStatus = httpStatus;
    }

    public CouponClientException(TipoErroInfraestruturaCupom tipo, String message) {
        this(tipo, null, message);
    }

    public TipoErroInfraestruturaCupom tipo() {
        return tipo;
    }

    public Integer httpStatus() {
        return httpStatus;
    }
}
