package br.com.akesofertas.afiliados;

import java.util.List;

/** Formato observado no Gerador de Links: {"urls": ["..."], "tag": "telegram"}. */
public record AffiliateLinkRequest(List<String> urls, String tag) {}
