package com.mediconecta.presentacion;

import com.mediconecta.modelo.Notificacion;
import com.mediconecta.modelo.Turno;
import com.mediconecta.negocio.FachadaReservas;
import com.mediconecta.negocio.GestionNotificaciones;
import com.mediconecta.negocio.GestionTurnos;
import com.mediconecta.negocio.ResultadoReserva;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
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

    @Inject
    private GestionNotificaciones gestionNotificaciones;

    static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";

    static final String CABECERA_REPETIDA = "Idempotent-Replayed";

    @Context
    private UriInfo uriInfo;

    @Context
    private Request request;

    @GET
    public List<Turno> listar() {
        return gestionTurnos.listarTurnos();
    }

    /**
     * Un turno, con su ETag. Con {@code If-None-Match} igual al ETag actual
     * responde {@code 304} sin cuerpo (GET condicional).
     */
    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") Long id) {
        Turno turno = gestionTurnos.obtenerTurno(id);
        EntityTag etiqueta = etiqueta(turno);
        Response.ResponseBuilder noCambio = request.evaluatePreconditions(etiqueta);
        if (noCambio != null) {
            return noCambio.tag(etiqueta).build();
        }
        return Response.ok(turno).tag(etiqueta).build();
    }

    private static EntityTag etiqueta(Turno turno) {
        return Etiquetas.de("turno", turno.getId(), turno.getVersion());
    }

    private static Long version(String siCoincide, Long id) {
        return Etiquetas.versionEsperada(siCoincide, "turno", id);
    }

    /**
     * Alta de un turno. Acepta la cabecera opcional {@code Idempotency-Key}: si
     * el mismo pedido llega dos veces con la misma clave (reintento por un
     * corte de red, doble clic), el segundo devuelve el turno del primero con
     * {@code Idempotent-Replayed: true} en lugar de crear otro. Las reglas estan
     * en {@link FachadaReservas#reservarConClave}.
     */
    @POST
    public Response crear(PedidoTurno pedido, @HeaderParam(CABECERA_IDEMPOTENCIA) String clave) {
        ResultadoReserva resultado = fachadaReservas.reservarConClave(
                pedido == null ? null : pedido.aSolicitud(), clave);
        Turno creado = resultado.turno();

        URI ubicacion = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(creado.getId()))
                .build();

        Response.ResponseBuilder respuesta = Response.created(ubicacion).entity(creado);
        if (resultado.repetida()) {
            respuesta.header(CABECERA_REPETIDA, "true");
        }
        return respuesta.build();
    }

    /** Avisos que generaron los consumidores JMS para este turno. */
    @GET
    @Path("/{id}/notificaciones")
    public List<Notificacion> notificaciones(@PathParam("id") Long id) {
        return gestionNotificaciones.notificacionesDeTurno(id);
    }

    /** Con {@code If-Match} (opcional) solo cancela si el turno sigue en esa version; si no, 412. */
    @PUT
    @Path("/{id}/cancelar")
    public Response cancelar(@PathParam("id") Long id, @HeaderParam(HttpHeaders.IF_MATCH) String siCoincide) {
        Turno turno = gestionTurnos.cancelarTurno(id, version(siCoincide, id));
        return Response.ok(turno).tag(etiqueta(turno)).build();
    }

    /** Registra la atencion: PROGRAMADO -> ATENDIDO (409 desde otro estado). */
    @PUT
    @Path("/{id}/atender")
    public Response atender(@PathParam("id") Long id, @HeaderParam(HttpHeaders.IF_MATCH) String siCoincide) {
        Turno turno = gestionTurnos.atenderTurno(id, version(siCoincide, id));
        return Response.ok(turno).tag(etiqueta(turno)).build();
    }

    /** Saca un turno cancelado de la agenda operativa sin borrarlo. */
    @PUT
    @Path("/{id}/archivar")
    public Response archivar(@PathParam("id") Long id, @HeaderParam(HttpHeaders.IF_MATCH) String siCoincide) {
        Turno turno = gestionTurnos.archivarTurno(id, version(siCoincide, id));
        return Response.ok(turno).tag(etiqueta(turno)).build();
    }
}
