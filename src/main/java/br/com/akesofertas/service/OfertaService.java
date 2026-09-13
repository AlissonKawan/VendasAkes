package br.com.akesofertas.service;

import br.com.akesofertas.client.TelegramClient;
import br.com.akesofertas.dto.OfertaRequest;
import br.com.akesofertas.dto.OfertaResponse;
import br.com.akesofertas.exception.OfertaInvalidaException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.net.URI;
import java.text.NumberFormat;
import java.util.Locale;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
public class OfertaService {
    private final TelegramClient telegram;

    public OfertaService(TelegramClient telegram) {
        this.telegram = telegram;
    }

    public OfertaResponse publicar(OfertaRequest oferta) {
        validarUrl(oferta.urlProduto(), "urlProduto");
        if (temTexto(oferta.imagemUrl())) {
            validarUrl(oferta.imagemUrl(), "imagemUrl");
        }
        String mensagem = formatar(oferta);
        // Mede o texto visível, antes das entidades HTML.
        String texto = mensagem.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        int limite = temTexto(oferta.imagemUrl()) ? 1024 : 4096;
        if (texto.length() > limite) {
            throw new OfertaInvalidaException("Mensagem excede " + limite
                    + " caracteres. Reduza os campos ou remova imagemUrl para enviar texto.");
        }
        long id = telegram.enviar(mensagem, oferta.imagemUrl());
        return new OfertaResponse(true, "Oferta publicada no Telegram", id);
    }

    String formatar(OfertaRequest oferta) {
        String mensagem = "🔥 OFERTA ENCONTRADA\n\n"
                + "🛒 Produto: " + html(oferta.titulo()) + "\n"
                + "🏬 Loja: " + html(oferta.loja()) + "\n"
                + "💰 Preço atual: R$ " + dinheiro(oferta.precoAtual()) + "\n";
        if (oferta.precoAntigo() != null) {
            mensagem += "💸 Preço anterior: R$ " + dinheiro(oferta.precoAntigo()) + "\n";
        }
        if (temTexto(oferta.cupom())) {
            mensagem += "🏷 Cupom: " + html(oferta.cupom()) + "\n";
        }
        return mensagem + "🔗 Link: " + html(oferta.urlProduto())
                + "\n\n⚡ Akes Ofertas | Ofertas &amp; Cupons";
    }

    private String dinheiro(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return formato.format(valor);
    }

    private String html(String valor) {
        return valor.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private void validarUrl(String valor, String campo) {
        try {
            URI uri = URI.create(valor);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // A mesma resposta é usada para todas as URLs inválidas.
        }
        throw new OfertaInvalidaException(campo + " deve ser uma URL HTTP/HTTPS válida");
    }
}
