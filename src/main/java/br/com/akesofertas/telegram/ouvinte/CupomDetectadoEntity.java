package br.com.akesofertas.telegram.ouvinte;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "cupons_detectados", indexes = {
        @Index(name = "idx_cupons_detectados_status", columnList = "status")
})
public class CupomDetectadoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String desconto;

    @Column(name = "compra_minima", length = 100)
    private String compraMinima;

    @Column(name = "desconto_maximo", length = 100)
    private String descontoMaximo;

    @Column(name = "valido_ate")
    private LocalDate validoAte;

    private String categoria;

    @Column(name = "url_campanha", nullable = false, length = 2000)
    private String urlCampanha;

    @Column(name = "url_desencurtada", length = 2000)
    private String urlDesencurtada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatusCupom status;

    @Column(name = "data_deteccao", nullable = false, updatable = false)
    private Instant dataDeteccao;

    @Column(name = "data_processamento")
    private Instant dataProcessamento;

    @Column(name = "mensagem_erro", length = 2000)
    private String mensagemErro;

    protected CupomDetectadoEntity() {}

    public CupomDetectadoEntity(String codigo, String desconto, String compraMinima, String descontoMaximo,
                                LocalDate validoAte, String categoria, String urlCampanha, String urlDesencurtada) {
        this.codigo = codigo;
        this.desconto = desconto;
        this.compraMinima = compraMinima;
        this.descontoMaximo = descontoMaximo;
        this.validoAte = validoAte;
        this.categoria = categoria;
        this.urlCampanha = urlCampanha;
        this.urlDesencurtada = urlDesencurtada;
        this.status = StatusCupom.PENDENTE;
        this.dataDeteccao = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDesconto() {
        return desconto;
    }

    public String getCompraMinima() {
        return compraMinima;
    }

    public String getDescontoMaximo() {
        return descontoMaximo;
    }

    public LocalDate getValidoAte() {
        return validoAte;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getUrlCampanha() {
        return urlCampanha;
    }

    public String getUrlDesencurtada() {
        return urlDesencurtada;
    }

    public void setUrlDesencurtada(String urlDesencurtada) {
        this.urlDesencurtada = urlDesencurtada;
    }

    public void atualizar(CupomDetectado cupom, String novaUrlDesencurtada) {
        this.desconto = cupom.desconto();
        this.compraMinima = cupom.compraMinima();
        this.descontoMaximo = cupom.descontoMaximo();
        this.validoAte = cupom.validoAte();
        this.categoria = cupom.categoria();
        this.urlCampanha = cupom.urlCampanha();
        this.urlDesencurtada = novaUrlDesencurtada;
        this.status = StatusCupom.PENDENTE;
        this.dataProcessamento = null;
        this.mensagemErro = null;
    }

    public StatusCupom getStatus() {
        return status;
    }

    public void setStatus(StatusCupom status) {
        this.status = status;
    }

    public Instant getDataDeteccao() {
        return dataDeteccao;
    }

    public Instant getDataProcessamento() {
        return dataProcessamento;
    }

    public void marcarProcessado() {
        this.status = StatusCupom.PROCESSADO;
        this.dataProcessamento = Instant.now();
    }

    public void marcarErro(String erro) {
        this.status = StatusCupom.ERRO;
        this.mensagemErro = erro != null && erro.length() > 2000 ? erro.substring(0, 1999) : erro;
    }

    public String getMensagemErro() {
        return mensagemErro;
    }

    public CupomDetectado toRecord() {
        return new CupomDetectado(codigo, desconto, compraMinima, descontoMaximo, validoAte, categoria,
                urlDesencurtada != null && !urlDesencurtada.isBlank() ? urlDesencurtada : urlCampanha);
    }
}
