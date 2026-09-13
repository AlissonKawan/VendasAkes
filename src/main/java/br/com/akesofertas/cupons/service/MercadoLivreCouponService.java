package br.com.akesofertas.cupons.service;

import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.afiliados.ofertas.OrigemOferta;
import br.com.akesofertas.cupons.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Serviço de gerenciamento e cache dos cupons normais do consumidor do Mercado Livre.
 * Mantém cache em memória por TTL para não sobrecarregar as requisições do ciclo.
 */
@Service
public class MercadoLivreCouponService {
    private static final Logger log = LoggerFactory.getLogger(MercadoLivreCouponService.class);

    private final CupomElegibilidadeService elegibilidadeService;
    private final Duration ttl;
    private final Clock clock;
    private final int maxPaginas;
    private final int maxCuponsPorCiclo;
    private final int maxContainersPorCiclo;
    private final int maxOfertasDiretasPorCiclo;
    private final int maxCandidatosCompartilhamento;
    private final Duration errorCooldown;

    private List<MercadoLivreCupom> cuponsEmCache = List.of();
    private final Map<String, ContainerCacheEntry> containerCache = new HashMap<>();
    private final Map<String, List<MercadoLivreCupom>> indiceCuponsPorItem = new HashMap<>();
    private Instant expiraEm = Instant.MIN;

    @Autowired
    public MercadoLivreCouponService(
            CupomElegibilidadeService elegibilidadeService,
            @Value("${akes.cupons.cache-ttl-segundos:1800}") long ttlSegundos,
            @Value("${akes.cupons.max-paginas:2}") int maxPaginas,
            @Value("${akes.cupons.max-cupons-por-ciclo:10}") int maxCuponsPorCiclo,
            @Value("${akes.cupons.max-containers-por-ciclo:5}") int maxContainersPorCiclo,
            @Value("${akes.cupons.max-ofertas-diretas-por-ciclo:20}") int maxOfertasDiretasPorCiclo,
            @Value("${akes.cupons.max-candidatos-compartilhamento:${akes.cupons.max-candidatos-create-link:20}}")
            int maxCandidatosCompartilhamento,
            @Value("${akes.cupons.container-error-cooldown-segundos:300}") long errorCooldownSegundos) {
        this(elegibilidadeService, Duration.ofSeconds(ttlSegundos), Clock.systemDefaultZone(), maxPaginas,
                maxCuponsPorCiclo, maxContainersPorCiclo, maxOfertasDiretasPorCiclo, maxCandidatosCompartilhamento,
                Duration.ofSeconds(errorCooldownSegundos));
    }

    public MercadoLivreCouponService(
            CupomElegibilidadeService elegibilidadeService,
            Duration ttl,
            Clock clock,
            int maxPaginas) {
        this(elegibilidadeService, ttl, clock, maxPaginas, 10, 5, 1, 3, Duration.ofMinutes(5));
    }

    public MercadoLivreCouponService(
            CupomElegibilidadeService elegibilidadeService,
            Duration ttl,
            Clock clock,
            int maxPaginas,
            int maxContainersPorCiclo,
            int maxOfertasDiretasPorCiclo,
            int maxCandidatosCompartilhamento,
            Duration errorCooldown) {
        this(elegibilidadeService, ttl, clock, maxPaginas, 10, maxContainersPorCiclo,
                maxOfertasDiretasPorCiclo, maxCandidatosCompartilhamento, errorCooldown);
    }

