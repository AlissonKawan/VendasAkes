package br.com.akesofertas.telegram;

import br.com.akesofertas.afiliados.*;
import br.com.akesofertas.afiliados.ofertas.*;
import br.com.akesofertas.afiliados.rejeicao.*;
import br.com.akesofertas.publicacao.*;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.boot.SpringApplication;
import java.nio.file.Path;
import java.util.List;

/** Executa no máximo um envio real, sem dados ou links de fallback. */
public final class EnviarOfertaTelegramDiagnostico {
    public static void main(String[] args) {
        var saida = System.out;
        var erros = System.err;
        int codigo;
        // O diagnóstico é um processo isolado: só imprime os campos públicos abaixo.
        try (var silencioso = new java.io.PrintStream(java.io.OutputStream.nullOutputStream())) {
            System.setOut(silencioso);
            System.setErr(silencioso);
            codigo = executar(saida);
        } finally {
            System.setOut(saida);
            System.setErr(erros);
        }
        System.exit(codigo);
    }

    private static int executar(java.io.PrintStream saida) {
        // Valida antes de iniciar Spring, banco ou navegador. Não aceita token via argumentos.
        if (System.getenv("TELEGRAM_BOT_TOKEN") == null || System.getenv("TELEGRAM_BOT_TOKEN").isBlank()) {
            saida.println("TELEGRAM_BOT_TOKEN não configurado");
            return 1;
        }
        if (System.getenv("TELEGRAM_CHAT_ID") == null || System.getenv("TELEGRAM_CHAT_ID").isBlank()) {
            saida.println("TELEGRAM_CHAT_ID não configurado");
            return 1;
        }
        String etapa = "inicializacao Spring/banco";
        try (var context = iniciarContexto()) {
            var db = context.getBean(OfertaPublicadaService.class);
            var repo = context.getBean(SpringDataOfertaPublicadaRepository.class);
            var telegram = context.getBean(TelegramService.class);
            String canal = System.getenv("AFILIADOS_NAVEGADOR");
            if (canal == null || canal.isBlank()) canal = "chrome";
            etapa = "abertura do navegador";
            try (var navegador = new NavegadorAfiliados(Path.of(".local/mercadolivre"), canal, false)) {
                etapa = "acesso ao Hub";
                navegador.pagina().navigate(MercadoLivreAffiliateOffersClient.HUB,
                        new com.microsoft.playwright.Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                etapa = "consulta do Hub e deduplicacao";
                var hub = new MercadoLivreAffiliateOffersClient(navegador.pagina(), new MercadoLivreAffiliateOfferMapper());
                var ofertas = hub.buscarLote();
                var rejeitadas = context.getBean(OfertaAfiliadoRejeitadaService.class);
                var linkService = new AffiliateLinkService(new MercadoLivreAffiliateClient(navegador.pagina()), "telegram");
                for (OfertaAfiliado oferta : ofertas) {
                    if (oferta.url() == null || oferta.itemId() == null || oferta.itemId().isBlank()) continue;
                    if (db.bloqueadaParaPublicacao("MERCADO_LIVRE", oferta.itemId())) continue;
                    if (rejeitadas.emCooldown("MERCADO_LIVRE", oferta.itemId())) continue;

                    saida.println("rastreamento itemId=" + oferta.itemId()
                            + " etapa=após-extração/mapper/OfertaAfiliado imagemPresente=" + (oferta.imagemUrl() != null)
                            + " imagemUrl=" + oferta.imagemUrl());

                    etapa = "geracao do link afiliado para " + oferta.itemId();
                    var resultado = linkService.gerarResultados(List.of(oferta.url())).stream()
                            .filter(r -> oferta.url().equals(r.originUrl())).findFirst().orElse(null);
                    if (resultado != null && Integer.valueOf(OfertaAfiliadoRejeitadaService.URL_NAO_PERMITIDA)
                            .equals(resultado.errorCode())) {
                        rejeitadas.registrarCodigo111("MERCADO_LIVRE", oferta);
                        saida.println("itemId rejeitado: " + oferta.itemId() + " | errorCode: 111");
                        continue;
                    }
                    if (resultado == null || !resultado.sucesso()) {
                        saida.println("itemId sem link confirmado: " + oferta.itemId() + " | errorCode: "
                                + (resultado == null ? "não informado" : resultado.errorCode()));
                        continue;
                    }

                    etapa = "resolucao da imagem na pagina original";
                    OfertaAfiliado ofertaPublicacao = new MercadoLivreImagemProdutoResolver(navegador.pagina())
                            .resolver(oferta);
                    saida.println("rastreamento itemId=" + oferta.itemId()
                            + " etapa=antes-publicador imagemPresente=" + (ofertaPublicacao.imagemUrl() != null)
                            + " imagemUrl=" + ofertaPublicacao.imagemUrl());
                    etapa = "registro PENDENTE_ENVIO";
                    var pendente = db.registrarPendente("MERCADO_LIVRE", ofertaPublicacao, resultado.shortUrl());
                    if (pendente == null) continue;
                    saida.println("itemId escolhido: " + oferta.itemId());
                    saida.println("titulo: " + oferta.titulo());
                    saida.println("preco: " + oferta.precoAtual());
                    saida.println("desconto: " + oferta.desconto());
                    saida.println("imagem após OfertaAfiliado: " + ofertaPublicacao.imagemUrl());
                    saida.println("short_url: " + resultado.shortUrl());
                    saida.println("status antes: " + pendente.status());
                    boolean enviado = false;
                    String resultadoTelegram;
                    etapa = "envio Telegram e registro do resultado";
                    try {
                        saida.println("imagem no Publicador/antes do Telegram: " + ofertaPublicacao.imagemUrl());
                        saida.println("método Telegram chamado: "
                                + (ofertaPublicacao.imagemUrl() != null
                                ? "sendPhoto" : "sendMessage"));
                        telegram.enviarOferta(pendente, ofertaPublicacao.imagemUrl());
                        enviado = true;
                        resultadoTelegram = "SUCESSO_HTTP_2XX / ok=true";
                    } catch (Exception e) {
                        db.marcarErroEnvio(pendente.id(), e.getMessage());
                        resultadoTelegram = "FALHA: " + e.getMessage();
                    }
                    etapa = "confirmacao do status no banco (conferir canal antes de repetir)";
                    if (enviado) db.marcarComoEnviada(pendente.id());
                    var finalEntity = repo.findById(pendente.id()).orElseThrow();
                    saida.println("resultado Telegram: " + resultadoTelegram);
                    saida.println("status final: " + finalEntity.getStatus());
                    saida.println("dataEnvio: " + finalEntity.getDataEnvio());
                    return enviado ? 0 : 1;
                }
                saida.println("Nenhuma oferta inédita elegível encontrada; nenhum envio realizado.");
                return 1;
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException && e.getMessage() != null
                    && e.getMessage().startsWith("SESSAO_AFILIADOS_EXPIRADA")) {
                saida.println("SESSAO_AFILIADOS_EXPIRADA: faça login manual com entrar-afiliados.ps1 e feche o navegador antes de executar novamente.");
            } else {
                saida.println("Diagnóstico interrompido na etapa: " + etapa + ". Tipo: " + e.getClass().getSimpleName()
                        + ". Nenhuma repetição automática realizada.");
            }
            return 1;
        }
    }

    static org.springframework.context.ConfigurableApplicationContext iniciarContexto() {
        // Este executável só precisa de JPA e Telegram. A aplicação normal mantém o OAuth.
        return SpringApplication.run(ConfiguracaoDiagnostico.class,
                "--spring.profiles.active=default", "--spring.main.web-application-type=none",
                "--spring.main.banner-mode=off", "--logging.level.root=OFF",
                "--spring.jpa.show-sql=false", "--telegram.enabled=true");
    }

    static void imprimirFalhasCreateLink(java.io.PrintStream saida, AffiliateLinkResponse resposta) {
        if (resposta.urls() == null) return;
        for (int i = 0; i < resposta.urls().size(); i++) {
            var link = resposta.urls().get(i);
            saida.println("createLink urls[" + i + "]:");
            saida.println("  origin_url: " + resumirUrl(link.originUrl()));
            saida.println("  created: " + link.created());
            saida.println("  short_url: " + resumirUrl(link.shortUrl()));
            saida.println("  tag: " + textoSeguro(link.tag()));
            saida.println("  error_code: " + link.errorCode());
            saida.println("  message: " + textoSeguro(link.message()));
            saida.println("  status: " + link.status());
        }
    }

    private static String resumirUrl(String valor) {
        if (valor == null || valor.isBlank()) return "null";
        try {
            var uri = java.net.URI.create(valor.trim());
            if (uri.getHost() == null && (valor.startsWith("www.mercadolivre.com.br/")
                    || valor.startsWith("produto.mercadolivre.com.br/"))) {
                uri = java.net.URI.create("https://" + valor.trim());
            }
            if (uri.getHost() == null) return "presente, formato não resumível";
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.length() > 180) path = path.substring(0, 180) + "...";
            return uri.getHost() + path;
        } catch (IllegalArgumentException exception) {
            return "presente, formato não resumível";
        }
    }

    private static String textoSeguro(String valor) {
        if (valor == null) return "null";
        String seguro = valor.replaceAll("https?://\\S+", "[URL]");
        return seguro.length() > 300 ? seguro.substring(0, 300) + "..." : seguro;
    }

    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.boot.persistence.autoconfigure.EntityScan(basePackageClasses = {
            OfertaPublicadaEntity.class, OfertaAfiliadoRejeitadaEntity.class})
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses = {
            SpringDataOfertaPublicadaRepository.class, OfertaAfiliadoRejeitadaRepository.class})
    @org.springframework.context.annotation.Import({OfertaPublicadaService.class,
            JpaOfertaPublicadaRepositoryAdapter.class, OfertaAfiliadoRejeitadaService.class,
            TelegramService.class, MensagemOfertaService.class})
    static class ConfiguracaoDiagnostico {}
}

