package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioMensajesFallidos;
import com.mediconecta.datos.RepositorioNotificaciones;
import com.mediconecta.datos.RepositorioTurnos;
import com.mediconecta.mensajeria.NotificadorTurnos;
import com.mediconecta.modelo.CanalNotificacion;
import com.mediconecta.modelo.MensajeFallido;
import com.mediconecta.modelo.Notificacion;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.TipoNotificacion;
import com.mediconecta.modelo.Turno;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lo que hacen los consumidores JMS con el evento "turno confirmado", y la
 * consulta de lo que hicieron.
 *
 * <p><b>Consumidor idempotente.</b> Cada aviso se identifica por
 * {@code (eventoId, tipo)}. Antes de generarlo se consulta si ya existe; si
 * existe, el metodo devuelve {@code false} y no hace nada. Asi una entrega
 * repetida del mismo mensaje (reintento, reenvio del productor) no manda dos
 * mails. La restriccion unica de la tabla cubre el caso de dos entregas
 * simultaneas.</p>
 *
 * <p>Los metodos {@code registrar*} son {@code @PermitAll} porque los invoca el
 * contenedor desde un MDB, sin usuario autenticado; no estan expuestos por REST.
 * Las consultas si exigen rol.</p>
 */
@Stateless
public class GestionNotificaciones {

    /** Con cuanta anticipacion se programa el recordatorio al paciente. */
    static final Duration ANTICIPACION_RECORDATORIO = Duration.ofHours(24);

    /** Cuantos mensajes fallidos devuelve como maximo la consulta. */
    static final int MAXIMO_FALLIDOS = 100;

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @Inject
    private RepositorioNotificaciones repositorioNotificaciones;

    @Inject
    private RepositorioMensajesFallidos repositorioMensajesFallidos;

    @Inject
    private RepositorioTurnos repositorioTurnos;

    @Inject
    private GestionTurnos gestionTurnos;

    @Inject
    private NotificadorTurnos notificadorTurnos;

    private Clock reloj = Clock.systemDefaultZone();

    // ---------------------------------------------------- consumidores JMS ---

    /** Consumidor de la cola: mail de confirmacion al paciente. */
    @PermitAll
    public boolean registrarConfirmacion(String eventoId, Long turnoId) {
        if (repositorioNotificaciones.existe(eventoId, TipoNotificacion.CONFIRMACION)) {
            return false;
        }
        Turno turno = turno(turnoId);
        Paciente paciente = turno.getPaciente();
        String destinatario = paciente.getEmail() != null && !paciente.getEmail().isBlank()
                ? paciente.getEmail()
                : paciente.getNombre() + " (sin email registrado)";
        String mensaje = "Hola " + paciente.getNombre() + ": tu turno de " + turno.getEspecialidad()
                + " con " + turno.getProfesional().getNombre() + " quedo confirmado para el "
                + turno.getFechaHora().format(DIA) + " a las " + turno.getFechaHora().format(HORA) + ".";
        guardar(eventoId, TipoNotificacion.CONFIRMACION, CanalNotificacion.EMAIL, turno, destinatario, mensaje, null);
        return true;
    }

    /** Suscriptor del topico: recordatorio al paciente 24 horas antes. */
    @PermitAll
    public boolean registrarRecordatorio(String eventoId, Long turnoId) {
        if (repositorioNotificaciones.existe(eventoId, TipoNotificacion.RECORDATORIO)) {
            return false;
        }
        Turno turno = turno(turnoId);
        Paciente paciente = turno.getPaciente();
        String destinatario = paciente.getTelefono() != null && !paciente.getTelefono().isBlank()
                ? paciente.getTelefono()
                : paciente.getNombre() + " (sin telefono registrado)";
        String mensaje = "Recordatorio MediConecta: el " + turno.getFechaHora().format(DIA) + " a las "
                + turno.getFechaHora().format(HORA) + " tenes turno con " + turno.getProfesional().getNombre()
                + ". Si no podes asistir, cancelalo desde Mis turnos.";
        guardar(eventoId, TipoNotificacion.RECORDATORIO, CanalNotificacion.SMS, turno, destinatario, mensaje,
                momentoDelRecordatorio(turno.getFechaHora(), LocalDateTime.now(reloj)));
        return true;
    }

