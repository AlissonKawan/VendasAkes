package br.com.akesofertas.afiliados;

import tools.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import java.net.URI;
import java.util.Map;

public class MercadoLivreAffiliateClient implements AffiliateLinkClient {
    public static final URI ENDPOINT = URI.create(
            "https://www.mercadolivre.com.br/affiliate-program/api/v2/affiliates/createLink");
    
    private final Page pagina;
    private final ObjectMapper json = new ObjectMapper();

    public MercadoLivreAffiliateClient(Page pagina) {
        this.pagina = pagina;
    }

    @Override
    public AffiliateLinkResponse criarLink(AffiliateLinkRequest pedido) {
        try {
            String corpoRequest = json.writeValueAsString(pedido);
            
            // Fazemos fetch interno, o ML lida com a sessão e headers
            Object responseObj = pagina.evaluate("""
                async (bodyStr) => {
                    const res = await fetch('%s', {
                        method: 'POST',
                        credentials: 'same-origin',
                        headers: {
                            'Content-Type': 'application/json',
                            'Accept': 'application/json'
                        },
                        body: bodyStr
                    });
                    
                    if (res.status === 401 || res.status === 403 || res.redirected) {
                        return { error: 'UNAUTHORIZED' };
                    }
                    
                    if (!res.ok) {
                        return { error: 'HTTP_' + res.status };
                    }
                    
                    const text = await res.text();
                    try {
                        return JSON.parse(text);
                    } catch(e) {
                        return { error: 'NOT_JSON' };
                    }
                }
            """.formatted(ENDPOINT.toString()), corpoRequest);
            
            if (responseObj instanceof Map<?, ?> map) {
                if (map.containsKey("error")) {
                    String err = (String) map.get("error");
                    if ("UNAUTHORIZED".equals(err)) {
                        throw new IllegalStateException("SESSAO_AFILIADOS_EXPIRADA");
                    }
                    throw new AffiliateLinkException("Falha ao consultar createLink: " + err);
                }
                
                String rawJson = json.writeValueAsString(responseObj);
                return json.readValue(rawJson, AffiliateLinkResponse.class);
            }
            throw new AffiliateLinkException("Resposta inválida do fetch no evaluate.");
            
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new AffiliateLinkException("Erro ao criar link no Hub: " + e.getMessage());
        }
    }
}
