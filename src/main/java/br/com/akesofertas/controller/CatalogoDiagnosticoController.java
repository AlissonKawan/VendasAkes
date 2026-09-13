package br.com.akesofertas.controller;

import br.com.akesofertas.service.CatalogoDiagnosticoService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/produtos")
public class CatalogoDiagnosticoController {
    private final CatalogoDiagnosticoService service;
    public CatalogoDiagnosticoController(CatalogoDiagnosticoService service) { this.service = service; }

    @GetMapping("/{productId}/diagnostico-ofertas")
    public ResponseEntity<CatalogoDiagnosticoService.Diagnostico> consultar(
            @PathVariable String productId, @RequestParam(defaultValue = "3") int limite) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultar(productId, limite));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> erro(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("sucesso", false, "mensagem", exception.getReason()));
    }
}
