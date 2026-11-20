package com.mediconecta.negocio;

import com.mediconecta.modelo.EstadoTurno;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El asistente de reserva {@code @Stateful}: recuerda cada paso y valida el orden. */
class AsistenteReservaTurnoTest {

    private Escenario escenario;

    private AsistenteReservaTurno asistente;

    private LocalDate dia;

    @BeforeEach
    void preparar() {
        escenario = new Escenario();
        asistente = escenario.asistente();
        dia = Escenario.proximoDiaHabil();
    }

    private String horario(int hora, int minuto) {
        return dia.atTime(hora, minuto).toString();
    }

    private void hastaElegirHorario() {
        asistente.iniciar("30111222");
        asistente.elegirEspecialidad("Cardiologia");
        asistente.elegirProfesional(escenario.laura.getId());
    }

    @Test
    void empiezaSinIniciarYConSuPropiaConversacion() {
        EstadoReserva estado = asistente.estado();

        assertEquals("SIN_INICIAR", estado.getPasoActual());
        assertNotNull(estado.getIdConversacion());
        assertNotNull(estado.getIniciadaEn());
        assertFalse(estado.isFinalizada());
    }

    @Test
    void elFlujoCompletoCreaUnTurno() {
        assertEquals("ELEGIR_ESPECIALIDAD", asistente.iniciar("30111222").getPasoActual());
        assertEquals("ELEGIR_PROFESIONAL", asistente.elegirEspecialidad(" Cardiologia ").getPasoActual());
        assertEquals("ELEGIR_HORARIO", asistente.elegirProfesional(escenario.laura.getId()).getPasoActual());

        List<LocalDateTime> libres = asistente.horariosDisponibles(dia);
        assertEquals(8, libres.size(), "de 9 a 13 cada 30 minutos");

        EstadoReserva aConfirmar = asistente.elegirHorario(horario(9, 30));
        assertEquals("CONFIRMAR", aConfirmar.getPasoActual());
        assertEquals("Ana Gomez", aConfirmar.getNombrePaciente());
        assertEquals("Dra. Laura Fernandez", aConfirmar.getNombreProfesional());

        EstadoReserva cerrado = asistente.confirmar();
        assertTrue(cerrado.isFinalizada());
        assertEquals("FINALIZADA", cerrado.getPasoActual());
        assertEquals(EstadoTurno.PROGRAMADO, escenario.turnos.buscarPorId(cerrado.getTurnoId()).orElseThrow().getEstado());
        assertEquals(1, escenario.notificados.size(), "confirmar pasa por la fachada y avisa por JMS");
        assertDoesNotThrow(asistente::finalizar);
    }

    @Test
    void noSePuedeSaltearUnPaso() {
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirEspecialidad("Cardiologia"));
        asistente.iniciar("30111222");
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirProfesional(escenario.laura.getId()));
        assertThrows(ReglaNegocioException.class, () -> asistente.confirmar());
    }

    @Test
    void validaLaEspecialidad() {
        asistente.iniciar("30111222");
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirEspecialidad("Astrologia"));
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirEspecialidad(" "));
    }

    @Test
    void elProfesionalTieneQueSerDeLaEspecialidadElegida() {
        asistente.iniciar("30111222");
        asistente.elegirEspecialidad("Cardiologia");

        assertThrows(ReglaNegocioException.class, () -> asistente.elegirProfesional(escenario.martin.getId()));
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirProfesional(null));
    }

    @Test
    void soloSeAceptaUnHorarioLibre() {
        hastaElegirHorario();

        assertThrows(ReglaNegocioException.class, () -> asistente.elegirHorario(horario(15, 0)), "fuera de agenda");
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirHorario(""));
    }

    @Test
    void volverOlvidaLoElegidoDespues() {
        hastaElegirHorario();

        EstadoReserva atras = asistente.volver();
        assertEquals("ELEGIR_PROFESIONAL", atras.getPasoActual());
        assertNull(atras.getProfesionalId());
        assertEquals("Cardiologia", atras.getEspecialidad());

        EstadoReserva otraVez = asistente.volver();
        assertEquals("ELEGIR_ESPECIALIDAD", otraVez.getPasoActual());
        assertNull(otraVez.getEspecialidad());

        assertThrows(ReglaNegocioException.class, () -> asistente.volver(), "ya esta en el primer paso");
    }

    @Test
    void noSePuedeVolverDespuesDeConfirmar() {
        hastaElegirHorario();
        asistente.elegirHorario(horario(10, 0));
        asistente.confirmar();

        assertThrows(ReglaNegocioException.class, () -> asistente.volver());
        assertThrows(ReglaNegocioException.class, () -> asistente.confirmar(), "no se duplica el turno");
    }

    @Test
    void sinProfesionalNoHayHorarios() {
        assertThrows(ReglaNegocioException.class, () -> asistente.horariosDisponibles(dia));
    }

    @Test
    void unPacienteSoloPuedeReservarParaSiMismo() {
        escenario.comoUsuario("paciente", Roles.PACIENTE);

        assertEquals("ELEGIR_ESPECIALIDAD", asistente.iniciar("30111222").getPasoActual());
        assertThrows(ReglaNegocioException.class, () -> escenario.asistente().iniciar("28999888"));
    }

    @Test
    void unHorarioTomadoDesapareceDeLaLista() {
        hastaElegirHorario();
        asistente.elegirHorario(horario(9, 0));
        asistente.confirmar();

        AsistenteReservaTurno otro = escenario.asistente();
        otro.iniciar("28999888");
        otro.elegirEspecialidad("Cardiologia");
        otro.elegirProfesional(escenario.laura.getId());

        assertFalse(otro.horariosDisponibles(dia).contains(dia.atTime(9, 0)));
        assertThrows(ReglaNegocioException.class, () -> otro.elegirHorario(horario(9, 0)));
    }
}
