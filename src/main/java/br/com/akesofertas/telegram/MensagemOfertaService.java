package br.com.akesofertas.telegram;

import br.com.akesofertas.publicacao.OfertaPublicada;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Locale;

@Service
public class MensagemOfertaService {

    public String montarMensagem(OfertaPublicada oferta) {
        StringBuilder sb = new StringBuilder();

        if (oferta.desconto() != null && !oferta.desconto().isBlank()) {
            sb.append("🔥 <b>").append(html(oferta.desconto())).append("</b>\n\n");
        }

        sb.append("💡 <b>").append(html(resumir(oferta.titulo(), 700))).append("</b>\n\n");

        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        
        if (oferta.precoAnterior() != null) {
            sb.append("❌ De: <s>").append(nf.format(oferta.precoAnterior())).append("</s>\n");
        }
        
        sb.append("✅ Por: <b>").append(nf.format(oferta.precoAtual())).append("</b>\n");

        if (oferta.cupom() != null && oferta.cupom().temCodigoExibivel()) {
            String codigo = oferta.cupom().codigoExibivel().replaceFirst("^#+", "");
            sb.append("\n🎟️ Cupom: ").append(html(codigo)).append("\n");
        }

        if (oferta.destaque() != null && !oferta.destaque().isBlank()) {
            sb.append("\n🏆 ").append(html(oferta.destaque())).append("\n");
        }

        if (oferta.cupom() != null) {
            var cupom = oferta.cupom();
            if (!cupom.temCodigoExibivel()) {
                String tituloCupom = cupom.title() == null || cupom.title().isBlank()
                        ? "Cupom oficial" : resumir(cupom.title().trim(), 160);
                sb.append("\n🎟️ Cupom oficial: <b>").append(html(tituloCupom)).append("</b>\n");
                sb.append("🔖 Campanha: <code>").append(cupom.couponId()).append("</code>\n");
                sb.append("👉 Ative o cupom no Mercado Livre\n\n");
            } else {
                sb.append('\n');
            }
            sb.append("💸 ");
            if (cupom.condicoes() != null && cupom.condicoes().discountPercent() != null) {
                sb.append(cupom.condicoes().discountPercent().stripTrailingZeros().toPlainString().replace('.', ','))
                        .append("% OFF\n\n");
            } else {
                sb.append(nf.format(cupom.descontoAplicado())).append(" OFF\n\n");
            }
            if (cupom.compraMinima() != null && cupom.compraMinima().signum() > 0) {
                sb.append("📦 Compra mínima: ").append(nf.format(cupom.compraMinima())).append("\n\n");
            }
            if ("PIX_PLUS_COUPON".equals(cupom.paymentContext())) {
                sb.append("🔥 <b>").append(nf.format(cupom.precoEstimado()))
                        .append(" no Pix + cupom</b>\n");
            } else {
                sb.append("🔥 Com cupom: <b>").append(nf.format(cupom.precoEstimado())).append("</b>\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    private static String html(String valor) {
        if (valor == null) return "";
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String resumir(String valor, int limite) {
        if (valor == null || valor.length() <= limite) return valor;
        return valor.substring(0, limite - 1) + "…";
    }
}

