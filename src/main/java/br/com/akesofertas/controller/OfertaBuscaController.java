package br.com.akesofertas.controller;

import br.com.akesofertas.service.OfertaBuscaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/ofertas/buscar")
public class OfertaBuscaController {
    private final OfertaBuscaService service;
    public OfertaBuscaController(OfertaBuscaService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<OfertaBuscaService.Resultado> buscar(
            @RequestParam(defaultValue = "mercadolivre") String fornecedor,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int limite) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.buscar(fornecedor, q, limite));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> erro(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("sucesso", false, "mensagem", exception.getReason()));
    }
}
