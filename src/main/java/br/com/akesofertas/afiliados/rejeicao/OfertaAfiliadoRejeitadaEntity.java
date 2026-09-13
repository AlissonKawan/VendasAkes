package br.com.akesofertas.afiliados.rejeicao;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ofertas_afiliado_rejeitadas", uniqueConstraints =
        @UniqueConstraint(name = "uk_ofertas_afiliado_rejeitadas_forn_item", columnNames = {"fornecedor", "item_id"}))
public class OfertaAfiliadoRejeitadaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String fornecedor;
    @Column(name = "item_id", nullable = false) private String itemId;
    @Column(name = "produto_id") private String produtoId;
    @Column(name = "error_code", nullable = false) private Integer errorCode;
    @Column(nullable = false, length = 255) private String motivo;
    @Column(name = "data_rejeicao", nullable = false) private Instant dataRejeicao;
    @Column(name = "reprocessar_apos", nullable = false) private Instant reprocessarApos;

    protected OfertaAfiliadoRejeitadaEntity() {}

    OfertaAfiliadoRejeitadaEntity(String fornecedor, String itemId, String produtoId, Integer errorCode,
                                  String motivo, Instant dataRejeicao, Instant reprocessarApos) {
        this.fornecedor = fornecedor;
        this.itemId = itemId;
        atualizar(produtoId, errorCode, motivo, dataRejeicao, reprocessarApos);
    }

    void atualizar(String produtoId, Integer errorCode, String motivo, Instant dataRejeicao, Instant reprocessarApos) {
        this.produtoId = produtoId;
        this.errorCode = errorCode;
        this.motivo = motivo;
        this.dataRejeicao = dataRejeicao;
        this.reprocessarApos = reprocessarApos;
    }

    public Instant getReprocessarApos() { return reprocessarApos; }
    public OfertaAfiliadoRejeitada toDomain() {
        return new OfertaAfiliadoRejeitada(id, fornecedor, itemId, produtoId, errorCode, motivo,
                dataRejeicao, reprocessarApos);
    }
}
