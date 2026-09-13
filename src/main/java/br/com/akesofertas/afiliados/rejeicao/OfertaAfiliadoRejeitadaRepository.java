package br.com.akesofertas.afiliados.rejeicao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OfertaAfiliadoRejeitadaRepository extends JpaRepository<OfertaAfiliadoRejeitadaEntity, Long> {
    Optional<OfertaAfiliadoRejeitadaEntity> findByFornecedorAndItemId(String fornecedor, String itemId);
}
