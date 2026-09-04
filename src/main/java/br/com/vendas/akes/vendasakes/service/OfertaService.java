package br.com.vendas.akes.vendasakes.service;

import br.com.vendas.akes.vendasakes.dto.OfertaRequest;
import br.com.vendas.akes.vendasakes.dto.OfertaResponse;
import br.com.vendas.akes.vendasakes.exception.OfertaDuplicadaException;
import br.com.vendas.akes.vendasakes.exception.OfertaInvalidaException;
import br.com.vendas.akes.vendasakes.exception.OfertaNaoEncontradaException;
import br.com.vendas.akes.vendasakes.mapper.OfertaMapper;
import br.com.vendas.akes.vendasakes.model.Oferta;
import br.com.vendas.akes.vendasakes.repository.OfertaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OfertaService {

    private final OfertaRepository ofertaRepository;
    private final OfertaMapper ofertaMapper;

    public OfertaService(OfertaRepository ofertaRepository, OfertaMapper ofertaMapper) {
        this.ofertaRepository = ofertaRepository;
        this.ofertaMapper = ofertaMapper;
    }

    @Transactional(readOnly = true)
    public List<OfertaResponse> listarTodas() {
        return ofertaRepository.findAll(Sort.by(Sort.Direction.DESC, "dataEncontrada"))
                .stream()
                .map(ofertaMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OfertaResponse buscarPorId(Long id) {
        return ofertaMapper.toResponse(buscarEntidade(id));
    }

    @Transactional
    public OfertaResponse criar(OfertaRequest request) {
        validarPrecos(request);
        validarDuplicidade(request, null);

        BigDecimal desconto = calcularPercentualDesconto(request.precoOriginal(), request.precoAtual());
        Oferta oferta = ofertaMapper.toEntity(request, desconto, LocalDateTime.now());

        return ofertaMapper.toResponse(ofertaRepository.saveAndFlush(oferta));
    }

    @Transactional
    public OfertaResponse atualizar(Long id, OfertaRequest request) {
        validarPrecos(request);
        Oferta oferta = buscarEntidade(id);
        validarDuplicidade(request, id);

        BigDecimal desconto = calcularPercentualDesconto(request.precoOriginal(), request.precoAtual());
        ofertaMapper.updateEntity(oferta, request, desconto);

        return ofertaMapper.toResponse(ofertaRepository.saveAndFlush(oferta));
    }

    @Transactional
    public OfertaResponse publicar(Long id) {
        Oferta oferta = buscarEntidade(id);

        // Na etapa 1 nao ha chamada externa: registramos apenas a publicacao simulada.
        oferta.marcarComoPublicada(LocalDateTime.now());

        return ofertaMapper.toResponse(ofertaRepository.save(oferta));
    }

    @Transactional
    public void excluir(Long id) {
        Oferta oferta = buscarEntidade(id);
        ofertaRepository.delete(oferta);
    }

    private Oferta buscarEntidade(Long id) {
        return ofertaRepository.findById(id)
                .orElseThrow(() -> new OfertaNaoEncontradaException(id));
    }

    private void validarDuplicidade(OfertaRequest request, Long idAtual) {
        ofertaRepository.findByMarketplaceAndMarketplaceProductId(
                        request.marketplace(),
                request.marketplaceProductId().trim()
                )
                .filter(ofertaExistente -> idAtual == null || !ofertaExistente.getId().equals(idAtual))
                .ifPresent(ofertaExistente -> {
                    throw new OfertaDuplicadaException(
                            request.marketplace(),
                            request.marketplaceProductId().trim()
                    );
                });
    }

    private void validarPrecos(OfertaRequest request) {
        if (request.precoAtual().compareTo(request.precoOriginal()) > 0) {
            throw new OfertaInvalidaException("O preco atual nao pode ser maior que o preco original");
        }
    }

    private BigDecimal calcularPercentualDesconto(BigDecimal precoOriginal, BigDecimal precoAtual) {
        if (precoOriginal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return precoOriginal
                .subtract(precoAtual)
                .multiply(BigDecimal.valueOf(100))
                .divide(precoOriginal, 2, RoundingMode.HALF_UP);
    }
}
