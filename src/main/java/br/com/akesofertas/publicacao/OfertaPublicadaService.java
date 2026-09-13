package br.com.akesofertas.publicacao;

import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Service
public class OfertaPublicadaService {
    private static final Logger log = LoggerFactory.getLogger(OfertaPublicadaService.class);
    private final OfertaPublicadaRepository repository;
    private final Duration janelaRepublicacao;
    private final Clock clock;

    @Autowired
    public OfertaPublicadaService(OfertaPublicadaRepository repository,
                                  @Value("${akes.ofertas.republicacao-horas:24}") long republicacaoHoras) {
        this(repository, Duration.ofHours(validarHoras(republicacaoHoras)), Clock.systemUTC());
    }

    OfertaPublicadaService(OfertaPublicadaRepository repository, Duration janelaRepublicacao, Clock clock) {
        this.repository = repository;
        if (janelaRepublicacao == null || janelaRepublicacao.isNegative() || janelaRepublicacao.isZero()) {
            throw new IllegalArgumentException("Janela de republicação deve ser positiva");
        }
        this.janelaRepublicacao = janelaRepublicacao;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<OfertaPublicada> buscarPorFornecedorEItemId(String fornecedor, String itemId) {
        validarChave(fornecedor, itemId);
        return repository.buscarPorFornecedorEItemId(fornecedor, itemId).map(OfertaPublicadaEntity::toDomain);
    }

    @Transactional(readOnly = true)
    public boolean bloqueadaParaPublicacao(String fornecedor, String itemId) {
        return buscarPorFornecedorEItemId(fornecedor, itemId).map(this::bloqueia).orElse(false);
    }

    private boolean bloqueia(OfertaPublicada oferta) {
        if (oferta.status() == StatusPublicacao.ERRO_ENVIO) return false;
        if (oferta.status() == StatusPublicacao.PENDENTE_ENVIO) return true;
        if (oferta.status() != StatusPublicacao.ENVIADA) return true;
        if (oferta.dataEnvio() == null) {
            log.warn("Oferta ENVIADA sem dataEnvio; publicação bloqueada para investigação. fornecedor={} itemId={}",
                    oferta.fornecedor(), oferta.itemId());
            return true;
        }
        return oferta.dataEnvio().isAfter(clock.instant().minus(janelaRepublicacao));
    }

    private static void validarChave(String fornecedor, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId nulo ou inválido");
        }
        if (fornecedor == null || fornecedor.isBlank()) {
            throw new IllegalArgumentException("fornecedor inválido");
        }
    }

    @Transactional
    public OfertaPublicada registrarPendente(String fornecedor, OfertaAfiliado oferta, String linkAfiliado) {
        if (oferta == null || oferta.itemId() == null || oferta.itemId().isBlank()) {
            throw new IllegalArgumentException("oferta ou itemId inválido");
        }
        if (linkAfiliado == null || linkAfiliado.isBlank()) {
            return null;
        }
        validarChave(fornecedor, oferta.itemId());
        Optional<OfertaPublicadaEntity> existente = repository.buscarPorFornecedorEItemId(fornecedor, oferta.itemId());
        if (existente.map(OfertaPublicadaEntity::toDomain).map(this::bloqueia).orElse(false)) return null;

        OfertaPublicadaEntity entity;
        if (existente.isPresent()) {
            entity = existente.get();
            entity.prepararRepublicacao(oferta.produtoId(), oferta.titulo(), oferta.url(), linkAfiliado,
                    oferta.precoAnterior(), oferta.precoAtual(), oferta.desconto(), oferta.comissao(),
                    oferta.destaque());
        } else {
            entity = new OfertaPublicadaEntity(fornecedor, oferta.itemId(), oferta.produtoId(), oferta.titulo(),
                    oferta.url(), linkAfiliado, oferta.precoAnterior(), oferta.precoAtual(), oferta.desconto(),
                    oferta.comissao(), oferta.destaque(), clock.instant());
        }
        
        return repository.salvar(entity).toDomain();
    }

    @Transactional
    public OfertaPublicada marcarComoEnviada(Long id) {
        OfertaPublicadaEntity entity = repository.buscarPorId(id);
        if (entity != null) {
            entity.marcarComoEnviada(clock.instant());
            return repository.salvar(entity).toDomain();
        }
        return null;
    }

    @Transactional
    public OfertaPublicada marcarErroEnvio(Long id, String erro) {
        OfertaPublicadaEntity entity = repository.buscarPorId(id);
        if (entity != null) {
            // Filtrar erro se tiver secrets ou trace longo (simplificado para exemplo)
            if (erro != null && erro.length() > 2000) {
                erro = erro.substring(0, 2000);
            }
            entity.marcarErroEnvio(erro);
            return repository.salvar(entity).toDomain();
        }
        return null;
    }

    private static long validarHoras(long horas) {
        if (horas <= 0) throw new IllegalArgumentException("akes.ofertas.republicacao-horas deve ser positivo");
        return horas;
    }
}

