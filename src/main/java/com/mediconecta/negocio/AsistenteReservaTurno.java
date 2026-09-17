package com.mediconecta.negocio;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Remove;
import jakarta.ejb.Stateful;
import jakarta.ejb.StatefulTimeout;
import jakarta.inject.Inject;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Componente 4 - Asistente de Reserva de Turno. EJB <b>stateful</b>.
 *
 * <p>Este es el componente que justifica la existencia de {@code @Stateful} en
 * el sistema. Reservar un turno guiado es <b>una sola operacion de negocio
 * repartida en cuatro invocaciones</b>: elegir especialidad, elegir profesional,
 * elegir horario y confirmar. Entre una y otra el servidor tiene que recordar lo
 * que <b>ese</b> usuario fue eligiendo, y el navegador no reenvia nada: cada
 * request solo lleva la cookie de sesion.</p>
 *
 * <p>Los campos de abajo son estado de conversacion. Ese es el criterio que lo
 * separa de {@code GestionTurnos}, que no tiene ni uno solo: si dos requests de
 * este flujo cayeran en instancias distintas, el segundo no sabria que eligio el
 * primero. Por eso el contenedor dedica una instancia por cliente en lugar de
 * usar un pool compartido.</p>
 *
 * <p>La conversacion se ata a la sesion HTTP a traves de
 * {@code ConversacionDeReserva}, un bean CDI {@code @SessionScoped} que inyecta
 * este EJB. El ciclo de vida lo administra integramente el contenedor:
 * {@code @PostConstruct} al crearlo, {@code @Remove} cuando la conversacion
 * termina, {@code @PreDestroy} justo antes de descartarlo y
 * {@code @StatefulTimeout} si el usuario abandona a mitad de camino.</p>
 *
 * <p>Al confirmar <b>no</b> repite ninguna regla: arma una {@link SolicitudTurno}
 * y se la pasa a {@link FachadaReservas}, el mismo camino que usa el alta
 * directa por REST.</p>
 */
