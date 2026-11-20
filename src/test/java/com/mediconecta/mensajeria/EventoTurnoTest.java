package com.mediconecta.mensajeria;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contrato JSON versionado del evento "turno confirmado". */
class EventoTurnoTest {

    private Turno turno;

    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-19T13:05:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void preparar() {
        Paciente ana = new Paciente("Ana Gomez", "30111222");
        ana.setId(7L);
        Profesional laura = new Profesional("Dra. Laura Fernandez", "Cardiologia", "MP-10234");
        laura.setId(3L);
        turno = new Turno();
        turno.setId(42L);
        turno.setPaciente(ana);
        turno.setProfesional(laura);
        turno.setEspecialidad("Cardiologia");
        turno.setFechaHora(LocalDateTime.of(2026, 11, 24, 9, 30));
    }

    @Test
    void idaYVueltaPorJson() {
        EventoTurno original = EventoTurno.turnoConfirmado(turno, reloj);
        EventoTurno leido = EventoTurno.desdeJson(original.aJson());

        assertEquals(original.eventoId(), leido.eventoId());
        assertEquals(EventoTurno.TIPO_TURNO_CONFIRMADO, leido.tipo());
        assertEquals(EventoTurno.VERSION, leido.version());
        assertEquals(Instant.parse("2026-11-19T13:05:00Z"), leido.ocurridoEn());
        assertEquals(42L, leido.turnoId());
        assertEquals(7L, leido.pacienteId());
        assertEquals(3L, leido.profesionalId());
        assertEquals("Cardiologia", leido.especialidad());
        assertEquals(LocalDateTime.of(2026, 11, 24, 9, 30), leido.fechaHora());
    }

    @Test
    void cadaEventoTieneSuPropioId() {
        assertNotEquals(EventoTurno.turnoConfirmado(turno, reloj).eventoId(),
                EventoTurno.turnoConfirmado(turno, reloj).eventoId());
    }

    @Test
    void elMensajeNoLlevaDatosPersonales() {
        String json = EventoTurno.turnoConfirmado(turno, reloj).aJson();

        assertFalse(json.contains("Ana Gomez"));
        assertFalse(json.contains("30111222"));
        assertTrue(json.contains("\"turnoId\":42"));
    }

    @Test
    void textoQueNoEsJsonEsUnMensajeInvalido() {
        assertThrows(MensajeInvalidoException.class, () -> EventoTurno.desdeJson("CONFIRMACION|Turno 5: Ana"));
        assertThrows(MensajeInvalidoException.class, () -> EventoTurno.desdeJson(""));
        assertThrows(MensajeInvalidoException.class, () -> EventoTurno.desdeJson(null));
        assertThrows(MensajeInvalidoException.class, () -> EventoTurno.desdeJson("[1, 2]"));
    }

    @Test
    void unaVersionFuturaSeRechaza() {
        String json = EventoTurno.turnoConfirmado(turno, reloj).aJson().replace("\"version\":1", "\"version\":2");

        MensajeInvalidoException error = assertThrows(MensajeInvalidoException.class, () -> EventoTurno.desdeJson(json));
        assertTrue(error.getMessage().contains("Version"));
    }

    @Test
    void otroTipoDeEventoSeRechaza() {
        String json = EventoTurno.turnoConfirmado(turno, reloj).aJson().replace("TURNO_CONFIRMADO", "TURNO_BORRADO");

        assertThrows(MensajeInvalidoException.class, () -> EventoTurno.desdeJson(json));
    }

    @Test
    void faltaUnCampoObligatorio() {
        String json = EventoTurno.turnoConfirmado(turno, reloj).aJson().replace("\"turnoId\":42,", "");

        assertThrows(MensajeInvalidoException.class, () -> EventoTurno.desdeJson(json));
    }

    @Test
    void unaFechaMalFormadaSeRechaza() {
        String json = EventoTurno.turnoConfirmado(turno, reloj).aJson().replace("2026-11-24T09:30", "24/11/2026");

        assertThrows(MensajeInvalidoException.class, () -> EventoTurno.desdeJson(json));
    }
}
