package br.com.vendas.akes.vendasakes.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OfertaNaoEncontradaException.class)
    public ResponseEntity<ApiError> handleNaoEncontrada(
            OfertaNaoEncontradaException exception,
            HttpServletRequest request
    ) {
        return resposta(HttpStatus.NOT_FOUND, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(OfertaDuplicadaException.class)
    public ResponseEntity<ApiError> handleDuplicada(
            OfertaDuplicadaException exception,
            HttpServletRequest request
    ) {
        return resposta(HttpStatus.CONFLICT, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(OfertaInvalidaException.class)
    public ResponseEntity<ApiError> handleInvalida(
            OfertaInvalidaException exception,
            HttpServletRequest request
    ) {
        return resposta(HttpStatus.BAD_REQUEST, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidacao(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<CampoInvalido> campos = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(erro -> new CampoInvalido(erro.getField(), erro.getDefaultMessage()))
                .toList();

        return resposta(HttpStatus.BAD_REQUEST, "Dados da requisicao invalidos", request, campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleJsonInvalido(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return resposta(HttpStatus.BAD_REQUEST, "JSON invalido ou valor nao reconhecido", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegridade(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return resposta(HttpStatus.CONFLICT, "A operacao viola uma restricao do banco de dados", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleErroInesperado(
            Exception exception,
            HttpServletRequest request
    ) {
        LOGGER.error("Erro inesperado ao processar {}", request.getRequestURI(), exception);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro interno inesperado", request, List.of());
    }

    private ResponseEntity<ApiError> resposta(
            HttpStatus status,
            String mensagem,
            HttpServletRequest request,
            List<CampoInvalido> campos
    ) {
        ApiError erro = new ApiError(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                request.getRequestURI(),
                campos
        );
        return ResponseEntity.status(status).body(erro);
    }
}
