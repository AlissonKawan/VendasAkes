package br.com.vendas.akes.vendasakes.controller;

import br.com.vendas.akes.vendasakes.dto.OfertaRequest;
import br.com.vendas.akes.vendasakes.dto.OfertaResponse;
import br.com.vendas.akes.vendasakes.service.OfertaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/ofertas")
public class OfertaController {

    private final OfertaService ofertaService;

    public OfertaController(OfertaService ofertaService) {
        this.ofertaService = ofertaService;
    }

    @GetMapping
    public ResponseEntity<List<OfertaResponse>> listarTodas() {
        return ResponseEntity.ok(ofertaService.listarTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfertaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ofertaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<OfertaResponse> criar(@Valid @RequestBody OfertaRequest request) {
        OfertaResponse ofertaCriada = ofertaService.criar(request);
        URI localizacao = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(ofertaCriada.id())
                .toUri();

        return ResponseEntity.created(localizacao).body(ofertaCriada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OfertaResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody OfertaRequest request
    ) {
        return ResponseEntity.ok(ofertaService.atualizar(id, request));
    }

    @PostMapping("/{id}/publicar")
    public ResponseEntity<OfertaResponse> publicar(@PathVariable Long id) {
        return ResponseEntity.ok(ofertaService.publicar(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        ofertaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
