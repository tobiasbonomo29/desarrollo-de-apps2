package com.mediconecta.presentacion;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/**
 * Prueba de vida del contenedor.
 *
 * <p>Es el unico recurso publico: en {@code web.xml} tiene una
 * {@code security-constraint} sin {@code auth-constraint}, de modo que se puede
 * comprobar que el WAR esta desplegado sin necesidad de iniciar sesion.</p>
 */
@Path("/salud")
public class RecursoSalud {

    @GET
    @Produces("text/plain;charset=UTF-8")
    public String saludar() {
        return "¡Hola desde el contenedor!";
    }
}
