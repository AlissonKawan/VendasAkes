package br.com.akesofertas.publicacao;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ofertas_publicadas", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"fornecedor", "item_id"})
})
public class OfertaPublicadaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fornecedor;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Column(name = "produto_id")
    private String produtoId;

    @Column(nullable = false, length = 1000)
    private String titulo;

    @Column(name = "url_original", nullable = false, length = 2000)
    private String urlOriginal;

    @Column(name = "url_afiliado", nullable = false, length = 2000)
    private String urlAfiliado;

    @Column(name = "preco_anterior")
    private BigDecimal precoAnterior;

    @Column(name = "preco_atual", nullable = false)
    private BigDecimal precoAtual;

    private String desconto;
    private String comissao;
    private String destaque;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPublicacao status;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private Instant dataCriacao;

    @Column(name = "data_envio")
    private Instant dataEnvio;

    @Column(name = "mensagem_erro", length = 2000)
    private String mensagemErro;

    protected OfertaPublicadaEntity() {}

    public OfertaPublicadaEntity(String fornecedor, String itemId, String produtoId, String titulo,
                                 String urlOriginal, String urlAfiliado, BigDecimal precoAnterior,
                                 BigDecimal precoAtual, String desconto, String comissao, String destaque) {
        this(fornecedor, itemId, produtoId, titulo, urlOriginal, urlAfiliado, precoAnterior,
                precoAtual, desconto, comissao, destaque, Instant.now());
    }

    OfertaPublicadaEntity(String fornecedor, String itemId, String produtoId, String titulo,
                          String urlOriginal, String urlAfiliado, BigDecimal precoAnterior,
                          BigDecimal precoAtual, String desconto, String comissao, String destaque,
                          Instant agora) {
        this.fornecedor = fornecedor;
        this.itemId = itemId;
        this.produtoId = produtoId;
        this.titulo = titulo;
        this.urlOriginal = urlOriginal;
        this.urlAfiliado = urlAfiliado;
        this.precoAnterior = precoAnterior;
        this.precoAtual = precoAtual;
        this.desconto = desconto;
        this.comissao = comissao;
        this.destaque = destaque;
        this.status = StatusPublicacao.PENDENTE_ENVIO;
        this.dataCriacao = agora;
    }
    
    // Getters
    public Long getId() { return id; }
    public String getFornecedor() { return fornecedor; }
    public String getItemId() { return itemId; }
    public String getProdutoId() { return produtoId; }
    public String getTitulo() { return titulo; }
    public String getUrlOriginal() { return urlOriginal; }
    public String getUrlAfiliado() { return urlAfiliado; }
    public BigDecimal getPrecoAnterior() { return precoAnterior; }
    public BigDecimal getPrecoAtual() { return precoAtual; }
    public String getDesconto() { return desconto; }
    public String getComissao() { return comissao; }
    public String getDestaque() { return destaque; }
    public StatusPublicacao getStatus() { return status; }
    public Instant getDataCriacao() { return dataCriacao; }
    public Instant getDataEnvio() { return dataEnvio; }
    public String getMensagemErro() { return mensagemErro; }

    // Setters transicionais
    public void marcarComoEnviada(Instant agora) {
        this.status = StatusPublicacao.ENVIADA;
        this.dataEnvio = agora;
        this.mensagemErro = null;
    }

    public void prepararRepublicacao(String produtoId, String titulo, String urlOriginal,
                                     String urlAfiliado, BigDecimal precoAnterior,
                                     BigDecimal precoAtual, String desconto, String comissao,
                                     String destaque) {
        this.produtoId = produtoId;
        this.titulo = titulo;
        this.urlOriginal = urlOriginal;
        this.urlAfiliado = urlAfiliado;
        this.precoAnterior = precoAnterior;
        this.precoAtual = precoAtual;
        this.desconto = desconto;
        this.comissao = comissao;
        this.destaque = destaque;
        this.status = StatusPublicacao.PENDENTE_ENVIO;
        this.dataEnvio = null;
        this.mensagemErro = null;
    }
    
    public void marcarErroEnvio(String erro) {
        this.status = StatusPublicacao.ERRO_ENVIO;
        this.mensagemErro = erro;
    }

    public OfertaPublicada toDomain() {
        return new OfertaPublicada(id, fornecedor, itemId, produtoId, titulo, urlOriginal, urlAfiliado,
                precoAnterior, precoAtual, desconto, comissao, destaque, status, dataCriacao, dataEnvio, mensagemErro);
    }
}

