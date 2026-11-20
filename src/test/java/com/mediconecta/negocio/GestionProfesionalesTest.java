package com.mediconecta.negocio;

import com.mediconecta.modelo.Disponibilidad;
import com.mediconecta.modelo.Profesional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Profesionales, su disponibilidad y la grilla de horarios libres. */
class GestionProfesionalesTest {

    private Escenario escenario;

    private GestionProfesionales gestion;

    @BeforeEach
    void preparar() {
        escenario = new Escenario();
        gestion = escenario.gestionProfesionales;
    }

    private static Disponibilidad franja(DayOfWeek dia, int desde, int hasta, int minutos) {
        return new Disponibilidad(dia, LocalTime.of(desde, 0), LocalTime.of(hasta, 0), minutos);
    }

    @Test
    void listaYFiltraPorEspecialidad() {
        assertEquals(2, gestion.listarProfesionales().size());
        assertEquals(1, gestion.listarPorEspecialidad("Cardiologia").size());
        assertEquals(2, gestion.listarPorEspecialidad(" ").size());
        assertTrue(gestion.listarEspecialidades().contains("Traumatologia"));
        assertThrows(NoEncontradoException.class, () -> gestion.obtener(999L));
    }

    @Test
    void registraValidandoDatosYMatricula() {
        Profesional nuevo = new Profesional(" Dra. Sofia Aguirre ", "Pediatria", " MP-30512 ");
        nuevo.getDisponibilidades().add(franja(DayOfWeek.MONDAY, 9, 12, 20));
        Profesional guardado = gestion.registrarProfesional(nuevo);
        assertEquals("MP-30512", guardado.getMatricula());
        assertTrue(guardado.isActivo());

        assertThrows(ReglaNegocioException.class, () -> gestion.registrarProfesional(null));
        assertThrows(ReglaNegocioException.class,
                () -> gestion.registrarProfesional(new Profesional(" ", "Pediatria", "MP-1")));
        assertThrows(ReglaNegocioException.class,
                () -> gestion.registrarProfesional(new Profesional("Dr. X", " ", "MP-1")));
        assertThrows(ReglaNegocioException.class,
                () -> gestion.registrarProfesional(new Profesional("Dr. X", "Pediatria", " ")));
        assertThrows(ReglaNegocioException.class,
                () -> gestion.registrarProfesional(new Profesional("Dr. X", "Pediatria", "MP-30512")));
    }

    @Test
    void lasFranjasInvalidasSeRechazan() {
        Long id = escenario.laura.getId();
        assertThrows(ReglaNegocioException.class, () -> gestion.definirDisponibilidad(id, List.of()));
        assertThrows(ReglaNegocioException.class,
                () -> gestion.definirDisponibilidad(id, List.of(franja(null, 9, 12, 30))));
        assertThrows(ReglaNegocioException.class,
                () -> gestion.definirDisponibilidad(id, List.of(franja(DayOfWeek.MONDAY, 12, 9, 30))));
        assertThrows(ReglaNegocioException.class,
                () -> gestion.definirDisponibilidad(id, List.of(franja(DayOfWeek.MONDAY, 9, 12, 0))));
        assertThrows(ReglaNegocioException.class, () -> gestion.definirDisponibilidad(id,
                List.of(new Disponibilidad(DayOfWeek.MONDAY, null, LocalTime.NOON, 30))));
    }

    @Test
    void unMedicoSoloEditaSuPropiaAgenda() {
        escenario.comoUsuario("medico", Roles.MEDICO);

        Profesional propio = gestion.definirDisponibilidad(escenario.laura.getId(),
                List.of(franja(DayOfWeek.SATURDAY, 8, 10, 60)));
        assertEquals(1, propio.getDisponibilidades().size());
        assertEquals("MP-10234", gestion.miPerfilProfesional().getMatricula());

        assertThrows(ReglaNegocioException.class, () -> gestion.definirDisponibilidad(escenario.martin.getId(),
                List.of(franja(DayOfWeek.MONDAY, 9, 10, 30))));

        escenario.comoUsuario("otro-medico", Roles.MEDICO);
        assertThrows(NoEncontradoException.class, () -> gestion.miPerfilProfesional());
    }

    @Test
    void losHorariosLibresSalenDeLaAgendaMenosLosOcupados() {
        LocalDate dia = Escenario.proximoDiaHabil();
        List<java.time.LocalDateTime> libres = gestion.horariosLibres(escenario.laura.getId(), dia);
        assertEquals(8, libres.size());
        assertEquals(dia.atTime(9, 0), libres.get(0));

        escenario.gestionTurnos.agendar(escenario.ana, escenario.laura, "Cardiologia", dia.atTime(9, 0).toString(), null);
        assertFalse(gestion.horariosLibres(escenario.laura.getId(), dia).contains(dia.atTime(9, 0)));

        assertTrue(gestion.horariosLibres(escenario.martin.getId(), dia).isEmpty(), "sin agenda no hay horarios");
        assertThrows(ReglaNegocioException.class, () -> gestion.horariosLibres(escenario.laura.getId(), null));
    }
}
