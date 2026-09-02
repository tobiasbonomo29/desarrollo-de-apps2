package com.mediconecta.turnos.negocio;

import com.mediconecta.turnos.datos.RepositorioTurnos;
import com.mediconecta.turnos.modelo.EstadoTurno;
import com.mediconecta.turnos.modelo.Turno;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/**
 * Componente de negocio "Gestion de Turnos": el primer componente real de
 * MediConecta y el nucleo de la Entrega 1.
 *
 * <p><b>Responsabilidad:</b> concentrar las reglas de negocio de los turnos
 * medicos. Es el unico lugar del sistema que decide si un turno es valido.
 * No sabe que existe HTTP (eso es de presentacion) ni como se guardan los
 * datos (eso es de la capa de datos, detras de {@link RepositorioTurnos}).</p>
 *
 * <p><b>Por que es STATELESS:</b> el componente no conserva ningun dato entre
 * una invocacion y la siguiente. Cada llamada (listar, buscar, crear) recibe
 * todo lo que necesita en sus parametros, resuelve y termina; no hay "carrito"
 * ni conversacion en curso asociada a un cliente. Como consecuencia, dos
 * requests del mismo usuario pueden ser atendidos por instancias distintas del
 * pool sin ningun efecto observable. Eso permite al contenedor mantener un pool
 * de instancias reutilizables, lo que escala mejor y ahorra memoria frente a
 * un {@code @Stateful}, que exige una instancia dedicada por cliente y
 * replicacion en cluster. El unico estado que existe (los turnos) es estado de
 * <i>aplicacion</i> y vive en la capa de datos, no en este componente.</p>
 *
 * <p>Se implementa como EJB {@code @Stateless} y no como bean CDI comun porque
 * el contenedor le agrega gratis el pool de instancias y, sobre todo, las
 * transacciones declarativas (cada metodo publico corre en su propia
 * transaccion, comportamiento por defecto {@code REQUIRED}), que es
 * exactamente lo que la Entrega 4 va a necesitar sin reescribir la clase.</p>
 */
@Stateless
public class GestionTurnos {

    /**
     * Se inyecta la <b>interfaz</b>, nunca la implementacion en memoria.
     * Gracias a eso este componente no se entera si detras hay un Map o una
     * base de datos: es la inversion de dependencias que hace posible cambiar
     * la capa de datos sin tocar el negocio.
     */
    @Inject
    private RepositorioTurnos repositorio;

    /**
     * Lista todos los turnos ordenados cronologicamente.
     *
     * <p>El orden es una decision de negocio (una agenda medica se lee de mas
     * proximo a mas lejano), por eso se resuelve aca y no en presentacion.</p>
     *
     * @return los turnos ordenados por fecha y hora ascendente
     */
    public List<Turno> listarTurnos() {
        return repositorio.listarTodos().stream()
                .sorted(Comparator.comparing(Turno::getFechaHora))
                .toList();
    }

    /**
     * Obtiene un turno por su identificador.
     *
     * @param id identificador del turno
     * @return el turno encontrado
     * @throws TurnoNoEncontradoException si no existe un turno con ese id
     */
    public Turno obtenerTurno(Long id) {
        return repositorio.buscarPorId(id)
                .orElseThrow(() -> new TurnoNoEncontradoException(id));
    }

    /**
     * Crea un turno nuevo aplicando todas las reglas de negocio.
     *
     * <p>Reglas que se validan, en orden:</p>
     * <ol>
     *   <li>El turno debe traer paciente con nombre y documento.</li>
     *   <li>Debe indicar profesional y especialidad.</li>
     *   <li>La fecha debe estar en formato ISO-8601 valido.</li>
     *   <li>La fecha debe ser futura: no se agendan turnos en el pasado.</li>
     *   <li>El profesional no puede tener otro turno vigente en ese mismo horario.</li>
     * </ol>
     *
     * <p>El estado inicial lo fija el negocio ({@code PROGRAMADO}) y no el
     * cliente, para que nadie pueda crear por HTTP un turno que nazca
     * directamente como {@code ATENDIDO}.</p>
     *
     * @param turno datos del turno a crear (sin id y sin estado)
     * @return el turno creado, ya con su id asignado
     * @throws ReglaNegocioException si alguna de las reglas anteriores no se cumple
     */
    public Turno crearTurno(Turno turno) {
        if (turno == null) {
            throw new ReglaNegocioException("Debe enviar los datos del turno.");
        }
        if (turno.getPaciente() == null
                || estaVacio(turno.getPaciente().getNombre())
                || estaVacio(turno.getPaciente().getDocumento())) {
            throw new ReglaNegocioException("El paciente debe tener nombre y documento.");
        }
        if (estaVacio(turno.getProfesional())) {
            throw new ReglaNegocioException("Debe indicar el profesional que atiende.");
        }
        if (estaVacio(turno.getEspecialidad())) {
            throw new ReglaNegocioException("Debe indicar la especialidad.");
        }

        LocalDateTime momento = interpretarFecha(turno.getFechaHora());
        if (momento.isBefore(LocalDateTime.now())) {
            throw new ReglaNegocioException("No se puede agendar un turno en el pasado.");
        }

        // Se reescribe la fecha en el formato canonico ANTES de buscar
        // superposiciones. Sin este paso, un cliente que manda
        // "2026-09-03T09:30:00" y otro que manda "2026-09-03T09:30" estarian
        // pidiendo el mismo horario y el sistema no lo detectaria, permitiendo
        // dos turnos simultaneos con el mismo profesional.
        turno.setFechaHora(momento.format(Turno.FORMATO_FECHA_HORA));

        if (repositorio.existeTurnoDe(turno.getProfesional(), turno.getFechaHora())) {
            throw new ReglaNegocioException(
                    "El profesional " + turno.getProfesional() + " ya tiene un turno en ese horario.");
        }

        turno.setEstado(EstadoTurno.PROGRAMADO);
        return repositorio.guardar(turno);
    }

    /**
     * Convierte el texto ISO-8601 recibido en una fecha real para poder validarla.
     *
     * @param fechaHora texto con formato {@code aaaa-MM-ddTHH:mm}
     * @return la fecha y hora correspondiente
     * @throws ReglaNegocioException si el texto falta o no tiene el formato esperado
     */
    private LocalDateTime interpretarFecha(String fechaHora) {
        if (estaVacio(fechaHora)) {
            throw new ReglaNegocioException("Debe indicar la fecha y hora del turno.");
        }
        try {
            return LocalDateTime.parse(fechaHora);
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(
                    "La fecha y hora debe tener el formato aaaa-MM-ddTHH:mm, por ejemplo 2026-09-10T09:00.");
        }
    }

    /** Indica si un texto es nulo o esta compuesto solo por espacios. */
    private boolean estaVacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
