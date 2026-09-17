package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioTurnos;
import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Componente 1 - Gestion de Turnos. EJB <b>stateless</b>.
 *
 * <p>Es el dueno de las reglas de la agenda. No conserva nada entre invocaciones:
 * cada metodo recibe en sus parametros todo lo que necesita, resuelve y termina.
 * Por eso dos requests del mismo usuario pueden ser atendidos por instancias
 * distintas del pool sin ninguna diferencia observable.</p>
 *
 * <p>La autorizacion se declara aca, sobre los metodos de negocio, y la aplica el
 * contenedor de EJB. No se declara en la capa de presentacion: asi la regla vale
 * aunque manana el mismo metodo se invoque desde SOAP o desde una cola.</p>
 */
@Stateless
public class GestionTurnos {

    @Inject
    private RepositorioTurnos repositorio;

    @Inject
    private FabricaTurnos fabricaTurnos;

    /**
     * Agenda completa, ordenada cronologicamente.
     *
     * <p>Un PACIENTE no puede verla: solo accede a su propio historial a traves
     * de {@code GestionPacientes.historialDeTurnos}.</p>
     */
    @RolesAllowed({Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Turno> listarTurnos() {
        return repositorio.listarTodos();
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public Turno obtenerTurno(Long id) {
        return repositorio.buscarPorId(id)
                .orElseThrow(() -> NoEncontradoException.turno(id));
    }

    /** Turnos de un dia, para el tablero de inicio. */
    @RolesAllowed({Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Turno> turnosDelDia(LocalDate fecha) {
        return repositorio.listarPorFecha(fecha);
    }

    /** Historial de un paciente. Lo consume {@code GestionPacientes}. */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Turno> turnosDePaciente(String documento) {
        return repositorio.listarPorPaciente(documento);
    }

    /**
     * Horarios que un profesional ya tiene tomados en una fecha.
     *
     * <p>Devuelve solo momentos, sin datos del paciente, porque lo consume
     * {@code GestionProfesionales} para calcular los horarios libres y esa
     * consulta esta disponible para cualquier rol.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<LocalDateTime> horariosOcupados(Long profesionalId, LocalDate fecha) {
        return repositorio.listarPorProfesionalYFecha(profesionalId, fecha).stream()
                .filter(t -> t.getEstado() != EstadoTurno.CANCELADO)
                .map(Turno::getFechaHora)
                .toList();
    }

    /**
     * Da de alta un turno aplicando todas las reglas de la agenda.
     *
     * <p>Recibe el paciente y el profesional ya resueltos: quien los busca es
     * {@link FachadaReservas}, que es el componente encargado de coordinar. Aca
     * viven unicamente las reglas propias del turno.</p>
     *
     * @throws ReglaNegocioException si la fecha no es valida, esta en el pasado
     *                               o el profesional ya tiene ese horario tomado
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.ADMIN})
    public Turno agendar(Paciente paciente, Profesional profesional,
                         String especialidad, String fechaHoraTexto, String motivo) {

        LocalDateTime momento = fabricaTurnos.interpretarFecha(fechaHoraTexto);

        if (momento.isBefore(LocalDateTime.now())) {
            throw new ReglaNegocioException("No se puede agendar un turno en el pasado.");
        }

        Turno turno = fabricaTurnos.crear(paciente, profesional, especialidad, momento, motivo);

        if (repositorio.existeTurnoVigente(profesional.getId(), turno.getFechaHora())) {
            throw new ReglaNegocioException(
                    "El profesional " + profesional.getNombre() + " ya tiene un turno en ese horario.");
        }

        return repositorio.guardar(turno);
    }

    /**
     * Cancela un turno.
     *
     * <p>Reglas: el turno debe existir; no se puede cancelar dos veces; y no se
     * puede cancelar uno ya atendido. Al quedar en {@code CANCELADO} su horario
     * vuelve a estar libre, porque {@code existeTurnoVigente} ignora los
     * cancelados.</p>
     */
    @RolesAllowed({Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public Turno cancelarTurno(Long id) {
        Turno turno = obtenerTurno(id);

        if (turno.getEstado() == EstadoTurno.CANCELADO) {
            throw new ReglaNegocioException("El turno " + id + " ya estaba cancelado.");
        }
        if (turno.getEstado() == EstadoTurno.ATENDIDO) {
            throw new ReglaNegocioException("No se puede cancelar un turno que ya fue atendido.");
        }

        turno.setEstado(EstadoTurno.CANCELADO);
        return repositorio.actualizar(turno);
    }
}