    public MercadoLivreCouponService(
            CupomElegibilidadeService elegibilidadeService,
            Duration ttl,
            Clock clock,
            int maxPaginas,
            int maxCuponsPorCiclo,
            int maxContainersPorCiclo,
            int maxOfertasDiretasPorCiclo,
            int maxCandidatosCompartilhamento,
            Duration errorCooldown) {
        this.elegibilidadeService = Objects.requireNonNull(elegibilidadeService, "elegibilidadeService é obrigatório");
        this.ttl = Objects.requireNonNull(ttl, "ttl é obrigatório");
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");
        this.maxPaginas = Math.max(1, maxPaginas);
        this.maxCuponsPorCiclo = Math.max(1, maxCuponsPorCiclo);
        this.maxContainersPorCiclo = Math.max(1, maxContainersPorCiclo);
        this.maxOfertasDiretasPorCiclo = Math.max(1, maxOfertasDiretasPorCiclo);
        this.maxCandidatosCompartilhamento = Math.max(1, maxCandidatosCompartilhamento);
        this.errorCooldown = Objects.requireNonNull(errorCooldown, "errorCooldown é obrigatório");
    }

    public int totalCuponsCache() {
        return cuponsEmCache.size();
    }

    public int maxContainersPorCiclo() {
        return maxContainersPorCiclo;
    }

    public int maxCuponsPorCiclo() {
        return maxCuponsPorCiclo;
    }

    public int maxOfertasDiretasPorCiclo() {
        return maxOfertasDiretasPorCiclo;
    }

    public int maxCandidatosCompartilhamento() {
        return maxCandidatosCompartilhamento;
    }

    public Map<String, ContainerCacheEntry> containerCache() {
        return Collections.unmodifiableMap(containerCache);
    }

    /**
     * Enriquece a oferta com o melhor cupom normal elegível (menor preço estimado / maior desconto).
     * Usa o índice em memória sem provocar requisições adicionais de navegação.
     */
    public synchronized OfertaAfiliado enriquecer(OfertaAfiliado oferta) {
        if (oferta == null || oferta.itemId() == null || oferta.itemId().isBlank()) return oferta;
        Instant agora = clock.instant();
        List<MercadoLivreCupom> candidatos = indiceCuponsPorItem.get(oferta.itemId());
        if (candidatos == null || candidatos.isEmpty()) {
            return oferta;
        }

        Optional<CupomAplicavel> melhor = candidatos.stream()
                .filter(c -> !c.expirado(agora) && c.ativo())
                .map(c -> elegibilidadeService.avaliar(oferta, c, agora))
                .flatMap(Optional::stream)
                .min(Comparator.comparing(CupomAplicavel::precoEstimado)
                        .thenComparing(CupomAplicavel::descontoAplicado, Comparator.reverseOrder()));

        if (melhor.isPresent()) {
            CupomAplicavel cupom = melhor.get();
            log.info("[DIAGNOSTICO_CUPOM] Cupom associado da fonte: itemId={} couponId={} titulo='{}' codigoExibivel='{}' temCodigo={} descontoAplicado={} compraMinima={} precoEstimado={}",
                    oferta.itemId(), cupom.couponId(), cupom.title(), cupom.codigoExibivel(),
                    cupom.temCodigoExibivel(), cupom.descontoAplicado(), cupom.compraMinima(), cupom.precoEstimado());
            return oferta.comCupom(cupom);
        }
        return oferta;
    }

    public synchronized void salvarCuponsEmCache(List<MercadoLivreCupom> cupons) {
        if (cupons != null) {
            this.cuponsEmCache = List.copyOf(cupons);
            this.expiraEm = clock.instant().plus(ttl);
            reconstruirIndice(clock.instant());
        }
    }

