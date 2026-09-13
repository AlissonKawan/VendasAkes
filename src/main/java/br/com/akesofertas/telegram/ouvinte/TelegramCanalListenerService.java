package br.com.akesofertas.telegram.ouvinte;

import it.tdlight.Init;
import it.tdlight.client.APIToken;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientBuilder;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Serviço 100% em Java que se conecta à conta do Telegram usando TDLight/TDLib.
 * Escuta em tempo real e varre o histórico recente do canal privado oficial de afiliados do Mercado Livre,
 * filtrando apenas cupons válidos (não vencidos) e enviando-os para o CampanhaProcessingService.
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramCanalListenerService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TelegramCanalListenerService.class);

    private final CampanhaProcessingService campanhaProcessor;
    private final boolean habilitado;
    private final int apiId;
    private final String apiHash;
    private final String canalTituloFiltro;
    private final int historicoLimite;
    private final Path sessaoPath;

    private SimpleTelegramClientFactory clientFactory;
    private SimpleTelegramClient client;
    private Long canalChatId = null;
    private final Set<Long> mensagensProcessadas = Collections.synchronizedSet(new HashSet<>());

    @Autowired
    public TelegramCanalListenerService(
            CampanhaProcessingService campanhaProcessor,
            @Value("${telegram.listener.enabled:true}") boolean habilitado,
            @Value("${TELEGRAM_API_ID:0}") int apiId,
            @Value("${TELEGRAM_API_HASH:}") String apiHash,
            @Value("${telegram.listener.canal-titulo:Afiliados e Criadores Mercado Livre}") String canalTituloFiltro,
            @Value("${telegram.listener.historico-limite:250}") int historicoLimite,
            @Value("${telegram.listener.sessao-path:.local/tdlight-session}") String sessaoPathStr) {
        this.campanhaProcessor = campanhaProcessor;
        this.habilitado = habilitado;
        this.apiId = apiId;
        this.apiHash = apiHash != null ? apiHash.trim() : "";
        this.canalTituloFiltro = canalTituloFiltro;
        this.historicoLimite = Math.max(5, historicoLimite);
        this.sessaoPath = Path.of(sessaoPathStr);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciarAssincrono() {
        if (!habilitado) {
            log.info("[TELEGRAM_OUVINTE] Ouvinte desabilitado por configuração");
            return;
        }

        if (apiId <= 0 || apiHash.isBlank()) {
            log.warn("[TELEGRAM_OUVINTE] TELEGRAM_API_ID ou TELEGRAM_API_HASH não configurados. " +
                     "Configure as variáveis de ambiente para ativar a escuta automática do canal de cupons.");
            return;
        }

        Thread listenerThread = new Thread(this::iniciarCliente, "telegram-canal-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void iniciarCliente() {
        try {
            log.info("[TELEGRAM_OUVINTE] Inicializando motor nativo TDLight Java...");
            Init.init();

            this.clientFactory = new SimpleTelegramClientFactory();
            APIToken apiToken = new APIToken(apiId, apiHash);
            TDLibSettings settings = TDLibSettings.create(apiToken);
            settings.setDatabaseDirectoryPath(sessaoPath.toAbsolutePath());

            SimpleTelegramClientBuilder builder = clientFactory.builder(settings);

            // Handler para escuta em tempo real
            builder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::aoReceberNovaMensagem);

            log.info("[TELEGRAM_OUVINTE] Conectando à conta do Telegram (sessão: {})...", sessaoPath);
            this.client = builder.build(AuthenticationSupplier.consoleLogin());

            // Aguarda login e carregamento dos chats
            // No primeiro uso o TDLight só conclui esta chamada depois que o usuário
            // autentica a sessão. Um timeout fazia o ouvinte morrer enquanto o QR ainda
            // aguardava confirmação; a aplicação encerra esta espera no shutdown.
            client.loadChatListMainAsync().get();

            // Localiza o chat do canal
            localizarCanal();

            // Executa a varredura do histórico recente
            if (canalChatId != null) {
                varrerHistoricoRecente(canalChatId);
            }

            log.info("[TELEGRAM_OUVINTE] Ouvinte ativo e monitorando novas postagens de cupons em tempo real.");
        } catch (Exception e) {
            log.error("[TELEGRAM_OUVINTE] Erro ao iniciar ouvinte do Telegram: {}", e.getMessage(), e);
        }
    }

    private void localizarCanal() {
        try {
            log.info("[TELEGRAM_OUVINTE] Buscando canal nos chats da conta...");
            TdApi.Chats chats = client.send(new TdApi.GetChats(new TdApi.ChatListMain(), 100)).get(15, TimeUnit.SECONDS);

            for (long id : chats.chatIds) {
                try {
                    TdApi.Chat chat = client.send(new TdApi.GetChat(id)).get(5, TimeUnit.SECONDS);
                    if (chat != null && chat.title != null) {
                        if (tituloCorresponde(chat.title)) {
                            this.canalChatId = id;
                            log.info("🎯 [TELEGRAM_OUVINTE] Canal de cupons localizado com sucesso! Título: '{}' (ID: {})", chat.title, id);
                            return;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Fallback para SearchChats se não estiver nos primeiros 100 chats principais
            TdApi.Chats searchResults = client.send(new TdApi.SearchChats(canalTituloFiltro, null, 20)).get(15, TimeUnit.SECONDS);
            for (long id : searchResults.chatIds) {
                try {
                    TdApi.Chat chat = client.send(new TdApi.GetChat(id)).get(5, TimeUnit.SECONDS);
                    if (chat != null && chat.title != null && tituloCorresponde(chat.title)) {
                        this.canalChatId = id;
                        log.info("🎯 [TELEGRAM_OUVINTE] Canal localizado via busca: '{}' (ID: {})", chat.title, id);
                        return;
                    }
                } catch (Exception ignored) {}
            }

            log.warn("[TELEGRAM_OUVINTE] Canal 'Afiliados e Criadores Mercado Livre' não foi localizado nos chats.");
        } catch (Exception e) {
            log.warn("[TELEGRAM_OUVINTE] Falha ao localizar canal: {}", e.getMessage());
        }
    }

    /**
     * Varre as últimas mensagens do canal e processa apenas os cupons cuja validade ainda não expirou.
     */
    public void varrerHistoricoRecente(long chatId) {
        try {
            log.info("[TELEGRAM_OUVINTE] Varrendo as últimas {} mensagens do canal para cupons válidos...", historicoLimite);
            List<TdApi.Message> mensagens = carregarHistoricoPaginado(chatId);
            if (mensagens.isEmpty()) {
                log.info("[TELEGRAM_OUVINTE] Nenhuma mensagem retornada no histórico.");
                return;
            }

            LocalDate hoje = LocalDate.now();
            int cuponsValidosTotal = 0;
            int mensagensComTexto = 0;
            int cuponsReconhecidos = 0;
            int cuponsVencidos = 0;

            // Processa da mensagem mais antiga para a mais recente
            for (int i = mensagens.size() - 1; i >= 0; i--) {
                TdApi.Message msg = mensagens.get(i);
                if (mensagensProcessadas.contains(msg.id)) continue;
                mensagensProcessadas.add(msg.id);

                String conteudo = TelegramMensagemExtractor.extrair(msg.content);
                if (conteudo != null) {
                    mensagensComTexto++;
                    List<CupomDetectado> reconhecidos = MercadoLivreTelegramCupomParser.extrairCupons(conteudo, hoje);
                    List<CupomDetectado> cupons = reconhecidos.stream()
                            .filter(cupom -> cupom.estaValido(hoje))
                            .toList();
                    cuponsReconhecidos += reconhecidos.size();
                    cuponsVencidos += reconhecidos.size() - cupons.size();
                    if (!cupons.isEmpty()) {
                        log.info("[TELEGRAM_OUVINTE] Mensagem ID {} possui {} cupom(ns) válido(s). Salvando no banco...", msg.id, cupons.size());
                        campanhaProcessor.salvarCuponsDetectados(cupons);
                        cuponsValidosTotal += cupons.size();
                    }
                }
            }

            log.info("[TELEGRAM_OUVINTE] Diagnóstico da varredura: mensagens={}, com texto/legenda={}, " +
                            "cupons reconhecidos={}, vencidos={}, válidos salvos={}",
                    mensagens.size(), mensagensComTexto, cuponsReconhecidos, cuponsVencidos, cuponsValidosTotal);
            if (cuponsValidosTotal > 0) {
                log.info("[TELEGRAM_OUVINTE] Cupons salvos para o próximo ciclo agendado de publicação.");
            }
        } catch (Exception e) {
            log.error("[TELEGRAM_OUVINTE] Erro ao varrer histórico do canal: {}", e.getMessage());
        }
    }

    private List<TdApi.Message> carregarHistoricoPaginado(long chatId) throws Exception {
        Map<Long, TdApi.Message> mensagens = new LinkedHashMap<>();
        long aPartirDaMensagem = 0;

        while (mensagens.size() < historicoLimite) {
            int tamanhoPagina = Math.min(100, historicoLimite - mensagens.size());
            TdApi.Messages pagina = client.send(
                            new TdApi.GetChatHistory(chatId, aPartirDaMensagem, 0, tamanhoPagina, false))
                    .get(30, TimeUnit.SECONDS);
            if (pagina == null || pagina.messages == null || pagina.messages.length == 0) break;

            int quantidadeAnterior = mensagens.size();
            for (TdApi.Message mensagem : pagina.messages) {
                if (mensagem != null) mensagens.putIfAbsent(mensagem.id, mensagem);
            }
            if (mensagens.size() == quantidadeAnterior) break;

            long mensagemMaisAntiga = pagina.messages[pagina.messages.length - 1].id;
            if (mensagemMaisAntiga == aPartirDaMensagem) break;
            aPartirDaMensagem = mensagemMaisAntiga;
        }

        return new ArrayList<>(mensagens.values());
    }

    private void aoReceberNovaMensagem(TdApi.UpdateNewMessage update) {
        try {
            TdApi.Message msg = update.message;
            if (canalChatId == null || msg.chatId != canalChatId) return;

            if (!mensagensProcessadas.add(msg.id)) {
                return; // Mensagem já tratada
            }

            String conteudo = TelegramMensagemExtractor.extrair(msg.content);
            if (conteudo != null) {
                LocalDate hoje = LocalDate.now();
                List<CupomDetectado> cupons = MercadoLivreTelegramCupomParser.extrairCuponsValidos(conteudo, hoje);

                if (!cupons.isEmpty()) {
                    log.info("🚨 [TELEGRAM_OUVINTE] NOVA MENSAGEM COM {} CUPOM(NS) VÁLIDO(S) RECEBIDA! Salvando no banco...", cupons.size());
                    campanhaProcessor.salvarCuponsDetectados(cupons);
                    log.info("[TELEGRAM_OUVINTE] Novos cupons aguardando o próximo ciclo agendado de publicação.");
                }
            }
        } catch (Exception e) {
            log.error("[TELEGRAM_OUVINTE] Erro ao processar mensagem recebida em tempo real: {}", e.getMessage(), e);
        }
    }

    private boolean tituloCorresponde(String titulo) {
        String atual = normalizar(titulo);
        String esperado = normalizar(canalTituloFiltro);
        if (!esperado.isBlank() && atual.contains(esperado)) return true;
        return atual.contains("afiliados") && atual.contains("mercado livre");
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .trim();
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                client.closeAndWait();
            } catch (Exception ignored) {}
        }
        if (clientFactory != null) {
            try {
                clientFactory.close();
            } catch (Exception ignored) {}
        }
    }
}
