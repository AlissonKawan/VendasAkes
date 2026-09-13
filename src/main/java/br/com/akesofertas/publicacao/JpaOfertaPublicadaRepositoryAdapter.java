package br.com.akesofertas.publicacao;

import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JpaOfertaPublicadaRepositoryAdapter implements OfertaPublicadaRepository {
    
    private final SpringDataOfertaPublicadaRepository jpaRepository;

    public JpaOfertaPublicadaRepositoryAdapter(SpringDataOfertaPublicadaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<OfertaPublicadaEntity> buscarPorFornecedorEItemId(String fornecedor, String itemId) {
        return jpaRepository.findByFornecedorAndItemId(fornecedor, itemId);
    }
    
    @Override
    public OfertaPublicadaEntity salvar(OfertaPublicadaEntity entity) {
        return jpaRepository.save(entity);
    }
    
    @Override
    public OfertaPublicadaEntity buscarPorId(Long id) {
        return jpaRepository.findById(id).orElse(null);
    }
}

