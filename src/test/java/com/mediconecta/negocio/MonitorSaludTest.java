package com.mediconecta.negocio;

import com.mediconecta.datos.SondaBaseDeDatos;
import com.mediconecta.mensajeria.SondaMensajeria;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Health check: UP solo si responden la base y el broker. */
class MonitorSaludTest {

    private static MonitorSalud monitor(SondaBaseDeDatos base, boolean mensajeria) {
        MonitorSalud monitor = new MonitorSalud();
        Inyeccion.campo(monitor, "sondaBaseDeDatos", base);
        Inyeccion.campo(monitor, "sondaMensajeria", new SondaMensajeria() {
            @Override
            public boolean responde() {
                return mensajeria;
            }
        });
        return monitor;
    }

    @Test
    void todoRespondeEstaArriba() {
        EstadoSalud estado = monitor(() -> true, true).verificar();

        assertTrue(estado.estaArriba());
        assertEquals(Map.of("baseDeDatos", "UP", "mensajeria", "UP"), estado.getComponentes());
        Instant.parse(estado.getVerificadoEn());
    }

    @Test
    void sinBaseDeDatosEstaAbajoYDiceQueComponenteFallo() {
        EstadoSalud estado = monitor(() -> false, true).verificar();

        assertFalse(estado.estaArriba());
        assertEquals(EstadoSalud.DOWN, estado.getEstado());
        assertEquals("DOWN", estado.getComponentes().get("baseDeDatos"));
        assertEquals("UP", estado.getComponentes().get("mensajeria"));
    }

    @Test
    void unaSondaQueLanzaExcepcionCuentaComoCaida() {
        EstadoSalud estado = monitor(() -> {
            throw new IllegalStateException("sin conexion");
        }, false).verificar();

        assertEquals("DOWN", estado.getComponentes().get("baseDeDatos"));
        assertEquals("DOWN", estado.getComponentes().get("mensajeria"));
    }
}
