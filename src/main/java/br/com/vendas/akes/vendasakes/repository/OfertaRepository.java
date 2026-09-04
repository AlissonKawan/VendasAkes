package br.com.vendas.akes.vendasakes.repository;

import br.com.vendas.akes.vendasakes.model.Marketplace;
import br.com.vendas.akes.vendasakes.model.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    boolean existsByMarketplaceAndMarketplaceProductId(
            Marketplace marketplace,
            String marketplaceProductId
    );

    Optional<Oferta> findByMarketplaceAndMarketplaceProductId(
            Marketplace marketplace,
            String marketplaceProductId
    );
}
