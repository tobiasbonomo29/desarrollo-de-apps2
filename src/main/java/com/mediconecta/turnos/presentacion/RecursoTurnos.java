package com.mediconecta.turnos.presentacion;

import com.mediconecta.turnos.modelo.Turno;
import com.mediconecta.turnos.negocio.GestionTurnos;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
 * Capa de presentacion del componente Gestion de Turnos: expone las
 * operaciones del negocio como una API REST.
 *
 * <p>Su unica responsabilidad es <b>traducir</b>: pasar de HTTP/JSON al lenguaje
 * del negocio y volver. No valida reglas, no ordena listas y no toca datos; solo
 * delega en {@link GestionTurnos} y arma la respuesta HTTP adecuada. Si esta
 * clase se borrara y se reemplazara por un servicio SOAP, el comportamiento del
 * sistema seria exactamente el mismo.</p>
 *
 * <p>URL base: {@code http://localhost:8080/mediconecta/api/turnos}</p>
 */
@Path("/turnos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoTurnos {

    /** Componente de negocio. La presentacion depende del negocio, nunca al reves. */
    @Inject
    private GestionTurnos gestionTurnos;

    /**
     * Datos de la URL del pedido en curso. Los aporta el contenedor y sirven
     * para construir la cabecera {@code Location} sin escribir la URL a mano.
     */
    @Context
    private UriInfo uriInfo;

    /**
     * Lista todos los turnos.
     *
     * @return {@code 200 OK} con el arreglo JSON de turnos
     */
    @GET
    public List<Turno> listar() {
        return gestionTurnos.listarTurnos();
    }

    /**
     * Devuelve un turno por su id.
     *
     * <p>Si no existe, el negocio lanza {@code TurnoNoEncontradoException} y el
     * {@link ManejadorTurnoNoEncontrado} la convierte en un {@code 404}.</p>
     *
     * @param id identificador del turno
     * @return {@code 200 OK} con el turno en JSON
     */
    @GET
    @Path("/{id}")
    public Turno obtener(@PathParam("id") Long id) {
        return gestionTurnos.obtenerTurno(id);
    }

    /**
     * Crea un turno nuevo.
     *
     * <p>Responde {@code 201 Created} con la cabecera {@code Location} apuntando
     * al recurso recien creado, que es lo que corresponde en REST para un alta.
     * Si los datos violan una regla de negocio, el
     * {@link ManejadorReglaNegocio} responde {@code 400}.</p>
     *
     * @param turno datos del turno recibidos en el cuerpo JSON
     * @return {@code 201 Created} con el turno ya creado y su id
     */
    @POST
    public Response crear(Turno turno) {
        Turno creado = gestionTurnos.crearTurno(turno);

        // Se parte de la URL real del pedido y se le agrega el id. Armar el
        // texto a mano ("/api/turnos/" + id) producia /api/api/turnos/4, porque
        // Response.created() resuelve las rutas relativas contra la base de la
        // aplicacion, que ya incluye /api.
        URI ubicacion = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(creado.getId()))
                .build();

        return Response.created(ubicacion).entity(creado).build();
    }
}
