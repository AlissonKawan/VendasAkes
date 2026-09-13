package br.com.akesofertas.telegram.ouvinte;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analisa as mensagens publicadas no canal oficial de afiliados do Mercado Livre,
 * identificando cupons, porcentagens de desconto, condições de compra, data de validade e links.
 */
public final class MercadoLivreTelegramCupomParser {
    private static final Logger log = LoggerFactory.getLogger(MercadoLivreTelegramCupomParser.class);

    // Identifica o início de um cupom: ex "🎟️ SAINDOBARRATO 👉 30% OFF" ou "SAINDOBARRATO 👉 30% OFF"
    private static final Pattern CUPOM_HEADER_PATTERN = Pattern.compile(
            "^\\s*(?:🎟️|🎫)?\\s*([A-Z0-9_-]{3,30})\\s*(?:👉|->|:)\\s*(\\d+%(?:\\s*OFF)?)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    // Identifica a data de validade: ex "Válido até 05.10" ou "Válido até 16/09"
    private static final Pattern VALIDADE_PATTERN = Pattern.compile(
            "V[áa]lido\\s+at[ée]\\s+(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?",
            Pattern.CASE_INSENSITIVE
    );

    // Identifica valores de compra mínima e desconto máximo
    private static final Pattern COMPRA_MINIMA_PATTERN = Pattern.compile(
            "Compra\\s+m[íi]nima:\\s*(R\\$\\s*[\\d.,]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DESCONTO_MAX_PATTERN = Pattern.compile(
            "Desconto\\s+m[áa]x(?:imo)?:\\s*(R\\$\\s*[\\d.,]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Identifica categoria antes da compra mínima: ex "Beleza | Compra mínima" ou "KaBuM! | Compra mínima"
    private static final Pattern CATEGORIA_PATTERN = Pattern.compile(
            "^(.*?)\\s*\\|\\s*Compra\\s+m[íi]nima:",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    // Identifica links visíveis ou adicionados a partir de entidades TextUrl do Telegram.
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(https?://(?:[\\w-]+\\.)*(?:bit\\.ly|meli\\.la|mercadolivre\\.com(?:\\.br)?)/[^\\s<>]+)",
            Pattern.CASE_INSENSITIVE
    );

    private MercadoLivreTelegramCupomParser() {}

    /**
     * Extrai todos os cupons encontrados na mensagem.
     */
    public static List<CupomDetectado> extrairCupons(String texto, LocalDate dataReferencia) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }

        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<CupomDetectado> resultado = new ArrayList<>();

        // Divide o texto por quebras para identificar os blocos de cupons
        Matcher headerMatcher = CUPOM_HEADER_PATTERN.matcher(texto);
        List<Integer> startIndices = new ArrayList<>();
        List<String> codigos = new ArrayList<>();
        List<String> descontos = new ArrayList<>();

        while (headerMatcher.find()) {
            startIndices.add(headerMatcher.start());
            codigos.add(headerMatcher.group(1).trim().toUpperCase());
            descontos.add(headerMatcher.group(2).trim().toUpperCase());
        }

        if (startIndices.isEmpty()) {
            return List.of();
        }

        for (int i = 0; i < startIndices.size(); i++) {
            int inicio = startIndices.get(i);
            int fim = (i + 1 < startIndices.size()) ? startIndices.get(i + 1) : texto.length();
            String bloco = texto.substring(inicio, fim);

            String codigo = codigos.get(i);
            String desconto = descontos.get(i);

            LocalDate validoAte = extrairValidade(bloco, hoje);
            String compraMinima = extrairRegex(COMPRA_MINIMA_PATTERN, bloco);
            String descontoMaximo = extrairRegex(DESCONTO_MAX_PATTERN, bloco);
            String categoria = extrairCategoria(bloco);
            String link = extrairRegex(LINK_PATTERN, bloco);

            resultado.add(new CupomDetectado(
                    codigo,
                    desconto,
                    compraMinima,
                    descontoMaximo,
                    validoAte,
                    categoria,
                    link
            ));
        }

        return resultado;
    }

    /**
     * Extrai apenas os cupons que ainda não venceram em relação à data de referência.
     */
    public static List<CupomDetectado> extrairCuponsValidos(String texto, LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        return extrairCupons(texto, hoje).stream()
                .filter(cupom -> cupom.estaValido(hoje))
                .toList();
    }

    private static LocalDate extrairValidade(String bloco, LocalDate hoje) {
        Matcher matcher = VALIDADE_PATTERN.matcher(bloco);
        if (matcher.find()) {
            try {
                int dia = Integer.parseInt(matcher.group(1));
                int mes = Integer.parseInt(matcher.group(2));
                int ano = hoje.getYear();
                if (matcher.group(3) != null) {
                    String anoStr = matcher.group(3);
                    ano = anoStr.length() == 2 ? 2000 + Integer.parseInt(anoStr) : Integer.parseInt(anoStr);
                } else if (mes < hoje.getMonthValue() && (hoje.getMonthValue() - mes) > 6) {
                    // Se o mês for muito anterior ao mês atual e não especificou ano, pode ser do ano seguinte
                    ano = hoje.getYear() + 1;
                }
                return LocalDate.of(ano, mes, dia);
            } catch (Exception e) {
                log.debug("Erro ao parsear data de validade do cupom: {}", e.getMessage());
            }
        }
        return null;
    }

    private static String extrairCategoria(String bloco) {
        Matcher matcher = CATEGORIA_PATTERN.matcher(bloco);
        if (matcher.find()) {
            String cat = matcher.group(1).trim();
            if (!cat.isBlank() && !cat.contains("👉") && !cat.contains("OFF")) {
                return cat;
            }
        }
        return null;
    }

    private static String extrairRegex(Pattern pattern, String texto) {
        Matcher matcher = pattern.matcher(texto);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
