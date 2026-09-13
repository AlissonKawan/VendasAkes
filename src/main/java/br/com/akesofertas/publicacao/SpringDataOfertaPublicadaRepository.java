package br.com.akesofertas.publicacao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpringDataOfertaPublicadaRepository extends JpaRepository<OfertaPublicadaEntity, Long> {
    Optional<OfertaPublicadaEntity> findByFornecedorAndItemId(String fornecedor, String itemId);
}

