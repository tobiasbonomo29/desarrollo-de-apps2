package com.mediconecta.presentacion;

import com.mediconecta.modelo.Turno;
import com.mediconecta.negocio.FachadaReservas;
import com.mediconecta.negocio.GestionTurnos;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;

/**
 * API REST de turnos.
 *
 * <p>Esta clase no valida nada ni decide nada: traduce HTTP a llamadas de
 * negocio y de vuelta. El alta va por {@link FachadaReservas} porque reservar
 * involucra a tres componentes, y coordinarlos no es tarea de la presentacion.</p>
 */
@Path("/turnos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoTurnos {

    @Inject
    private GestionTurnos gestionTurnos;

    @Inject
    private FachadaReservas fachadaReservas;

    @Context
    private UriInfo uriInfo;

    @GET
    public List<Turno> listar() {
        return gestionTurnos.listarTurnos();
    }

    @GET
    @Path("/{id}")
    public Turno obtener(@PathParam("id") Long id) {
        return gestionTurnos.obtenerTurno(id);
    }

    @POST
    public Response crear(PedidoTurno pedido) {
        Turno creado = fachadaReservas.reservar(pedido == null ? null : pedido.aSolicitud());

        URI ubicacion = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(creado.getId()))
                .build();

        return Response.created(ubicacion).entity(creado).build();
    }

    @PUT
    @Path("/{id}/cancelar")
    public Turno cancelar(@PathParam("id") Long id) {
        return gestionTurnos.cancelarTurno(id);
    }

    /** Saca un turno cancelado de la agenda operativa sin borrarlo. */
    @PUT
    @Path("/{id}/archivar")
    public Turno archivar(@PathParam("id") Long id) {
        return gestionTurnos.archivarTurno(id);
    }
}
