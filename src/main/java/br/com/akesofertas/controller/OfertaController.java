package br.com.akesofertas.controller;

import br.com.akesofertas.dto.OfertaRequest;
import br.com.akesofertas.dto.OfertaResponse;
import br.com.akesofertas.service.OfertaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/ofertas")
public class OfertaController {
    private final OfertaService service;

    public OfertaController(OfertaService service) {
        this.service = service;
    }

    @PostMapping("/publicar")
    public OfertaResponse publicar(@Valid @RequestBody OfertaRequest oferta) {
        return service.publicar(oferta);
    }
}
