package br.com.akesofertas.afiliados;
import org.springframework.stereotype.Component;
@Component
public class DummyAffiliateLinkClient implements AffiliateLinkClient {
    @Override
    public AffiliateLinkResponse criarLink(AffiliateLinkRequest pedido) {
        throw new UnsupportedOperationException("Mocked");
    }
}
