package com.mediconecta.presentacion;

import com.mediconecta.modelo.MensajeFallido;
import com.mediconecta.negocio.GestionNotificaciones;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Operacion de la mensajeria JMS (solo ADMIN; el control esta en
 * {@link GestionNotificaciones}).
 *
 * <ul>
 *   <li>{@code GET /api/mensajeria/mensajes-fallidos}: lo que termino en la
 *       DLQ despues de agotar los reintentos.</li>
 *   <li>{@code POST /api/mensajeria/mensaje-defectuoso}: manda un mensaje que
 *       ningun consumidor entiende, para ver en vivo los reintentos y la DLQ.
 *       Responde {@code 202 Accepted}: el resultado es asincronico.</li>
 * </ul>
 */
@Path("/mensajeria")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoMensajeria {

    @Inject
    private GestionNotificaciones gestionNotificaciones;

    @GET
    @Path("/mensajes-fallidos")
    public List<MensajeFallido> mensajesFallidos() {
        return gestionNotificaciones.mensajesFallidos();
    }

    @POST
    @Path("/mensaje-defectuoso")
    public Response enviarMensajeDefectuoso() {
        String eventoId = gestionNotificaciones.enviarMensajeDefectuoso();
        return Response.accepted(Map.of("eventoId", eventoId)).build();
    }
}
