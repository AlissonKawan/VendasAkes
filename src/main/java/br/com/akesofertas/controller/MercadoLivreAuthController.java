package br.com.akesofertas.controller;

import br.com.akesofertas.service.MercadoLivreAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/mercadolivre")
public class MercadoLivreAuthController {
    private final MercadoLivreAuthService service;

    public MercadoLivreAuthController(MercadoLivreAuthService service) {
        this.service = service;
    }

    @GetMapping("/auth")
    public ResponseEntity<Void> autorizar(HttpServletRequest request) {
        // Cria uma sessão. O navegador guarda só o cookie JSESSIONID, nunca os tokens.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(service.iniciar(request.getSession()))
                .cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(HttpServletRequest request,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        service.concluir(request.getSession(false), code, state, error);
        // Troca o ID da sessão após autenticar e sai da URL que contém o code.
        request.changeSessionId();
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, request.getContextPath() + "/mercadolivre/me")
                .header("Referrer-Policy", "no-referrer")
                .cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<?, ?>> usuario(HttpServletRequest request) {
        // Usa os tokens da mesma sessão e retorna os dados da conta, sem as credenciais.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.consultarUsuario(request.getSession(false)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> erro(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer")
                .body(Map.of("sucesso", false, "mensagem", exception.getReason()));
    }
}
