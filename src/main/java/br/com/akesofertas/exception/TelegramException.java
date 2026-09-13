package br.com.akesofertas.exception;

public class TelegramException extends RuntimeException {
    public TelegramException(String mensagem) {
        super(mensagem);
    }
}
