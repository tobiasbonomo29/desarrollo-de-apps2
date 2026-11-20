package com.mediconecta.seguridad;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ventana deslizante del limitador (login y gateway de IA). */
class LimitadorDeFrecuenciaTest {

    /** Reloj que el test adelanta a mano. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-19T12:00:00Z");

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final RelojManual reloj = new RelojManual();

    private final LimitadorDeFrecuencia limitador = new LimitadorDeFrecuencia(reloj);

    @Test
    void bloqueaAlLlegarAlMaximoYSeLiberaAlPasarLaVentana() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limitador.bloqueo("k", 3, Duration.ofMinutes(5)).isEmpty());
            limitador.registrar("k");
            reloj.avanzar(Duration.ofMinutes(1));
        }
        Duration espera = limitador.bloqueo("k", 3, Duration.ofMinutes(5)).orElseThrow();
        assertEquals(Duration.ofMinutes(2), espera, "el primer evento vence a los 5 minutos de ocurrido");

        reloj.avanzar(Duration.ofMinutes(2));
        assertTrue(limitador.bloqueo("k", 3, Duration.ofMinutes(5)).isEmpty());
    }

    @Test
    void consumirAnotaSoloSiHayLugar() {
        assertTrue(limitador.consumir("ia", 2, Duration.ofMinutes(1)).isEmpty());
        assertTrue(limitador.consumir("ia", 2, Duration.ofMinutes(1)).isEmpty());
        assertTrue(limitador.consumir("ia", 2, Duration.ofMinutes(1)).isPresent());
        reloj.avanzar(Duration.ofSeconds(61));
        assertTrue(limitador.consumir("ia", 2, Duration.ofMinutes(1)).isEmpty());
    }

    @Test
    void lasClavesSonIndependientesYSePuedenLimpiar() {
        limitador.registrar("a");
        limitador.registrar("a");
        assertTrue(limitador.bloqueo("a", 2, Duration.ofMinutes(1)).isPresent());
        assertTrue(limitador.bloqueo("b", 2, Duration.ofMinutes(1)).isEmpty());
        limitador.limpiar("a");
        assertTrue(limitador.bloqueo("a", 2, Duration.ofMinutes(1)).isEmpty());
    }
}
