package br.com.akesofertas.telegram.ouvinte;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CupomDetectadoRepository extends JpaRepository<CupomDetectadoEntity, Long> {

    Optional<CupomDetectadoEntity> findByCodigo(String codigo);

    List<CupomDetectadoEntity> findByStatusOrderByDataDeteccaoDesc(StatusCupom status);

    /**
     * Busca somente cupons pendentes com validade explícita vigente e link de campanha.
     */
    default List<CupomDetectadoEntity> findPendentesNaoVencidos(LocalDate hoje) {
        return findByStatusOrderByDataDeteccaoDesc(StatusCupom.PENDENTE).stream()
                .filter(c -> c.getValidoAte() != null && !c.getValidoAte().isBefore(hoje))
                .filter(c -> c.getUrlCampanha() != null && !c.getUrlCampanha().isBlank())
                .toList();
    }
}
