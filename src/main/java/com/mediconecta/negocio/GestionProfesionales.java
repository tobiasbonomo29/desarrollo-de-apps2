package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioProfesionales;
import com.mediconecta.modelo.Disponibilidad;
import com.mediconecta.modelo.Profesional;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Componente 3 - Gestion de Profesionales. EJB <b>stateless</b>.
 *
 * <p>Dueno del padron de profesionales, de sus especialidades y de la agenda de
 * disponibilidad semanal. La operacion mas interesante es
 * {@link #horariosLibres}, que es donde este componente <b>colabora</b> con
 * {@code GestionTurnos}: la grilla de horarios sale de la disponibilidad, pero
 * los horarios que ya tienen un turno vigente los conoce el componente de
 * turnos, no este.</p>
 */
@Stateless
public class GestionProfesionales {

    @Inject
    private RepositorioProfesionales repositorio;

    @Inject
    private GestionTurnos gestionTurnos;

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Profesional> listarProfesionales() {
        return repositorio.listarTodos();
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Profesional> listarPorEspecialidad(String especialidad) {
        if (especialidad == null || especialidad.isBlank()) {
            return listarProfesionales();
        }
        return repositorio.listarPorEspecialidad(especialidad.trim());
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<String> listarEspecialidades() {
        return repositorio.listarEspecialidades();
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public Profesional obtener(Long id) {
        return repositorio.buscarPorId(id)
                .orElseThrow(() -> NoEncontradoException.profesional(id));
    }

    /** Alta de profesional. Operacion reservada al rol ADMIN. */
    @RolesAllowed(Roles.ADMIN)
    public Profesional registrarProfesional(Profesional profesional) {
        if (profesional == null) {
            throw new ReglaNegocioException("Debe enviar los datos del profesional.");
        }
        if (estaVacio(profesional.getNombre())) {
            throw new ReglaNegocioException("El nombre del profesional es obligatorio.");
        }
        if (estaVacio(profesional.getEspecialidad())) {
            throw new ReglaNegocioException("La especialidad del profesional es obligatoria.");
        }
        if (estaVacio(profesional.getMatricula())) {
            throw new ReglaNegocioException("La matricula del profesional es obligatoria.");
        }

        String matricula = profesional.getMatricula().trim();
        if (repositorio.buscarPorMatricula(matricula).isPresent()) {
            throw new ReglaNegocioException("Ya existe un profesional con matricula " + matricula + ".");
        }

        profesional.setId(null);
        profesional.setNombre(profesional.getNombre().trim());
        profesional.setEspecialidad(profesional.getEspecialidad().trim());
        profesional.setMatricula(matricula);
        profesional.setActivo(true);
        if (profesional.getDisponibilidades() == null) {
            profesional.setDisponibilidades(new ArrayList<>());
        }
        profesional.getDisponibilidades().forEach(this::validarFranja);
        return repositorio.guardar(profesional);
    }

    /**
     * Reemplaza la agenda semanal de un profesional.
     *
     * <p>Operacion de MEDICO o ADMIN: es quien atiende (o la administracion)
     * quien decide en que franjas se puede reservar.</p>
     */
    @RolesAllowed({Roles.MEDICO, Roles.ADMIN})
    public Profesional definirDisponibilidad(Long profesionalId, List<Disponibilidad> franjas) {
        Profesional profesional = obtener(profesionalId);

        if (franjas == null || franjas.isEmpty()) {
            throw new ReglaNegocioException("Debe indicar al menos una franja de disponibilidad.");
        }
        franjas.forEach(this::validarFranja);

        profesional.getDisponibilidades().clear();
        franjas.forEach(f -> {
            f.setId(null);
            profesional.getDisponibilidades().add(f);
        });
        return repositorio.actualizar(profesional);
    }

    /**
     * Horarios en los que todavia se puede reservar con ese profesional ese dia.
     *
     * <p>Toma las franjas del dia de la semana correspondiente, las parte en
     * intervalos del largo configurado, descuenta los que ya tienen un turno
     * vigente (dato que aporta {@code GestionTurnos}) y descarta los que ya
     * pasaron.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<LocalDateTime> horariosLibres(Long profesionalId, LocalDate fecha) {
        if (fecha == null) {
            throw new ReglaNegocioException("Debe indicar la fecha, con formato aaaa-MM-dd.");
        }

        Profesional profesional = obtener(profesionalId);
        List<LocalDateTime> ocupados = gestionTurnos.horariosOcupados(profesionalId, fecha);
        LocalDateTime ahora = LocalDateTime.now();
        List<LocalDateTime> libres = new ArrayList<>();

        for (Disponibilidad franja : profesional.getDisponibilidades()) {
            if (franja.getDiaSemana() != fecha.getDayOfWeek()) {
                continue;
            }
            LocalTime hora = franja.getHoraDesde();
            while (!hora.plusMinutes(franja.getDuracionMinutos()).isAfter(franja.getHoraHasta())) {
                LocalDateTime momento = fecha.atTime(hora);
                if (momento.isAfter(ahora) && !ocupados.contains(momento)) {
                    libres.add(momento);
                }
                hora = hora.plusMinutes(franja.getDuracionMinutos());
            }
        }

        libres.sort(LocalDateTime::compareTo);
        return libres;
    }

    private void validarFranja(Disponibilidad franja) {
        if (franja.getDiaSemana() == null) {
            throw new ReglaNegocioException("Cada franja de disponibilidad necesita un dia de la semana.");
        }
        if (franja.getHoraDesde() == null || franja.getHoraHasta() == null) {
            throw new ReglaNegocioException("Cada franja necesita hora de inicio y de fin.");
        }
        if (!franja.getHoraDesde().isBefore(franja.getHoraHasta())) {
            throw new ReglaNegocioException("La hora de inicio debe ser anterior a la de fin.");
        }
        if (franja.getDuracionMinutos() <= 0) {
            throw new ReglaNegocioException("La duracion del turno debe ser mayor a cero minutos.");
        }
    }

    private boolean estaVacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
