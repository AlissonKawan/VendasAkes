package br.com.akesofertas.telegram.ouvinte;

import it.tdlight.jni.TdApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Extrai texto, legendas e URLs ocultas das mensagens recebidas pela TDLib. */
final class TelegramMensagemExtractor {

    private TelegramMensagemExtractor() {}

    static String extrair(TdApi.MessageContent content) {
        TdApi.FormattedText formattedText = switch (content) {
            case TdApi.MessageText text -> text.text;
            case TdApi.MessagePhoto photo -> photo.caption;
            case TdApi.MessageVideo video -> video.caption;
            case TdApi.MessageAnimation animation -> animation.caption;
            case TdApi.MessageDocument document -> document.caption;
            default -> null;
        };
        return extrair(formattedText);
    }

    static String extrair(TdApi.FormattedText formattedText) {
        if (formattedText == null || formattedText.text == null || formattedText.text.isBlank()) {
            return null;
        }

        String texto = formattedText.text;
        List<UrlOculta> urlsOcultas = new ArrayList<>();
        if (formattedText.entities != null) {
            for (TdApi.TextEntity entity : formattedText.entities) {
                if (entity != null && entity.type instanceof TdApi.TextEntityTypeTextUrl textUrl
                        && textUrl.url != null && !textUrl.url.isBlank()
                        && !texto.contains(textUrl.url)) {
                    int posicao = Math.max(0, Math.min(texto.length(), entity.offset + entity.length));
                    urlsOcultas.add(new UrlOculta(posicao, textUrl.url.trim()));
                }
            }
        }

        if (urlsOcultas.isEmpty()) return texto;
        StringBuilder resultado = new StringBuilder(texto);
        urlsOcultas.stream().sorted(Comparator.comparingInt(UrlOculta::posicao).reversed())
                .forEach(url -> resultado.insert(url.posicao(), System.lineSeparator() + url.valor()));
        return resultado.toString();
    }

    private record UrlOculta(int posicao, String valor) {}
}
