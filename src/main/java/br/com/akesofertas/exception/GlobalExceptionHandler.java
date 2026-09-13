package br.com.akesofertas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Erro> validacao(MethodArgumentNotValidException exception) {
        List<String> campos = exception.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage()).toList();
        return ResponseEntity.badRequest().body(new Erro(false, "Payload inválido", campos));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Erro> jsonInvalido() {
        return erro(HttpStatus.BAD_REQUEST, "JSON inválido ou tipo de campo incorreto");
    }

    @ExceptionHandler(OfertaInvalidaException.class)
    public ResponseEntity<Erro> ofertaInvalida(OfertaInvalidaException exception) {
        return erro(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(TelegramException.class)
    public ResponseEntity<Erro> telegram(TelegramException exception) {
        return erro(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    private ResponseEntity<Erro> erro(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new Erro(false, mensagem, List.of()));
    }

    public record Erro(boolean sucesso, String mensagem, List<String> erros) {}
}
