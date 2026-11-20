package com.mediconecta.negocio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Un timeout en cualquier punto de la cadena de causas se informa como 504. */
class IntegracionExternaExceptionTest {

    @Test
    void unTimeoutDeSocketEnvueltoSeDetecta() {
        RuntimeException envoltorio = new RuntimeException(new IOException(new SocketTimeoutException("Read timed out")));

        assertTrue(new IntegracionExternaException("no respondio", envoltorio).esPorTiempo());
    }

    @Test
    void unTimeoutDelClienteHttpSeDetecta() {
        assertTrue(IntegracionExternaException.fueTiempoAgotado(new HttpTimeoutException("timeout")));
    }

    @Test
    void otraFallaNoEsPorTiempo() {
        assertFalse(new IntegracionExternaException("conexion rechazada", new IOException("refused")).esPorTiempo());
        assertFalse(new IntegracionExternaException("sin causa").esPorTiempo());
        assertFalse(IntegracionExternaException.fueTiempoAgotado(null));
    }
}
