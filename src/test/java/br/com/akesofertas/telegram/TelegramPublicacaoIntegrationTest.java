package br.com.akesofertas.telegram;

import br.com.akesofertas.afiliados.AffiliateLinkService;
import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.afiliados.ResultadoLinkAfiliado;
import br.com.akesofertas.afiliados.rejeicao.OfertaAfiliadoRejeitadaService;
import br.com.akesofertas.publicacao.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest
class TelegramPublicacaoIntegrationTest {
    @Autowired OfertaPublicadaService db;
    @Autowired SpringDataOfertaPublicadaRepository repo;

    @ParameterizedTest
    @CsvSource({"404,false,ERRO_ENVIO", "401,false,ERRO_ENVIO", "200,true,ENVIADA", "200,false,ERRO_ENVIO"})
    void respostaHttpDeterminaStatusPersistido(int status, boolean ok, StatusPublicacao esperado) {
        String item = "MLB" + UUID.randomUUID();
        String url = "https://produto.mercadolivre.com.br/" + item;
        var oferta = new OfertaAfiliado(item, null, "Oferta teste", null, BigDecimal.TEN, null, null, null, url);
        var links = mock(AffiliateLinkService.class);
        when(links.gerarResultados(anyList())).thenReturn(java.util.List.of(
                new ResultadoLinkAfiliado(url, true, "https://meli.la/teste", true, null, null)));
        var http = new RestTemplate();
        var server = MockRestServiceServer.bindTo(http).build();
        server.expect(request -> {
            // O registro já está persistido quando o transporte recebe o pedido.
            var antes = repo.findAll().stream().filter(e -> item.equals(e.toDomain().itemId())).findFirst().orElseThrow();
            assertEquals(StatusPublicacao.PENDENTE_ENVIO, antes.getStatus());
            assertNull(antes.getDataEnvio());
        }).andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
                .body("{\"ok\":" + ok + ",\"result\":{\"message_id\":1}}"));
        var telegram = new TelegramService("123:FAKE_TEST", "@test", new MensagemOfertaService(), http);
        try {
            new PublicadorOfertaService(db, links, telegram, mock(OfertaAfiliadoRejeitadaService.class))
                    .processarOferta("MERCADO_LIVRE", oferta);
            var depois = repo.findAll().stream().filter(e -> item.equals(e.toDomain().itemId())).findFirst().orElseThrow();
            assertEquals(esperado, depois.getStatus());
            if (esperado == StatusPublicacao.ENVIADA) assertNotNull(depois.getDataEnvio());
            else {
                assertNull(depois.getDataEnvio());
                assertNotNull(depois.toDomain().mensagemErro());
                assertFalse(depois.toDomain().mensagemErro().contains("123:FAKE_TEST"));
            }
            server.verify();
        } finally {
            repo.findAll().stream().filter(e -> item.equals(e.toDomain().itemId())).forEach(repo::delete);
        }
    }
}
