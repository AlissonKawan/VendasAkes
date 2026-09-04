package br.com.vendas.akes.vendasakes.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OfertaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveCadastrarEConsultarOferta() throws Exception {
        String json = ofertaJson("MLB-INTEGRACAO-1");

        mockMvc.perform(post("/api/ofertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.marketplace").value("MERCADO_LIVRE"))
                .andExpect(jsonPath("$.percentualDesconto").value(20.0))
                .andExpect(jsonPath("$.publicado").value(false));

        mockMvc.perform(get("/api/ofertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].marketplaceProductId").value("MLB-INTEGRACAO-1"));
    }

    @Test
    void deveRetornarConflitoAoCadastrarOfertaDuplicada() throws Exception {
        String json = ofertaJson("MLB-INTEGRACAO-2");

        mockMvc.perform(post("/api/ofertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/ofertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private String ofertaJson(String marketplaceProductId) {
        return """
                {
                  "marketplace": "MERCADO_LIVRE",
                  "marketplaceProductId": "%s",
                  "titulo": "Fone Bluetooth",
                  "precoOriginal": 250.00,
                  "precoAtual": 200.00,
                  "urlProduto": "https://produto.mercadolivre.com.br/%s",
                  "urlAfiliado": null,
                  "imagemUrl": "https://http2.mlstatic.com/imagem.jpg"
                }
                """.formatted(marketplaceProductId, marketplaceProductId);
    }
}
