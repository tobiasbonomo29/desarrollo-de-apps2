package com.mediconecta.negocio;

import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Patron Facade: unica puerta de entrada al caso de uso "reservar un turno".
 *
 * <p><b>Problema que resuelve.</b> Reservar un turno no es una operacion de un
 * solo componente. Hay que resolver el paciente (o darlo de alta si es la
 * primera vez), resolver el profesional, verificar que la especialidad sea
 * coherente y recien entonces agendar. Si esa coreografia viviera en
 * {@code RecursoTurnos}, la capa de presentacion tendria que inyectar y ordenar
 * tres componentes de negocio; y como el asistente stateful hace exactamente lo
 * mismo, la secuencia quedaria duplicada en dos lugares que se irian
 * desincronizando.</p>
 *
 * <p><b>Como lo resuelve.</b> La fachada concentra la coordinacion. Los dos
 * clientes -el alta directa por REST y el asistente {@code @Stateful}- llaman a
 * {@link #reservar} y no saben cuantos componentes hay detras. La fachada
 * <b>coordina pero no decide</b>: cada regla la sigue aplicando su dueno
 * ({@code GestionPacientes} valida el padron, {@code GestionTurnos} valida fecha
 * y superposicion). Es la diferencia entre un Facade y un componente que se
 * convierte en el cajon de sastre del sistema.</p>
 */
@Stateless
public class FachadaReservas {

    @Inject
    private GestionTurnos gestionTurnos;

    @Inject
    private GestionPacientes gestionPacientes;

    @Inject
    private GestionProfesionales gestionProfesionales;

    // ---------- Consultas que alimentan la pantalla de reserva ----------

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<String> especialidades() {
        return gestionProfesionales.listarEspecialidades();
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Profesional> profesionalesDe(String especialidad) {
        return gestionProfesionales.listarPorEspecialidad(especialidad);
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<LocalDateTime> horariosLibres(Long profesionalId, LocalDate fecha) {
        return gestionProfesionales.horariosLibres(profesionalId, fecha);
    }

    /**
     * Estado general de la clinica para el tablero de inicio.
     *
     * <p>Es el otro caso donde la fachada evita que la presentacion orqueste:
     * una sola llamada en lugar de cuatro.</p>
     */
    @RolesAllowed({Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public ResumenAgenda resumenDelDia() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        List<Turno> deHoy = gestionTurnos.turnosDelDia(hoy);

        long pacientes = gestionPacientes.listarPacientes().stream()
                .filter(Paciente::isActivo)
                .count();

        long profesionales = gestionProfesionales.listarProfesionales().stream()
                .filter(Profesional::isActivo)
                .count();

        List<Turno> proximos = gestionTurnos.listarTurnos().stream()
                .filter(t -> t.getEstado() == EstadoTurno.PROGRAMADO)
                .filter(t -> !t.getFechaHora().isBefore(ahora))
                .sorted(Comparator.comparing(Turno::getFechaHora))
                .limit(6)
                .toList();

        long pendientes = gestionTurnos.listarTurnos().stream()
                .filter(t -> t.getEstado() == EstadoTurno.PROGRAMADO)
                .filter(t -> !t.getFechaHora().isBefore(ahora))
                .count();

        return new ResumenAgenda(
                deHoy.stream().filter(t -> t.getEstado() != EstadoTurno.CANCELADO).count(),
                pacientes, profesionales, pendientes, proximos);
    }

    // ---------- El caso de uso completo ----------

    /**
     * Reserva un turno de punta a punta.
     *
     * <p>Pasos: resolver el paciente (alta al vuelo si el documento todavia no
     * esta en el padron), resolver el profesional (por id o, para compatibilidad
     * con la API original, por nombre) y delegar el alta en
     * {@code GestionTurnos}, que es quien valida fecha y superposicion.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.ADMIN})
    public Turno reservar(SolicitudTurno solicitud) {
        if (solicitud == null) {
            throw new ReglaNegocioException("Debe enviar los datos del turno.");
        }

        Paciente paciente = resolverPaciente(solicitud);
        Profesional profesional = resolverProfesional(solicitud);

        return gestionTurnos.agendar(paciente, profesional,
                solicitud.getEspecialidad(), solicitud.getFechaHora(), solicitud.getMotivo());
    }

    private Paciente resolverPaciente(SolicitudTurno solicitud) {
        String documento = solicitud.getDocumentoPaciente();
        if (documento == null || documento.isBlank()) {
            throw new ReglaNegocioException("El paciente debe tener nombre y documento.");
        }

        try {
            return gestionPacientes.buscarPorDocumento(documento);
        } catch (NoEncontradoException noExiste) {
            if (solicitud.getNombrePaciente() == null || solicitud.getNombrePaciente().isBlank()) {
                throw new ReglaNegocioException(
                        "No hay un paciente con documento " + documento
                                + ". Registrelo primero o envie tambien su nombre.");
            }
            Paciente nuevo = new Paciente(solicitud.getNombrePaciente(), documento);
            return gestionPacientes.registrarPaciente(nuevo);
        }
    }

    private Profesional resolverProfesional(SolicitudTurno solicitud) {
        if (solicitud.getProfesionalId() != null) {
            return gestionProfesionales.obtener(solicitud.getProfesionalId());
        }

        String nombre = solicitud.getNombreProfesional();
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Debe indicar el profesional que atiende.");
        }

        return gestionProfesionales.listarProfesionales().stream()
                .filter(p -> p.getNombre().equalsIgnoreCase(nombre.trim()))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException(
                        "No hay ningun profesional llamado " + nombre + "."));
    }
}
