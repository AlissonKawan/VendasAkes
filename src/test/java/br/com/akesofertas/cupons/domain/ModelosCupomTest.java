package br.com.akesofertas.cupons.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelosCupomTest {

    @Test
    void sanitizacaoCodigoRejeitaTokensInternos() {
        assertNull(MercadoLivreCupom.sanitizarCodigoExibivel("eyJhY3Rpb24iOiJhdXRvLWNsaW1i..."));
        assertNull(MercadoLivreCupom.sanitizarCodigoExibivel("YWJjZGVmZ2hpamsxbW5vcHFyc3R1dnd4eXo="));
        assertEquals("MELI20", MercadoLivreCupom.sanitizarCodigoExibivel("meli20"));
    }

    @Test
    void condicoesPreservamTextoEPermitemCamposNaoReconhecidos() {
        String texto = "Nova regra ainda não reconhecida pelo parser.";
        var condicoes = new CondicoesCupom(14090458L, LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 9, 10), null, new BigDecimal("20"), null,
                new BigDecimal("100"), 1, 3157, true, true, texto);

        assertEquals(texto, condicoes.rawTerms());
        assertNull(condicoes.minimumPurchase());
        assertNull(condicoes.fixedDiscount());
    }

    @Test
    void produtoMantemItemIdExatoDoAnuncio() {
        var produto = new ProdutoElegivelCupom(13558453L, "MLB4812130742", "Produto",
                "https://produto.mercadolivre.com.br/exemplo?wid=MLB4812130742");

        assertEquals("MLB4812130742", produto.itemId());
        assertEquals(13558453L, produto.couponId());
    }
}
