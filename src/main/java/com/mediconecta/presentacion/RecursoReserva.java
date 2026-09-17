package com.mediconecta.presentacion;

import com.mediconecta.negocio.EstadoReserva;
import com.mediconecta.negocio.Fechas;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API REST de la reserva guiada (componente stateful).
 *
 * <p>Cada endpoint es una invocacion distinta al <b>mismo</b> EJB stateful,
 * localizado a traves de {@link ConversacionDeReserva}. Fijarse que ningun paso
 * recibe lo elegido en los pasos anteriores: eso es lo que demuestra que el
 * estado vive en el servidor.</p>
 */
@Path("/reserva")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoReserva {

    @Inject
    private ConversacionDeReserva conversacion;

    /** Estado actual de la conversacion, tal como lo recuerda el servidor. */
    @GET
    public EstadoReserva estado() {
        return conversacion.asistente().estado();
    }

    @POST
    @Path("/iniciar")
    public EstadoReserva iniciar(PasoReserva paso) {
        conversacion.cerrar();
        return conversacion.asistente().iniciar(paso == null ? null : paso.getDocumentoPaciente());
    }

    @PUT
    @Path("/especialidad")
    public EstadoReserva elegirEspecialidad(PasoReserva paso) {
        return conversacion.asistente().elegirEspecialidad(paso == null ? null : paso.getEspecialidad());
    }

    @PUT
    @Path("/profesional")
    public EstadoReserva elegirProfesional(PasoReserva paso) {
        return conversacion.asistente().elegirProfesional(paso == null ? null : paso.getProfesionalId());
    }

    @PUT
    @Path("/horario")
    public EstadoReserva elegirHorario(PasoReserva paso) {
        return conversacion.asistente().elegirHorario(paso == null ? null : paso.getFechaHora());
    }

    /** Retrocede un paso. La conversacion la mueve el servidor, no el navegador. */
    @PUT
    @Path("/volver")
    public EstadoReserva volver() {
        return conversacion.asistente().volver();
    }

    /** Horarios libres del profesional que la conversacion ya recuerda. */
    @GET
    @Path("/horarios")
    public List<LocalDateTime> horarios(@QueryParam("fecha") String fecha) {
        return conversacion.asistente().horariosDisponibles(Fechas.dia(fecha));
    }

    /**
     * Confirma la reserva y cierra la conversacion.
     *
     * <p>Se guarda el estado <b>antes</b> de cerrar porque despues de
     * {@code cerrar()} el EJB ya no existe: el contenedor ejecuto su
     * {@code @Remove} y su {@code @PreDestroy}.</p>
     */
    @POST
    @Path("/confirmar")
    public EstadoReserva confirmar() {
        EstadoReserva resultado = conversacion.asistente().confirmar();
        conversacion.cerrar();
        return resultado;
    }

    /** Abandona la reserva en curso. */
    @DELETE
    public EstadoReserva cancelar() {
        conversacion.cerrar();
        return conversacion.asistente().estado();
    }
}