@Stateful
@StatefulTimeout(value = 30, unit = TimeUnit.MINUTES)
@RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.ADMIN})
public class AsistenteReservaTurno implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(AsistenteReservaTurno.class.getName());

    static final int SIN_INICIAR = 0;
    static final int ELEGIR_ESPECIALIDAD = 1;
    static final int ELEGIR_PROFESIONAL = 2;
    static final int ELEGIR_HORARIO = 3;
    static final int CONFIRMAR = 4;
    static final int FINALIZADA = 5;

    @Inject
    private FachadaReservas fachadaReservas;

    @Inject
    private GestionPacientes gestionPacientes;

    @Inject
    private GestionProfesionales gestionProfesionales;

    @Inject
    private FabricaTurnos fabricaTurnos;

    // ----- Estado de conversacion: esto es lo que hace stateful al componente -----

    private String idConversacion;

    private LocalDateTime iniciadaEn;

    private int paso = SIN_INICIAR;

    private String documentoPaciente;

    private String nombrePaciente;

    private String especialidad;

    private Long profesionalId;

    private String nombreProfesional;

    private LocalDateTime fechaHora;

    private Long turnoId;

    // ----- Ciclo de vida administrado por el contenedor -----

    @PostConstruct
    void alCrear() {
        idConversacion = UUID.randomUUID().toString().substring(0, 8);
        iniciadaEn = LocalDateTime.now();
        LOG.info("[STATEFUL] @PostConstruct - el contenedor creo el asistente "
                + idConversacion + " (instancia " + instancia() + ")");
    }

    @PreDestroy
    void alDestruir() {
        LOG.info("[STATEFUL] @PreDestroy - el contenedor descarta el asistente "
                + idConversacion + " (instancia " + instancia() + ")");
    }

    /**
     * Cierra la conversacion y le avisa al contenedor que puede descartar la
     * instancia. Despues de este metodo el bean deja de existir.
     */
    @Remove
    public void finalizar() {
        LOG.info("[STATEFUL] @Remove - fin de la conversacion " + idConversacion
                + " en el paso " + paso);
    }

    // ----- Los cuatro pasos -----

    /** Paso 0: identifica al paciente y abre la conversacion. */
    public EstadoReserva iniciar(String documentoPaciente) {
        Paciente paciente = gestionPacientes.buscarPorDocumento(documentoPaciente);
        this.documentoPaciente = paciente.getDocumento();
        this.nombrePaciente = paciente.getNombre();
        this.especialidad = null;
        this.profesionalId = null;
        this.nombreProfesional = null;
        this.fechaHora = null;
        this.turnoId = null;
        this.paso = ELEGIR_ESPECIALIDAD;

        LOG.info("[STATEFUL] conversacion " + idConversacion + " iniciada para el paciente "
                + paciente.getDocumento());
        return estado();
    }

    /** Paso 1. */
    public EstadoReserva elegirEspecialidad(String especialidad) {
        exigirPaso(ELEGIR_ESPECIALIDAD, "Primero debe iniciar la reserva indicando el paciente.");

        if (especialidad == null || especialidad.isBlank()) {
            throw new ReglaNegocioException("Debe elegir una especialidad.");
        }
        if (!fachadaReservas.especialidades().contains(especialidad.trim())) {
            throw new ReglaNegocioException("No hay profesionales para la especialidad " + especialidad + ".");
        }

        this.especialidad = especialidad.trim();
        this.profesionalId = null;
        this.nombreProfesional = null;
        this.fechaHora = null;
        this.paso = ELEGIR_PROFESIONAL;

        LOG.info("[STATEFUL] conversacion " + idConversacion + " recuerda especialidad=" + this.especialidad);
        return estado();
    }

    /** Paso 2. */
    public EstadoReserva elegirProfesional(Long profesionalId) {
        exigirPaso(ELEGIR_PROFESIONAL, "Primero debe elegir una especialidad.");

        if (profesionalId == null) {
            throw new ReglaNegocioException("Debe elegir un profesional.");
        }

        Profesional profesional = gestionProfesionales.obtener(profesionalId);
        if (!profesional.getEspecialidad().equalsIgnoreCase(especialidad)) {
            throw new ReglaNegocioException("El profesional " + profesional.getNombre()
                    + " no atiende " + especialidad + ".");
        }

        this.profesionalId = profesional.getId();
        this.nombreProfesional = profesional.getNombre();
        this.fechaHora = null;
        this.paso = ELEGIR_HORARIO;

        LOG.info("[STATEFUL] conversacion " + idConversacion + " recuerda profesional=" + nombreProfesional);
        return estado();
    }

    /** Paso 3. */
    public EstadoReserva elegirHorario(String fechaHoraTexto) {
        exigirPaso(ELEGIR_HORARIO, "Primero debe elegir un profesional.");

        LocalDateTime momento = fabricaTurnos.interpretarFecha(textoValido(fechaHoraTexto));
        List<LocalDateTime> libres = gestionProfesionales.horariosLibres(profesionalId, momento.toLocalDate());
        if (!libres.contains(momento)) {
            throw new ReglaNegocioException("Ese horario ya no esta disponible. Elija otro.");
        }

        this.fechaHora = momento;
        this.paso = CONFIRMAR;

        LOG.info("[STATEFUL] conversacion " + idConversacion + " recuerda horario=" + momento);
        return estado();
    }

    /**
     * Vuelve un paso atras y olvida lo que se habia elegido de ahi en adelante.
     *
     * <p>Que "Atras" sea una operacion del servidor y no del navegador es otra
     * muestra de que la conversacion vive aca: el cliente no tiene que reconstruir
     * nada, solo vuelve a preguntar en que paso quedo.</p>
     */
    public EstadoReserva volver() {
        if (paso == SIN_INICIAR || paso == ELEGIR_ESPECIALIDAD) {
            throw new ReglaNegocioException("Ya esta en el primer paso de la reserva.");
        }
        if (paso == FINALIZADA) {
            throw new ReglaNegocioException("La reserva ya fue confirmada.");
        }

        paso--;
        if (paso == ELEGIR_ESPECIALIDAD) {
            especialidad = null;
        }
        if (paso <= ELEGIR_PROFESIONAL) {
            profesionalId = null;
            nombreProfesional = null;
        }
        fechaHora = null;

        LOG.info("[STATEFUL] conversacion " + idConversacion + " retrocedio al paso " + paso);
        return estado();
    }

    /** Horarios disponibles del profesional elegido, para la pantalla del paso 3. */
    public List<LocalDateTime> horariosDisponibles(LocalDate fecha) {
        if (profesionalId == null) {
            throw new ReglaNegocioException("Primero debe elegir un profesional.");
        }
        return gestionProfesionales.horariosLibres(profesionalId, fecha);
    }

    /**
     * Paso 4: confirma. Delega el alta en la fachada, que es el mismo camino que
     * usa el alta directa, para no duplicar ni una regla.
     */
    public EstadoReserva confirmar() {
        exigirPaso(CONFIRMAR, "Faltan pasos para poder confirmar la reserva.");

        SolicitudTurno solicitud = new SolicitudTurno();
        solicitud.setDocumentoPaciente(documentoPaciente);
        solicitud.setNombrePaciente(nombrePaciente);
        solicitud.setProfesionalId(profesionalId);
        solicitud.setEspecialidad(especialidad);
        solicitud.setFechaHora(fechaHora.toString());

        Turno turno = fachadaReservas.reservar(solicitud);

        this.turnoId = turno.getId();
        this.paso = FINALIZADA;

        LOG.info("[STATEFUL] conversacion " + idConversacion + " confirmada: turno " + turnoId);
        return estado();
    }

    /** Estado actual, tal como lo recuerda el servidor. */
    public EstadoReserva estado() {
        EstadoReserva vista = new EstadoReserva();
        vista.setIdConversacion(idConversacion);
        vista.setInstancia(instancia());
        vista.setPaso(paso);
        vista.setPasoActual(nombreDelPaso());
        vista.setIniciadaEn(iniciadaEn);
        vista.setDocumentoPaciente(documentoPaciente);
        vista.setNombrePaciente(nombrePaciente);
        vista.setEspecialidad(especialidad);
        vista.setProfesionalId(profesionalId);
        vista.setNombreProfesional(nombreProfesional);
        vista.setFechaHora(fechaHora);
        vista.setFinalizada(paso == FINALIZADA);
        vista.setTurnoId(turnoId);
        return vista;
    }

    private void exigirPaso(int esperado, String mensaje) {
        if (paso != esperado) {
            throw new ReglaNegocioException(mensaje);
        }
    }

    private String textoValido(String fechaHoraTexto) {
        if (fechaHoraTexto == null || fechaHoraTexto.isBlank()) {
            throw new ReglaNegocioException("Debe elegir un horario.");
        }
        return fechaHoraTexto.trim();
    }

    private String nombreDelPaso() {
        return switch (paso) {
            case ELEGIR_ESPECIALIDAD -> "ELEGIR_ESPECIALIDAD";
            case ELEGIR_PROFESIONAL -> "ELEGIR_PROFESIONAL";
            case ELEGIR_HORARIO -> "ELEGIR_HORARIO";
            case CONFIRMAR -> "CONFIRMAR";
            case FINALIZADA -> "FINALIZADA";
            default -> "SIN_INICIAR";
        };
    }

    private String instancia() {
        return Integer.toHexString(System.identityHashCode(this));
    }
}
