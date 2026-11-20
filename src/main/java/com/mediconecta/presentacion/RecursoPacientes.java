package com.mediconecta.presentacion;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Turno;
import com.mediconecta.negocio.GestionPacientes;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;

/** API REST del componente Gestion de Pacientes. */
@Path("/pacientes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoPacientes {

    @Inject
    private GestionPacientes gestionPacientes;

    @Context
    private UriInfo uriInfo;

    @Context
    private Request request;

    @GET
    public List<Paciente> listar(@QueryParam("q") String texto) {
        return gestionPacientes.buscar(texto);
    }

    /** Ficha del paciente asociado al usuario que tiene la sesion abierta. */
    @GET
    @Path("/mi-ficha")
    public Paciente miFicha() {
        return gestionPacientes.miFicha();
    }

    /** La ficha, con ETag ({@code If-None-Match} -> 304). Un PACIENTE solo la propia. */
    @GET
    @Path("/{documento}")
    public Response obtener(@PathParam("documento") String documento) {
        Paciente paciente = gestionPacientes.consultarFicha(documento);
        EntityTag etiqueta = Etiquetas.de("paciente", paciente.getDocumento(), paciente.getVersion());
        Response.ResponseBuilder noCambio = request.evaluatePreconditions(etiqueta);
        if (noCambio != null) {
            return noCambio.tag(etiqueta).build();
        }
        return Response.ok(paciente).tag(etiqueta).build();
    }

    @POST
    public Response registrar(Paciente paciente) {
        Paciente creado = gestionPacientes.registrarPaciente(paciente);

        URI ubicacion = uriInfo.getAbsolutePathBuilder()
                .path(creado.getDocumento())
                .build();

        return Response.created(ubicacion).entity(creado).build();
    }

    /** Con {@code If-Match} (opcional) solo actualiza si la ficha sigue en esa version; si no, 412. */
    @PUT
    @Path("/{documento}")
    public Response actualizar(@PathParam("documento") String documento, Paciente datos,
                               @HeaderParam(HttpHeaders.IF_MATCH) String siCoincide) {
        Paciente paciente = gestionPacientes.actualizarDatos(documento, datos,
                Etiquetas.versionEsperada(siCoincide, "paciente", documento));
        return Response.ok(paciente)
                .tag(Etiquetas.de("paciente", paciente.getDocumento(), paciente.getVersion()))
                .build();
    }

    @GET
    @Path("/{documento}/turnos")
    public List<Turno> historial(@PathParam("documento") String documento) {
        return gestionPacientes.historialDeTurnos(documento);
    }
}
