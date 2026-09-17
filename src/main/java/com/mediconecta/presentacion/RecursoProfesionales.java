package com.mediconecta.presentacion;

import com.mediconecta.modelo.Disponibilidad;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.negocio.Fechas;
import com.mediconecta.negocio.GestionProfesionales;

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
import java.time.LocalDateTime;
import java.util.List;

/** API REST del componente Gestion de Profesionales. */
@Path("/profesionales")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoProfesionales {

    @Inject
    private GestionProfesionales gestionProfesionales;

    @Context
    private UriInfo uriInfo;

    @GET
    public List<Profesional> listar(@QueryParam("especialidad") String especialidad) {
        return gestionProfesionales.listarPorEspecialidad(especialidad);
    }

    @GET
    @Path("/especialidades")
    public List<String> especialidades() {
        return gestionProfesionales.listarEspecialidades();
    }

    @GET
    @Path("/{id}")
    public Profesional obtener(@PathParam("id") Long id) {
        return gestionProfesionales.obtener(id);
    }

    /** Alta de profesional. El contenedor solo deja pasar al rol ADMIN. */
    @POST
    public Response registrar(Profesional profesional) {
        Profesional creado = gestionProfesionales.registrarProfesional(profesional);

        URI ubicacion = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(creado.getId()))
                .build();

        return Response.created(ubicacion).entity(creado).build();
    }

    /** Redefine la agenda semanal. Reservado a MEDICO y ADMIN. */
    @PUT
    @Path("/{id}/disponibilidad")
    public Profesional definirDisponibilidad(@PathParam("id") Long id, List<Disponibilidad> franjas) {
        return gestionProfesionales.definirDisponibilidad(id, franjas);
    }

    @GET
    @Path("/{id}/horarios-libres")
    public List<LocalDateTime> horariosLibres(@PathParam("id") Long id,
                                              @QueryParam("fecha") String fecha) {
        return gestionProfesionales.horariosLibres(id, Fechas.dia(fecha));
    }
}
