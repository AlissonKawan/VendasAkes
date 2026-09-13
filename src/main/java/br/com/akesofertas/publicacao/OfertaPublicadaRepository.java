package br.com.akesofertas.publicacao;

import java.util.Optional;

public interface OfertaPublicadaRepository {
    Optional<OfertaPublicadaEntity> buscarPorFornecedorEItemId(String fornecedor, String itemId);
    OfertaPublicadaEntity salvar(OfertaPublicadaEntity entity);
    OfertaPublicadaEntity buscarPorId(Long id);
}

