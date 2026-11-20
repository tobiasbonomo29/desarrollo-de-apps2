package com.mediconecta.negocio;

import com.mediconecta.datos.memoria.RepositorioIdempotenciaEnMemoria;
import com.mediconecta.datos.memoria.RepositorioPacientesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioProfesionalesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioTurnosEnMemoria;
import com.mediconecta.mensajeria.NotificadorTurnos;
import com.mediconecta.modelo.Disponibilidad;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Los componentes de negocio armados como los arma el contenedor, pero sobre
 * los repositorios en memoria y con un productor JMS que solo anota.
 *
 * <p>Datos: Ana Gomez (usuario "paciente"), Bruno Diaz (sin usuario), la
 * Dra. Laura Fernandez (Cardiologia, lunes a viernes de 9 a 13 cada 30 minutos,
 * usuario "medico") y el Dr. Martin Ruiz (Traumatologia, sin usuario).</p>
 */
final class Escenario {

    final RepositorioTurnosEnMemoria turnos = new RepositorioTurnosEnMemoria();

    final RepositorioPacientesEnMemoria pacientes = new RepositorioPacientesEnMemoria();

    final RepositorioProfesionalesEnMemoria profesionales = new RepositorioProfesionalesEnMemoria();

    final RepositorioIdempotenciaEnMemoria claves = new RepositorioIdempotenciaEnMemoria();

    /** Turnos por los que se habria mandado un mensaje JMS. */
    final List<Turno> notificados = new ArrayList<>();

    final GestionTurnos gestionTurnos = new GestionTurnos();

    final GestionPacientes gestionPacientes = new GestionPacientes();

    final GestionProfesionales gestionProfesionales = new GestionProfesionales();

    final FachadaReservas fachada = new FachadaReservas();

    final Paciente ana;

    final Paciente bruno;

    final Profesional laura;

    final Profesional martin;

    Escenario() {
        ana = new Paciente("Ana Gomez", "30111222");
        ana.setUsuario("paciente");
        pacientes.guardar(ana);
        bruno = pacientes.guardar(new Paciente("Bruno Diaz", "28999888"));

        laura = new Profesional("Dra. Laura Fernandez", "Cardiologia", "MP-10234");
        laura.setUsuario("medico");
        for (DayOfWeek dia : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            laura.getDisponibilidades().add(new Disponibilidad(dia, LocalTime.of(9, 0), LocalTime.of(13, 0), 30));
        }
        profesionales.guardar(laura);
        martin = profesionales.guardar(new Profesional("Dr. Martin Ruiz", "Traumatologia", "MP-20876"));

        Inyeccion.campo(gestionTurnos, "repositorio", turnos);
        Inyeccion.campo(gestionTurnos, "fabricaTurnos", new FabricaTurnos());

        Inyeccion.campo(gestionPacientes, "repositorio", pacientes);
        Inyeccion.campo(gestionPacientes, "gestionTurnos", gestionTurnos);

        Inyeccion.campo(gestionProfesionales, "repositorio", profesionales);
        Inyeccion.campo(gestionProfesionales, "gestionTurnos", gestionTurnos);

        Inyeccion.campo(fachada, "gestionTurnos", gestionTurnos);
        Inyeccion.campo(fachada, "gestionPacientes", gestionPacientes);
        Inyeccion.campo(fachada, "gestionProfesionales", gestionProfesionales);
        Inyeccion.campo(fachada, "repositorioIdempotencia", claves);
        Inyeccion.campo(fachada, "notificadorTurnos", new NotificadorTurnos() {
            @Override
            public void turnoConfirmado(Turno turno) {
                notificados.add(turno);
            }
        });

        comoUsuario("recepcion", Roles.RECEPCION);
    }

    /** Cambia el usuario autenticado en todos los componentes a la vez. */
    void comoUsuario(String usuario, String... roles) {
        var llamador = Inyeccion.llamador(usuario, roles);
        Inyeccion.campo(gestionTurnos, "contexto", llamador);
        Inyeccion.campo(gestionPacientes, "contexto", llamador);
        Inyeccion.campo(gestionProfesionales, "contexto", llamador);
        Inyeccion.campo(fachada, "contexto", llamador);
    }

    /** Un asistente recien creado, como lo deja el contenedor despues de @PostConstruct. */
    AsistenteReservaTurno asistente() {
        AsistenteReservaTurno asistente = new AsistenteReservaTurno();
        Inyeccion.campo(asistente, "fachadaReservas", fachada);
        Inyeccion.campo(asistente, "gestionPacientes", gestionPacientes);
        Inyeccion.campo(asistente, "gestionProfesionales", gestionProfesionales);
        Inyeccion.campo(asistente, "fabricaTurnos", new FabricaTurnos());
        asistente.alCrear();
        return asistente;
    }

    /** El proximo dia habil a partir de manana (Laura atiende de lunes a viernes). */
    static LocalDate proximoDiaHabil() {
        LocalDate dia = LocalDate.now().plusDays(1);
        while (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dia = dia.plusDays(1);
        }
        return dia;
    }
}