    static OptionalLong parsearCampaignId(String rawId) {
        if (rawId == null || rawId.isBlank()) return OptionalLong.empty();
        try {
            long id = Long.parseLong(rawId.trim());
            return id > 0 ? OptionalLong.of(id) : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    private void reconstruirIndice(Instant agora) {
        indiceCuponsPorItem.clear();
        for (MercadoLivreCupom c : cuponsEmCache) {
            if (!c.ativo() || c.expirado(agora)) continue;
            for (String itemId : c.itemIdsElegiveis()) {
                if (itemId != null && !itemId.isBlank()) {
                    indiceCuponsPorItem.computeIfAbsent(itemId, k -> new ArrayList<>()).add(c);
                }
            }
        }
        for (ContainerCacheEntry entry : containerCache.values()) {
            if (!entry.erro() && !entry.expirado(agora)) {
                for (MercadoLivreCupom c : cuponsEmCache) {
                    if (c.urlProdutos() != null && c.urlProdutos().equals(entry.containerUrl())) {
                        atualizarIndiceComProdutos(c, entry.produtos());
                    }
                }
            }
        }
    }

    private void atualizarIndiceComProdutos(MercadoLivreCupom cupom, List<ProdutoElegivelCupom> prods) {
        if (prods == null || prods.isEmpty()) return;
        Set<String> novosItens = new HashSet<>(cupom.itemIdsElegiveis());
        for (ProdutoElegivelCupom p : prods) {
            if (p.itemId() != null && !p.itemId().isBlank()) {
                novosItens.add(p.itemId());
            }
        }
        MercadoLivreCupom cupomExpandido = new MercadoLivreCupom(
                cupom.id(), cupom.titulo(), cupom.codigoExibivel(),
                cupom.tokenAtivacao(), cupom.tipoAtivacao(), cupom.tipoDesconto(),
                cupom.valorDesconto(), cupom.compraMinima(), cupom.descontoMaximo(),
                cupom.dataExpiracao(), cupom.status(), cupom.urlProdutos(),
                novosItens, cupom.itensPreview());

        for (ProdutoElegivelCupom p : prods) {
            if (p.itemId() != null && !p.itemId().isBlank()) {
                List<MercadoLivreCupom> lista = indiceCuponsPorItem.computeIfAbsent(p.itemId(), k -> new ArrayList<>());
                lista.removeIf(c -> c.id().equals(cupom.id()));
                lista.add(cupomExpandido);
            }
        }
    }

    public synchronized void limparCache() {
        this.cuponsEmCache = List.of();
        this.containerCache.clear();
        this.indiceCuponsPorItem.clear();
        this.expiraEm = Instant.MIN;
    }

    public synchronized void salvarContainerEmCache(ContainerCacheEntry entry) {
        if (entry != null && entry.containerUrl() != null) {
            this.containerCache.put(entry.containerUrl(), entry);
            reconstruirIndice(clock.instant());
        }
    }

    public static class ContainerCacheEntry {
        private final long couponId;
        private final String containerUrl;
        private final List<ProdutoElegivelCupom> produtos;
        private final Instant resolvidoEm;
        private final Instant expiraEm;
        private final boolean erro;
        private final String motivoErro;

        public ContainerCacheEntry(long couponId, String containerUrl, List<ProdutoElegivelCupom> produtos,
                                   Instant resolvidoEm, Instant expiraEm, boolean erro, String motivoErro) {
            this.couponId = couponId;
            this.containerUrl = containerUrl;
            this.produtos = produtos != null ? List.copyOf(produtos) : List.of();
            this.resolvidoEm = resolvidoEm;
            this.expiraEm = expiraEm;
            this.erro = erro;
            this.motivoErro = motivoErro;
        }

        public long couponId() { return couponId; }
        public String containerUrl() { return containerUrl; }
        public List<ProdutoElegivelCupom> produtos() { return produtos; }
        public Instant resolvidoEm() { return resolvidoEm; }
        public Instant expiraEm() { return expiraEm; }
        public boolean erro() { return erro; }
        public String motivoErro() { return motivoErro; }
        public boolean expirado(Instant agora) { return agora != null && agora.isAfter(expiraEm); }
    }

    public static class ContainerMetricas {
        private int containersResolvidos = 0;
        private int containersDoCache = 0;
        private int containersIgnoradosCooldown = 0;

        public int containersResolvidos() { return containersResolvidos; }
        public int containersDoCache() { return containersDoCache; }
        public int containersIgnoradosCooldown() { return containersIgnoradosCooldown; }
        public void registrarResolvido() { containersResolvidos++; }
        public void registrarCache() { containersDoCache++; }
        public void registrarCooldown() { containersIgnoradosCooldown++; }
    }
}
