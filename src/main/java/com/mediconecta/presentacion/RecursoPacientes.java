package com.mediconecta.presentacion;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Turno;
import com.mediconecta.negocio.GestionPacientes;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
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

    @GET
    @Path("/{documento}")
    public Paciente obtener(@PathParam("documento") String documento) {
        return gestionPacientes.buscarPorDocumento(documento);
    }

    @POST
    public Response registrar(Paciente paciente) {
        Paciente creado = gestionPacientes.registrarPaciente(paciente);

        URI ubicacion = uriInfo.getAbsolutePathBuilder()
                .path(creado.getDocumento())
                .build();

        return Response.created(ubicacion).entity(creado).build();
    }

    @PUT
    @Path("/{documento}")
    public Paciente actualizar(@PathParam("documento") String documento, Paciente datos) {
        return gestionPacientes.actualizarDatos(documento, datos);
    }

    @GET
    @Path("/{documento}/turnos")
    public List<Turno> historial(@PathParam("documento") String documento) {
        return gestionPacientes.historialDeTurnos(documento);
    }
}
