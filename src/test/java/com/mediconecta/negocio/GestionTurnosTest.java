package com.mediconecta.negocio;

import com.mediconecta.datos.memoria.RepositorioTurnosEnMemoria;
import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;
import com.mediconecta.modelo.estado.TransicionInvalidaException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de la agenda en {@link GestionTurnos}, sin contenedor ni base de
 * datos: el repositorio es la implementacion en memoria.
 */
class GestionTurnosTest {

    private GestionTurnos gestion;

    private Paciente ana;

    private Profesional laura;

    private String manana;

    @BeforeEach
    void preparar() {
        gestion = new GestionTurnos();
        Inyeccion.campo(gestion, "repositorio", new RepositorioTurnosEnMemoria());
        Inyeccion.campo(gestion, "fabricaTurnos", new FabricaTurnos());
        comoUsuario("recepcion", Roles.RECEPCION);

        ana = new Paciente("Ana Gomez", "30111222");
        ana.setId(1L);
        ana.setUsuario("paciente");

        laura = new Profesional("Dra. Laura Fernandez", "Cardiologia", "MP-10234");
        laura.setId(1L);
        laura.setUsuario("medico");

        manana = LocalDate.now().plusDays(1).atTime(9, 30).format(Turno.FORMATO_FECHA_HORA);
    }

    private void comoUsuario(String usuario, String... roles) {
        Inyeccion.campo(gestion, "contexto", Inyeccion.llamador(usuario, roles));
    }

    private Turno agendarManana() {
        return gestion.agendar(ana, laura, "Cardiologia", manana, "Control");
    }

    @Test
    void unTurnoNuevoNaceProgramadoYConId() {
        Turno turno = agendarManana();
        assertNotNull(turno.getId());
        assertEquals(EstadoTurno.PROGRAMADO, turno.getEstado());
        assertFalse(turno.isArchivado());
    }

    @Test
    void noSePuedeAgendarEnElPasado() {
        String ayer = LocalDateTime.now().minusDays(1).format(Turno.FORMATO_FECHA_HORA);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> gestion.agendar(ana, laura, "Cardiologia", ayer, null));
        assertTrue(e.getMessage().contains("pasado"));
    }

    @Test
    void noSePuedeSuperponerConOtroTurnoDelMismoProfesional() {
        agendarManana();
        assertThrows(ReglaNegocioException.class, this::agendarManana);
    }

    @Test
    void cancelarLiberaElHorario() {
        Turno turno = agendarManana();
        gestion.cancelarTurno(turno.getId());

        Turno otro = agendarManana();
        assertEquals(EstadoTurno.PROGRAMADO, otro.getEstado());
    }

    @Test
    void cancelarDosVecesEsUnaTransicionInvalida() {
        Turno turno = agendarManana();
        gestion.cancelarTurno(turno.getId());
        assertThrows(TransicionInvalidaException.class, () -> gestion.cancelarTurno(turno.getId()));
    }

    @Test
    void unPacienteNoPuedeCancelarTurnosAjenos() {
        Turno turno = agendarManana();
        comoUsuario("otro-paciente", Roles.PACIENTE);
        assertThrows(ReglaNegocioException.class, () -> gestion.cancelarTurno(turno.getId()));
        comoUsuario("recepcion", Roles.RECEPCION);
        assertEquals(EstadoTurno.PROGRAMADO, gestion.obtenerTurno(turno.getId()).getEstado());
    }

    @Test
    void unPacientePuedeCancelarSuPropioTurno() {
        Turno turno = agendarManana();
        comoUsuario("paciente", Roles.PACIENTE);
        assertEquals(EstadoTurno.CANCELADO, gestion.cancelarTurno(turno.getId()).getEstado());
    }

    @Test
    void elMedicoAtiendeTurnosDeSuAgendaYNoDeOtra() {
        Turno turno = agendarManana();

        comoUsuario("otro-medico", Roles.MEDICO);
        assertThrows(ReglaNegocioException.class, () -> gestion.atenderTurno(turno.getId()));

        comoUsuario("medico", Roles.MEDICO);
        assertEquals(EstadoTurno.ATENDIDO, gestion.atenderTurno(turno.getId()).getEstado());
    }

    @Test
    void unTurnoAtendidoNoSePuedeCancelar() {
        Turno turno = agendarManana();
        comoUsuario("medico", Roles.MEDICO);
        gestion.atenderTurno(turno.getId());

        comoUsuario("recepcion", Roles.RECEPCION);
        assertThrows(TransicionInvalidaException.class, () -> gestion.cancelarTurno(turno.getId()));
    }

    @Test
    void soloSeArchivanTurnosCanceladosYUnaSolaVez() {
        Turno turno = agendarManana();
        assertThrows(TransicionInvalidaException.class, () -> gestion.archivarTurno(turno.getId()));

        gestion.cancelarTurno(turno.getId());
        assertTrue(gestion.archivarTurno(turno.getId()).isArchivado());
        assertFalse(gestion.listarTurnos().stream().anyMatch(t -> t.getId().equals(turno.getId())));

        assertThrows(TransicionInvalidaException.class, () -> gestion.archivarTurno(turno.getId()));
    }

    @Test
    void unTurnoInexistenteDaNoEncontrado() {
        assertThrows(NoEncontradoException.class, () -> gestion.obtenerTurno(999L));
    }

    @Test
    void unPacienteSoloPuedeConsultarSusPropiosTurnos() {
        Turno turno = agendarManana();

        comoUsuario("paciente", Roles.PACIENTE);
        assertEquals(turno.getId(), gestion.obtenerTurno(turno.getId()).getId());

        comoUsuario("otro-paciente", Roles.PACIENTE);
        assertThrows(ReglaNegocioException.class, () -> gestion.obtenerTurno(turno.getId()),
                "sin este control un paciente podria leer turnos ajenos recorriendo ids");

        comoUsuario("medico", Roles.MEDICO);
        assertEquals(turno.getId(), gestion.obtenerTurno(turno.getId()).getId());
    }

    @Test
    void conIfMatchDeUnaVersionViejaNoSeModificaElTurno() {
        Turno turno = agendarManana();

        assertThrows(VersionDesactualizadaException.class,
                () -> gestion.cancelarTurno(turno.getId(), turno.getVersion() + 1));
        assertEquals(EstadoTurno.PROGRAMADO, gestion.obtenerTurno(turno.getId()).getEstado());

        assertEquals(EstadoTurno.CANCELADO, gestion.cancelarTurno(turno.getId(), turno.getVersion()).getEstado());
        assertThrows(VersionDesactualizadaException.class, () -> gestion.archivarTurno(turno.getId(), 99L));
        assertTrue(gestion.archivarTurno(turno.getId(), null).isArchivado());
    }

    @Test
    void atenderTambienRespetaLaVersion() {
        Turno turno = agendarManana();
        comoUsuario("medico", Roles.MEDICO);
        assertThrows(VersionDesactualizadaException.class, () -> gestion.atenderTurno(turno.getId(), 42L));
        assertEquals(EstadoTurno.ATENDIDO, gestion.atenderTurno(turno.getId(), turno.getVersion()).getEstado());
    }
}
