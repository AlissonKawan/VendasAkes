package br.com.akesofertas.controller;

import br.com.akesofertas.provider.ProdutoEncontrado;
import br.com.akesofertas.service.ProdutoBuscaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {
    private final ProdutoBuscaService service;
    public ProdutoController(ProdutoBuscaService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<ProdutoEncontrado>> buscar(
            @RequestParam(defaultValue = "mercadolivre") String fornecedor,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "5") int limite) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.buscar(fornecedor, q, limite));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> erro(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("sucesso", false, "mensagem", exception.getReason()));
    }
}
