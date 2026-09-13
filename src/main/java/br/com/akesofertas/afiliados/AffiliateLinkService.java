package br.com.akesofertas.afiliados;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AffiliateLinkService {
    private static final Set<String> HOSTS_PRODUTO = Set.of(
            "www.mercadolivre.com.br", "produto.mercadolivre.com.br");
    private final AffiliateLinkClient client;
    private final String tag;

    public AffiliateLinkService(AffiliateLinkClient client,
                                @Value("${afiliados.tag:telegram}") String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Defina uma etiqueta de afiliado em AFILIADOS_TAG.");
        }
        this.client = client;
        this.tag = tag.trim();
    }

    /** O consumidor recebe apenas o link; não conhece cookies, headers ou DTOs do portal. */
    public Map<String, String> gerarLinks(List<String> urlsProduto) {
        Map<String, String> links = new LinkedHashMap<>();
        for (ResultadoLinkAfiliado resultado : gerarResultados(urlsProduto)) {
            if (resultado.sucesso()) links.put(resultado.originUrl(), resultado.shortUrl());
        }
        return Map.copyOf(links);
    }

    public List<ResultadoLinkAfiliado> gerarResultados(List<String> urlsProduto) {
        if (urlsProduto == null || urlsProduto.isEmpty()) {
            return List.of();
        }

        List<String> validUrls = urlsProduto.stream()
                .map(u -> validarUrl(u, HOSTS_PRODUTO, "URL do produto").toString())
                .collect(Collectors.toList());

        AffiliateLinkRequest pedido = new AffiliateLinkRequest(validUrls, tag);
        AffiliateLinkResponse resposta = client.criarLink(pedido);
        return extrairResultados(resposta, validUrls);
    }

    private List<ResultadoLinkAfiliado> extrairResultados(AffiliateLinkResponse resposta, List<String> requestedUrls) {
        if (resposta == null || !Integer.valueOf(200).equals(resposta.status())) {
            throw new AffiliateLinkException("Mercado Livre não retornou status 200 no JSON de resposta.");
        }
        if (resposta.urls() == null) {
            throw new AffiliateLinkException("Mercado Livre retornou lista de URLs nula.");
        }

        Map<String, ResultadoLinkAfiliado> porOrigem = new LinkedHashMap<>();
        for (AffiliateLinkResponse.Link link : resposta.urls()) {
            if (link.originUrl() == null) {
                continue;
            }
            String shortUrl = null;
            boolean sucesso = false;
            try {
                if (tag.equals(link.tag())) {
                    shortUrl = validarUrl(link.shortUrl(), Set.of("meli.la"), "Link de afiliado retornado").toString();
                    sucesso = true;
                }
            } catch (AffiliateLinkException ignored) {}
            porOrigem.putIfAbsent(chaveCorrelacao(link.originUrl()), new ResultadoLinkAfiliado(link.originUrl(), sucesso,
                    shortUrl, link.created(), link.errorCode(), link.message()));
        }

        var resultados = new java.util.ArrayList<ResultadoLinkAfiliado>();
        for (String solicitada : requestedUrls) {
            ResultadoLinkAfiliado retornado = porOrigem.get(chaveCorrelacao(solicitada));
            resultados.add(retornado == null
                    ? new ResultadoLinkAfiliado(solicitada, false, null, null, null, null)
                    : new ResultadoLinkAfiliado(solicitada, retornado.sucesso(), retornado.shortUrl(),
                            retornado.created(), retornado.errorCode(), retornado.message()));
        }
        return List.copyOf(resultados);
    }

    private static String chaveCorrelacao(String valor) {
        try {
            URI uri = URI.create(valor);
            return new URI(uri.getScheme(), uri.getRawAuthority(), uri.getRawPath(), uri.getRawQuery(), null)
                    .toASCIIString();
        } catch (Exception exception) {
            return valor;
        }
    }

    private static URI validarUrl(String valor, Set<String> hosts, String campo) {
        try {
            URI uri = URI.create(valor == null ? "" : valor.trim());
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && hosts.stream().anyMatch(host -> host.equalsIgnoreCase(uri.getHost()))
                    && uri.getUserInfo() == null && uri.getPort() == -1
                    && uri.getPath() != null && uri.getPath().length() > 1) {
                return uri;
            }
        } catch (IllegalArgumentException ignored) {
            // Não incluir o valor recebido na exceção: ele pode conter dados privados.
        }
        throw new AffiliateLinkException(campo + " deve ser uma URL HTTPS do domínio esperado, com caminho.");
    }
}
