package com.mediconecta.datos;

import com.mediconecta.modelo.Turno;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Contrato de acceso a datos de turnos (patron DAO / Repository).
 *
 * <p>Esta interfaz es la frontera entre la capa de negocio y la capa de datos.
 * El negocio depende <b>solo de esta interfaz</b> y nunca de una implementacion
 * concreta. Esa decision es la que permitio migrar de un
 * {@code ConcurrentHashMap} a JPA + PostgreSQL sin tocar una sola linea de
 * {@code GestionTurnos} ni de la capa de presentacion: alcanzo con escribir
 * {@link com.mediconecta.datos.jpa.RepositorioTurnosJpa}.</p>
 *
 * <p>Notar que el contrato no menciona SQL, ni {@code EntityManager}, ni listas:
 * habla en terminos del dominio.</p>
 */
public interface RepositorioTurnos {

    /** Devuelve todos los turnos registrados (nunca {@code null}). */
    List<Turno> listarTodos();

    /**
     * Agenda operativa: todos los turnos que no fueron archivados.
     *
     * <p>Separada de {@link #listarTodos()} porque la agenda general oculta lo
     * archivado, pero otras consultas (como el historial de un paciente) tienen
     * que seguir mostrandolo.</p>
     */
    List<Turno> listarAgenda();

    /**
     * Busca un turno por su identificador.
     *
     * <p>Devuelve {@link Optional} en lugar de {@code null} para obligar a quien
     * llama a contemplar explicitamente el caso "no existe".</p>
     */
    Optional<Turno> buscarPorId(Long id);

    /** Guarda un turno nuevo y le asigna identificador. */
    Turno guardar(Turno turno);

    /** Persiste los cambios de un turno ya existente. */
    Turno actualizar(Turno turno);

    /**
     * Indica si el profesional ya tiene un turno <b>vigente</b> en ese horario.
     *
     * <p>Un turno cancelado no cuenta: su horario vuelve a estar libre. La
     * consulta vive en la capa de datos (manana es un {@code SELECT ... WHERE});
     * que hacer con la respuesta lo decide el negocio.</p>
     */
    boolean existeTurnoVigente(Long profesionalId, LocalDateTime fechaHora);

    /** Historial completo de un paciente, identificado por su documento. */
    List<Turno> listarPorPaciente(String documento);

    /** Turnos de un profesional en un dia concreto. */
    List<Turno> listarPorProfesionalYFecha(Long profesionalId, LocalDate fecha);

    /** Turnos de toda la clinica en un dia concreto. */
    List<Turno> listarPorFecha(LocalDate fecha);
}
