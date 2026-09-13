package br.com.akesofertas.telegram.ouvinte;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/**
 * Gerencia a persistência dos cupons capturados do canal oficial do Telegram.
 * Desacopla o fluxo de captura (Fluxo 1) do fluxo de navegação e postagem (Fluxo 2).
 */
@Service
public class CupomStorageService {
    private static final Logger log = LoggerFactory.getLogger(CupomStorageService.class);

    private final CupomDetectadoRepository repository;

    @Autowired
    public CupomStorageService(CupomDetectadoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CupomDetectadoEntity salvarCupom(CupomDetectado cupom, String urlDesencurtada) {
        if (cupom == null || cupom.codigo() == null || cupom.codigo().isBlank()
                || cupom.validoAte() == null
                || cupom.urlCampanha() == null || cupom.urlCampanha().isBlank()) {
            log.debug("[STORAGE] Cupom ignorado por falta de código, validade explícita ou link de campanha.");
            return null;
        }

        Optional<CupomDetectadoEntity> existente = repository.findByCodigo(cupom.codigo());
        if (existente.isPresent()) {
            CupomDetectadoEntity entity = existente.get();
            boolean mesmaCampanha = Objects.equals(entity.getValidoAte(), cupom.validoAte())
                    && Objects.equals(entity.getUrlCampanha(), cupom.urlCampanha())
                    && Objects.equals(entity.getDesconto(), cupom.desconto());
            if (entity.getStatus() == StatusCupom.PROCESSADO && mesmaCampanha) {
                log.debug("[STORAGE] Cupom {} já foi processado anteriormente.", cupom.codigo());
                return entity;
            }
            entity.atualizar(cupom, urlDesencurtada);
            log.info("[STORAGE] Cupom {} atualizado e colocado como PENDENTE.", cupom.codigo());
            return repository.save(entity);
        }

        CupomDetectadoEntity nova = new CupomDetectadoEntity(
                cupom.codigo(),
                cupom.desconto(),
                cupom.compraMinima(),
                cupom.descontoMaximo(),
                cupom.validoAte(),
                cupom.categoria(),
                cupom.urlCampanha(),
                urlDesencurtada
        );

        CupomDetectadoEntity salva = repository.save(nova);
        log.info("💾 [STORAGE] Novo cupom salvo no banco: {} ({} | Válido até: {})",
                salva.getCodigo(), salva.getDesconto(), salva.getValidoAte());
        return salva;
    }

    @Transactional(readOnly = true)
    public List<CupomDetectadoEntity> buscarPendentesValidos() {
        return repository.findPendentesNaoVencidos(LocalDate.now());
    }

    @Transactional
    public void marcarComoProcessado(Long id) {
        repository.findById(id).ifPresent(entity -> {
            entity.marcarProcessado();
            repository.save(entity);
            log.info("✅ [STORAGE] Cupom {} marcado como PROCESSADO.", entity.getCodigo());
        });
    }

    @Transactional
    public void marcarComoErro(Long id, String erro) {
        repository.findById(id).ifPresent(entity -> {
            entity.marcarErro(erro);
            repository.save(entity);
            log.warn("⚠️ [STORAGE] Cupom {} marcado com ERRO: {}", entity.getCodigo(), erro);
        });
    }
}
