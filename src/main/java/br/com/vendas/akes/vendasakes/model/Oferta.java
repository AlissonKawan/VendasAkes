package br.com.vendas.akes.vendasakes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "ofertas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_oferta_marketplace_produto",
                columnNames = {"marketplace", "marketplace_product_id"}
        )
)
public class Oferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Marketplace marketplace;

    @Column(name = "marketplace_product_id", nullable = false, length = 120)
    private String marketplaceProductId;

    @Column(nullable = false, length = 300)
    private String titulo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal precoOriginal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal precoAtual;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualDesconto;

    @Column(nullable = false, length = 2048)
    private String urlProduto;

    @Column(length = 2048)
    private String urlAfiliado;

    @Column(length = 2048)
    private String imagemUrl;

    @Column(nullable = false)
    private LocalDateTime dataEncontrada;

    private LocalDateTime dataPublicacao;

    @Column(nullable = false)
    private boolean publicado;

    public Oferta(
            Marketplace marketplace,
            String marketplaceProductId,
            String titulo,
            BigDecimal precoOriginal,
            BigDecimal precoAtual,
            BigDecimal percentualDesconto,
            String urlProduto,
            String urlAfiliado,
            String imagemUrl,
            LocalDateTime dataEncontrada
    ) {
        atualizarDados(
                marketplace,
                marketplaceProductId,
                titulo,
                precoOriginal,
                precoAtual,
                percentualDesconto,
                urlProduto,
                urlAfiliado,
                imagemUrl
        );
        this.dataEncontrada = dataEncontrada;
        this.publicado = false;
    }

    public void atualizarDados(
            Marketplace marketplace,
            String marketplaceProductId,
            String titulo,
            BigDecimal precoOriginal,
            BigDecimal precoAtual,
            BigDecimal percentualDesconto,
            String urlProduto,
            String urlAfiliado,
            String imagemUrl
    ) {
        this.marketplace = marketplace;
        this.marketplaceProductId = marketplaceProductId;
        this.titulo = titulo;
        this.precoOriginal = precoOriginal;
        this.precoAtual = precoAtual;
        this.percentualDesconto = percentualDesconto;
        this.urlProduto = urlProduto;
        this.urlAfiliado = urlAfiliado;
        this.imagemUrl = imagemUrl;
    }

    public void marcarComoPublicada(LocalDateTime momentoDaPublicacao) {
        if (!publicado) {
            this.publicado = true;
            this.dataPublicacao = momentoDaPublicacao;
        }
    }
}