    /** Otro suscriptor del topico: aviso en la agenda del profesional. */
    @PermitAll
    public boolean registrarAvisoProfesional(String eventoId, Long turnoId) {
        if (repositorioNotificaciones.existe(eventoId, TipoNotificacion.AVISO_PROFESIONAL)) {
            return false;
        }
        Turno turno = turno(turnoId);
        String mensaje = "Nuevo turno: " + turno.getPaciente().getNombre() + " el "
                + turno.getFechaHora().format(DIA) + " a las " + turno.getFechaHora().format(HORA)
                + (turno.getMotivo() == null || turno.getMotivo().isBlank() ? "." : " (motivo: " + turno.getMotivo() + ").");
        guardar(eventoId, TipoNotificacion.AVISO_PROFESIONAL, CanalNotificacion.AGENDA, turno,
                turno.getProfesional().getNombre(), mensaje, null);
        return true;
    }

    /** Monitor de la DLQ: deja constancia de un mensaje que agoto sus reintentos. */
    @PermitAll
    public boolean registrarMensajeFallido(String mensajeId, String eventoId, String destinoOriginal, String cuerpo) {
        if (repositorioMensajesFallidos.existe(mensajeId)) {
            return false;
        }
        repositorioMensajesFallidos.guardar(
                new MensajeFallido(mensajeId, eventoId, destinoOriginal, cuerpo, LocalDateTime.now(reloj)));
        return true;
    }

    // -------------------------------------------------------------- consultas ---

    /**
     * Avisos generados para un turno. El control de quien puede ver el turno lo
     * hace {@link GestionTurnos#obtenerTurno}: un PACIENTE solo ve los suyos.
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Notificacion> notificacionesDeTurno(Long turnoId) {
        gestionTurnos.obtenerTurno(turnoId);
        return repositorioNotificaciones.listarPorTurno(turnoId);
    }

    @RolesAllowed(Roles.ADMIN)
    public List<MensajeFallido> mensajesFallidos() {
        return repositorioMensajesFallidos.listarRecientes(MAXIMO_FALLIDOS);
    }

    /** Demostracion de reintentos + DLQ: manda un mensaje que ningun consumidor entiende. */
    @RolesAllowed(Roles.ADMIN)
    public String enviarMensajeDefectuoso() {
        return notificadorTurnos.enviarMensajeDefectuoso();
    }

    // ------------------------------------------------------------------------

    /**
     * 24 horas antes del turno; si eso ya paso (turno para dentro de unas
     * horas), en este momento.
     */
    static LocalDateTime momentoDelRecordatorio(LocalDateTime turno, LocalDateTime ahora) {
        LocalDateTime ideal = turno.minus(ANTICIPACION_RECORDATORIO);
        return ideal.isBefore(ahora) ? ahora : ideal;
    }

    private Turno turno(Long turnoId) {
        // Sin el turno no se puede armar el aviso: la excepcion hace que el
        // mensaje se reintente y, si sigue sin aparecer, termine en la DLQ.
        return repositorioTurnos.buscarPorId(turnoId).orElseThrow(() -> NoEncontradoException.turno(turnoId));
    }

    private void guardar(String eventoId, TipoNotificacion tipo, CanalNotificacion canal, Turno turno,
                         String destinatario, String mensaje, LocalDateTime programadaPara) {
        Notificacion notificacion = new Notificacion(eventoId, tipo, canal, turno.getId(), destinatario, mensaje,
                LocalDateTime.now(reloj));
        notificacion.setProgramadaPara(programadaPara);
        repositorioNotificaciones.guardar(notificacion);
    }
}
