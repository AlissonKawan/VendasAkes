package br.com.akesofertas.telegram.ouvinte;

import br.com.akesofertas.afiliados.ResultadoLinkAfiliado;
import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.afiliados.ofertas.OrigemOferta;
import br.com.akesofertas.cupons.domain.CondicoesCupom;
import br.com.akesofertas.cupons.domain.CupomAplicavel;
import br.com.akesofertas.cupons.domain.ProdutoElegivelCupom;
import br.com.akesofertas.cupons.evidence.ValidationConfidence;
import br.com.akesofertas.publicacao.OfertaPublicadaService;
import br.com.akesofertas.scheduler.MercadoLivreAfiliadosSessionFactory;
import br.com.akesofertas.telegram.PublicadorOfertaService;
import br.com.akesofertas.telegram.ResultadoPublicacaoOferta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pipeline desacoplado em dois fluxos:
 * Fluxo 1: Recebe cupons do ouvinte do Telegram, des-encurta URLs e salva no banco de dados com status PENDENTE.
 * Fluxo 2: Recupera os cupons pendentes válidos, navega no link da campanha com Playwright, coleta os produtos elegíveis,
 *          gera os links de afiliados, publica no canal e marca o cupom como PROCESSADO.
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
public class CampanhaProcessingService {
    private static final Logger log = LoggerFactory.getLogger(CampanhaProcessingService.class);
    private static final String FORNECEDOR = "MERCADO_LIVRE";
    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+)%");
    private static final Pattern VALOR_PATTERN = Pattern.compile("R\\$\\s*([\\d.,]+)");

    private final MercadoLivreAfiliadosSessionFactory sessoes;
    private final PublicadorOfertaService publicador;
    private final OfertaPublicadaService publicadas;
    private final CupomStorageService storage;
    private final int maxOfertasPorCiclo;
    private final AtomicBoolean processandoFluxo2 = new AtomicBoolean(false);

    @Autowired
    public CampanhaProcessingService(
            @Autowired(required = false) MercadoLivreAfiliadosSessionFactory sessoes,
            PublicadorOfertaService publicador,
            OfertaPublicadaService publicadas,
            CupomStorageService storage,
            @Value("${akes.campanhas.max-ofertas-por-ciclo:${akes.campanhas.max-produtos:3}}") int maxOfertasPorCiclo) {
        this.sessoes = sessoes;
        this.publicador = publicador;
        this.publicadas = publicadas;
        this.storage = storage;
        this.maxOfertasPorCiclo = Math.max(1, maxOfertasPorCiclo);
    }

    // =========================================================================
    // FLUXO 1: SALVAR CUPONS DETECTADOS
    // =========================================================================

    /**
     * Salva todos os cupons detectados no banco de dados com status PENDENTE.
     */
    public List<CupomDetectadoEntity> salvarCuponsDetectados(List<CupomDetectado> cupons) {
        if (cupons == null || cupons.isEmpty()) return List.of();

        List<CupomDetectadoEntity> salvos = new ArrayList<>();
        for (CupomDetectado cupom : cupons) {
            try {
                String urlReal = desencurtarUrl(cupom.urlCampanha());
                CupomDetectadoEntity salvo = storage.salvarCupom(cupom, urlReal);
                if (salvo != null) salvos.add(salvo);
            } catch (Exception e) {
                log.error("[FLUXO 1] Erro ao salvar cupom {}: {}", cupom.codigo(), e.getMessage());
            }
        }
        return salvos;
    }

    /**
     * Recebe e salva os cupons e inicia o processamento dos pendentes.
     */
    public int processarCupons(List<CupomDetectado> cupons) {
        salvarCuponsDetectados(cupons);
        return processarCuponsPendentes();
    }

    // =========================================================================
    // FLUXO 2: PROCESSAR PRODUTOS DOS CUPONS PENDENTES COM PLAYWRIGHT
    // =========================================================================

    /**
     * Varre todos os cupons pendentes no banco, entra no link de cada um com o navegador Playwright,
     * coleta os produtos, gera links de afiliados e publica no Telegram.
     */
    public int processarCuponsPendentes() {
        if (sessoes == null) {
            log.warn("[FLUXO 2] Fábrica de sessões do Mercado Livre não ativa (akes.scheduler.enabled=true). " +
                     "Os cupons permanecem salvos como PENDENTES no banco de dados.");
            return 0;
        }

        if (!processandoFluxo2.compareAndSet(false, true)) {
            log.info("[FLUXO 2] Já existe um processamento de cupons pendentes em andamento.");
            return 0;
        }

        try {
            List<CupomDetectadoEntity> pendentes = storage.buscarPendentesValidos();
            if (pendentes.isEmpty()) {
                log.info("[FLUXO 2] Nenhum cupom pendente para processamento.");
                return 0;
            }

            log.info("🎯 [FLUXO 2] Iniciando coleta de produtos para {} cupom(ns) pendente(s), "
                    + "com limite global de {} oferta(s) neste ciclo...", pendentes.size(), maxOfertasPorCiclo);
            int totalPublicados = 0;

            try (var sessao = sessoes.abrir()) {
                for (CupomDetectadoEntity entity : pendentes) {
                    if (totalPublicados >= maxOfertasPorCiclo) {
                        log.info("[FLUXO 2] Limite global de {} oferta(s) atingido. "
                                + "Os demais cupons ficam pendentes para o próximo ciclo.", maxOfertasPorCiclo);
                        break;
                    }
                    try {
                        int vagasRestantes = maxOfertasPorCiclo - totalPublicados;
                        int enviados = processarCupomComSessao(sessao, entity, vagasRestantes);
                        totalPublicados += enviados;
                        if (enviados > 0) {
                            storage.marcarComoProcessado(entity.getId());
                        } else {
                            log.warn("[FLUXO 2] Cupom {} permaneceu PENDENTE porque nenhuma oferta foi publicada.",
                                    entity.getCodigo());
                        }
                    } catch (Exception e) {
                        log.error("[FLUXO 2] Erro ao processar produtos do cupom {}: {}", entity.getCodigo(), e.getMessage());
                        storage.marcarComoErro(entity.getId(), e.getMessage());
                    }
                }
            }

            log.info("🏁 [FLUXO 2] Processamento concluído! Total de ofertas enviadas: {}", totalPublicados);
            return totalPublicados;
        } finally {
            processandoFluxo2.set(false);
        }
    }

    /** Reprocessa pendências do fluxo de cupons sem misturá-las à coleta normal do Hub. */
    @Scheduled(fixedDelayString = "${akes.campanhas.interval-ms:300000}",
            initialDelayString = "${akes.campanhas.initial-delay-ms:60000}")
    public void reprocessarPendentesAgendado() {
        processarCuponsPendentes();
    }

    private int processarCupomComSessao(MercadoLivreAfiliadosSessionFactory.Sessao sessao,
                                        CupomDetectadoEntity cupom, int limiteOfertas) {
        String urlCampanha = cupom.getUrlDesencurtada() != null && !cupom.getUrlDesencurtada().isBlank()
                ? cupom.getUrlDesencurtada() : cupom.getUrlCampanha();

        log.info("[FLUXO 2] Navegando na campanha do cupom {} ({})... Link: {}",
                cupom.getCodigo(), cupom.getDesconto(), urlCampanha);

        long campaignId = Math.abs((long) cupom.getCodigo().hashCode());
        if (campaignId <= 0) campaignId = 1L;

        List<ProdutoElegivelCupom> produtos = sessao.produtosCupons().buscarProdutos(campaignId, urlCampanha);
        if (produtos.isEmpty()) {
            log.info("[FLUXO 2] Nenhum produto encontrado na página de campanha do cupom {}", cupom.getCodigo());
            return 0;
        }

        BigDecimal percentual = extrairPercentual(cupom.getDesconto());
        BigDecimal compraMinima = extrairValor(cupom.getCompraMinima());
        BigDecimal descontoMaximo = extrairValor(cupom.getDescontoMaximo());

        List<ProdutoElegivelCupom> candidatos = new ArrayList<>();
        for (ProdutoElegivelCupom p : produtos) {
            if (p.precoAtual() == null || p.precoAtual().signum() <= 0) {
                log.debug("Produto {} ignorado porque não possui preço válido", p.itemId());
                continue;
            }
            if (compraMinima != null && p.precoAtual().compareTo(compraMinima) < 0) {
                log.debug("Produto {} ignorado: preço abaixo da compra mínima do cupom", p.itemId());
                continue;
            }
            if (publicadas.bloqueadaParaPublicacao(FORNECEDOR, p.itemId())) {
                log.debug("Produto {} já publicado anteriormente", p.itemId());
                continue;
            }
            candidatos.add(p);
            if (candidatos.size() >= limiteOfertas) break;
        }

        int enviados = 0;
        for (ProdutoElegivelCupom p : candidatos) {
            BigDecimal precoAtual = p.precoAtual() != null ? p.precoAtual() : BigDecimal.ZERO;
            BigDecimal descontoAplicado = precoAtual.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (descontoMaximo != null && descontoAplicado.compareTo(descontoMaximo) > 0) {
                descontoAplicado = descontoMaximo;
            }
            BigDecimal precoEstimado = precoAtual.subtract(descontoAplicado).max(BigDecimal.ZERO);

            CondicoesCupom condicoes = new CondicoesCupom(campaignId, null, null, compraMinima,
                    percentual, null, descontoMaximo, null, null, null, null, null);

            CupomAplicavel cupomAplicavel = new CupomAplicavel(
                    campaignId,
                    cupom.getCodigo(),
                    "Cupom " + cupom.getDesconto(),
                    condicoes,
                    descontoAplicado,
                    precoEstimado,
                    compraMinima,
                    null,
                    ValidationConfidence.HIGH,
                    Instant.now(),
                    null,
                    null
            );

            OfertaAfiliado oferta = new OfertaAfiliado(
                    p.itemId(),
                    p.productId(),
                    p.title(),
                    precoAtual,
                    precoEstimado,
                    cupom.getDesconto(),
                    null,
                    cupom.getCategoria(),
                    p.productUrl(),
                    p.imageUrl(),
                    cupomAplicavel,
                    OrigemOferta.CUPOM
            );

            // Imagem do produto
            OfertaAfiliado comImagem = sessao.resolverImagem(oferta);
            if (comImagem == null) comImagem = oferta;

            // Link de afiliado
            List<ResultadoLinkAfiliado> links = sessao.gerarLinks(List.of(p.productUrl()));
            String shortUrl = null;
            if (!links.isEmpty() && links.getFirst().shortUrl() != null) {
                shortUrl = links.getFirst().shortUrl();
            }

            if (shortUrl == null || shortUrl.isBlank()) {
                log.warn("[FLUXO 2] Não foi possível gerar link de afiliado oficial para o item {}", p.itemId());
                continue;
            }

            ResultadoPublicacaoOferta resultado = publicador.publicarComLink(FORNECEDOR, comImagem, shortUrl);
            if (resultado == ResultadoPublicacaoOferta.ENVIADA) {
                enviados++;
                log.info("🚀 [FLUXO 2] Produto {} com cupom {} publicado no Telegram com sucesso!", p.itemId(), cupom.getCodigo());
            }
        }

        return enviados;
    }

    /**
     * Resolve redirecionamentos de links como bit.ly para a URL final do Mercado Livre.
     */
    public String desencurtarUrl(String url) {
        if (url == null || url.isBlank()) return url;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(Duration.ofSeconds(8))
                    .build();
            HttpClient httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.uri().toString();
        } catch (Exception e) {
            log.debug("Não foi possível seguir redirecionamento de {}: {}", url, e.getMessage());
            return url;
        }
    }

    private BigDecimal extrairPercentual(String desconto) {
        if (desconto == null) return BigDecimal.TEN;
        Matcher m = PERCENT_PATTERN.matcher(desconto);
        if (m.find()) {
            try {
                return new BigDecimal(m.group(1));
            } catch (Exception ignored) {}
        }
        return BigDecimal.TEN;
    }

    private BigDecimal extrairValor(String texto) {
        if (texto == null) return null;
        Matcher m = VALOR_PATTERN.matcher(texto);
        if (m.find()) {
            try {
                String clean = m.group(1).replace(".", "").replace(",", ".");
                return new BigDecimal(clean);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
