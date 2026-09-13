package br.com.akesofertas.provider;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Set;

/** Política conservadora: confirma o preço da buy box para uma unidade no marketplace. */
final class MercadoLivrePrecoOferta {
    private MercadoLivrePrecoOferta() {}

    static Preco confirmar(JsonNode vencedor, JsonNode resposta, Instant agora) {
        BigDecimal atual = numero(vencedor.path("price"));
        String moeda = vencedor.path("currency_id").asText("");
        if (atual == null || atual.signum() <= 0 || !"BRL".equals(moeda)) return null;
        var correspondentes = new ArrayList<JsonNode>();
        for (var preco : resposta.path("prices")) {
            BigDecimal valor = numero(preco.path("amount"));
            if (valor != null && atual.compareTo(valor) == 0
                    && moeda.equals(preco.path("currency_id").asText())
                    && Set.of("standard", "promotion").contains(preco.path("type").asText(""))
                    && !preco.path("id").asText("").isBlank() && aplicavel(preco, agora)) {
                correspondentes.add(preco);
            }
        }
        // Não escolhe min(amount), nem decide entre dois preços com o mesmo valor e condições distintas.
        if (correspondentes.size() != 1) return null;
        var escolhido = correspondentes.getFirst();
        BigDecimal regular = numero(escolhido.path("regular_amount"));
        BigDecimal original = numero(vencedor.path("original_price"));
        if (!opcionalValido(escolhido.path("regular_amount"), regular, atual)
                || !opcionalValido(vencedor.path("original_price"), original, atual)) return null;
        if (regular != null && original != null && regular.compareTo(original) != 0) return null;
        // Ambos são campos oficiais do mesmo anúncio. Um preço standard separado não é preço anterior.
        if (regular == null) regular = original;
        BigDecimal desconto = regular == null || regular.compareTo(atual) <= 0 ? null
                : regular.subtract(atual).multiply(BigDecimal.valueOf(100)).divide(regular, 2, RoundingMode.HALF_UP);
        boolean promocional = "promotion".equals(escolhido.path("type").asText()) || desconto != null;
        return new Preco(atual, regular, original, moeda, promocional, desconto, escolhido.path("id").asText());
    }

    private static boolean opcionalValido(JsonNode campo, BigDecimal valor, BigDecimal atual) {
        return campo.isMissingNode() || campo.isNull() || (valor != null && valor.compareTo(atual) >= 0);
    }

    private static boolean aplicavel(JsonNode preco, Instant agora) {
        var condicoes = preco.path("conditions");
        var contextos = condicoes.path("context_restrictions");
        if (!condicoes.isObject() || !contextos.isArray()) return false;
        // Condições desconhecidas exigem revisão do contrato, em vez de presumir elegibilidade.
        for (var campo : condicoes.properties()) {
            if (!Set.of("context_restrictions", "start_time", "end_time", "min_purchase_unit", "eligible")
                    .contains(campo.getKey())) return false;
        }
        for (var contexto : contextos) {
            if (!contexto.isString() || !"channel_marketplace".equals(contexto.asText())) return false;
        }
        var minimo = condicoes.path("min_purchase_unit");
        if (!minimo.isMissingNode() && !minimo.isNull()
                && (!minimo.isIntegralNumber() || minimo.longValue() != 1)) return false;
        var elegivel = condicoes.path("eligible");
        if (!elegivel.isMissingNode() && (!elegivel.isBoolean() || !elegivel.booleanValue())) return false;
        if ("net".equals(preco.path("amount_tax_inclusion_type").asText())) return false;
        // Datas confidenciais podem não vir para terceiros. A igualdade com a buy box confirma o valor atual;
        // qualquer janela de validade explícita precisa também estar vigente.
        return dentroDaJanela(condicoes.path("start_time"), agora, true)
                && dentroDaJanela(condicoes.path("end_time"), agora, false);
    }

    private static boolean dentroDaJanela(JsonNode data, Instant agora, boolean inicio) {
        if (data.isMissingNode() || data.isNull()) return true;
        if (!data.isString()) return false;
        try {
            Instant instante = Instant.parse(data.asText());
            return inicio ? !agora.isBefore(instante) : agora.isBefore(instante);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    static BigDecimal numero(JsonNode campo) { return campo.isNumber() ? campo.decimalValue() : null; }

    record Preco(BigDecimal atual, BigDecimal regular, BigDecimal originalCatalogo, String moeda,
                 boolean promocional, BigDecimal desconto, String id) {}
}
