package br.com.akesofertas.afiliados.rejeicao;

import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class OfertaAfiliadoRejeitadaService {
    public static final int URL_NAO_PERMITIDA = 111;
    private static final Duration COOLDOWN = Duration.ofHours(24);
    private static final String MOTIVO_111 = "URL não permitida no programa de afiliados";
    private final OfertaAfiliadoRejeitadaRepository repository;
    private final Clock clock;

    @Autowired
    public OfertaAfiliadoRejeitadaService(OfertaAfiliadoRejeitadaRepository repository) {
        this(repository, Clock.systemUTC());
    }

    OfertaAfiliadoRejeitadaService(OfertaAfiliadoRejeitadaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean emCooldown(String fornecedor, String itemId) {
        Instant agora = clock.instant();
        return repository.findByFornecedorAndItemId(fornecedor, itemId)
                .map(r -> r.getReprocessarApos().isAfter(agora)).orElse(false);
    }

    @Transactional
    public OfertaAfiliadoRejeitada registrarCodigo111(String fornecedor, OfertaAfiliado oferta) {
        Instant agora = clock.instant();
        var entity = repository.findByFornecedorAndItemId(fornecedor, oferta.itemId())
                .orElseGet(() -> new OfertaAfiliadoRejeitadaEntity(fornecedor, oferta.itemId(),
                        oferta.produtoId(), URL_NAO_PERMITIDA, MOTIVO_111, agora, agora.plus(COOLDOWN)));
        entity.atualizar(oferta.produtoId(), URL_NAO_PERMITIDA, MOTIVO_111, agora, agora.plus(COOLDOWN));
        return repository.save(entity).toDomain();
    }
}
