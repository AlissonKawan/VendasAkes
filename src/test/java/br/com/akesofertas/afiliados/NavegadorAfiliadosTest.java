package br.com.akesofertas.afiliados;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NavegadorAfiliadosTest {
    @Test
    void fechaAbasRestauradasEConservaSomenteAPrincipal() {
        BrowserContext contexto = mock(BrowserContext.class);
        Page principal = mock(Page.class);
        Page excedente1 = mock(Page.class);
        Page excedente2 = mock(Page.class);
        var abertas = new ArrayList<>(java.util.List.of(principal, excedente1, excedente2));
        when(contexto.pages()).thenAnswer(ignored -> java.util.List.copyOf(abertas));
        doAnswer(ignored -> { abertas.remove(excedente1); return null; }).when(excedente1).close();
        doAnswer(ignored -> { abertas.remove(excedente2); return null; }).when(excedente2).close();

        int quantidade = NavegadorAfiliados.fecharAbasExcedentes(contexto, principal);

        assertEquals(1, quantidade);
        verify(principal, never()).close();
        verify(excedente1).close();
        verify(excedente2).close();
    }
}
