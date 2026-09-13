package br.com.akesofertas.cupons.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converte apenas os padrões de condições já comprovados em respostas reais do portal. */
public final class CondicoesCupomParser {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String MONEY = "(\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?)(?!\\d|[.,]\\d)";
    private static final Pattern TEXTUAL_ID = Pattern.compile("\\bID\\s+(\\d+)\\s*-", FLAGS);
    private static final Pattern DATES = Pattern.compile(
            "Cupom\\s+válido\\s+no\\s+Brasil,\\s+de\\s+(\\d{2}/\\d{2}/\\d{4})"
                    + "\\s+a\\s+(\\d{2}/\\d{2}/\\d{4})", FLAGS);
    private static final Pattern MINIMUM_PURCHASE = Pattern.compile(
            "valor\\s+seja\\s+igual\\s+ou\\s+superior\\s+a\\s+R\\$\\s*" + MONEY, FLAGS);
    private static final Pattern PERCENT_DISCOUNT = Pattern.compile(
            "O\\s+cupom\\s+consiste\\s+em\\s+(\\d+(?:,\\d+)?)%\\s+de\\s+desconto", FLAGS);
    private static final Pattern FIXED_DISCOUNT = Pattern.compile(
            "O\\s+cupom\\s+consiste\\s+em\\s+um\\s+desconto\\s+total\\s+de\\s+R\\$\\s*" + MONEY,
            FLAGS);
    private static final Pattern MAXIMUM_DISCOUNT = Pattern.compile(
            "Máximo\\s+de\\s+desconto\\s+de\\s+R\\$\\s*" + MONEY, FLAGS);
    private static final Pattern USES_PER_CPF = Pattern.compile(
            "limitado\\s+a\\s+(\\d+)\\s*(?:\\([^)]*\\))?\\s+usos?\\s+por\\s+CPF", FLAGS);
    private static final Pattern AVAILABLE_STOCK = Pattern.compile(
            "estoque\\s+de\\s+(\\d+)\\s+cupons", FLAGS);
    private static final Pattern AUTOMATIC_APPLICATION = Pattern.compile(
            "O\\s+cupom\\s+será\\s+aplicado\\s+automaticamente\\s+no\\s+carrinho\\s+elegível", FLAGS);
    private static final Pattern CUMULATIVE = Pattern.compile(
            "Cupom\\s+acumulável\\s+com\\s+outros\\s+descontos", FLAGS);
    private static final DateTimeFormatter BRAZILIAN_DATE = DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Usa {@code expectedCouponId} como identidade da consulta. Quando o texto também traz um ID,
     * ele serve apenas para validar que a resposta corresponde à campanha esperada.
     */
    public CondicoesCupom parse(long expectedCouponId, String rawTerms) {
        validarEntrada(expectedCouponId, rawTerms);
        validarCouponId(expectedCouponId, rawTerms);
        LocalDate[] dates = extrairDatas(rawTerms);

        return new CondicoesCupom(
                expectedCouponId,
                dates == null ? null : dates[0],
                dates == null ? null : dates[1],
                extrairDinheiro(MINIMUM_PURCHASE, rawTerms),
                extrairPercentual(rawTerms),
                extrairDinheiro(FIXED_DISCOUNT, rawTerms),
                extrairDinheiro(MAXIMUM_DISCOUNT, rawTerms),
                extrairInteiro(USES_PER_CPF, rawTerms),
                extrairInteiro(AVAILABLE_STOCK, rawTerms),
                extrairPresenca(AUTOMATIC_APPLICATION, rawTerms),
                extrairPresenca(CUMULATIVE, rawTerms),
                rawTerms);
    }

    static BigDecimal parseBrazilianMoney(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("valor monetário é obrigatório");
        }
        if (!value.matches("(?:\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:,\\d{1,2})?")) {
            throw new IllegalArgumentException("valor monetário brasileiro inválido");
        }
        return new BigDecimal(value.replace(".", "").replace(',', '.'));
    }

    private static void validarEntrada(long expectedCouponId, String rawTerms) {
        if (expectedCouponId <= 0) throw new IllegalArgumentException("expectedCouponId deve ser positivo");
        if (rawTerms == null || rawTerms.isBlank()) {
            throw new IllegalArgumentException("rawTerms é obrigatório");
        }
    }

    private static void validarCouponId(long expectedCouponId, String text) {
        Matcher matcher = TEXTUAL_ID.matcher(text);
        if (!matcher.find()) return;
        final long textualId;
        try {
            textualId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new CondicoesCupomParsingException("couponId textual inválido");
        }
        if (textualId != expectedCouponId) {
            throw new CondicoesCupomParsingException(
                    "couponId textual não corresponde ao couponId consultado");
        }
    }

    private static LocalDate[] extrairDatas(String text) {
        Matcher matcher = DATES.matcher(text);
        if (!matcher.find()) return null;
        try {
            return new LocalDate[]{
                    LocalDate.parse(matcher.group(1), BRAZILIAN_DATE),
                    LocalDate.parse(matcher.group(2), BRAZILIAN_DATE)};
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static BigDecimal extrairDinheiro(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return null;
        try {
            return parseBrazilianMoney(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static BigDecimal extrairPercentual(String text) {
        Matcher matcher = PERCENT_DISCOUNT.matcher(text);
        if (!matcher.find()) return null;
        try {
            return new BigDecimal(matcher.group(1).replace(',', '.'));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer extrairInteiro(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return null;
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Boolean extrairPresenca(Pattern pattern, String text) {
        return pattern.matcher(text).find() ? Boolean.TRUE : null;
    }
}
