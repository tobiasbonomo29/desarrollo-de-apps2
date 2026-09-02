package com.mediconecta.turnos.presentacion;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/**
 * Recurso de verificacion de vida ("health check") de la aplicacion.
 *
 * <p>Es la prueba que pide la guia de la catedra para demostrar que el
 * componente esta realmente desplegado y respondiendo dentro del contenedor.
 * No toca la capa de negocio a proposito: si este endpoint responde, se sabe
 * que WildFly levanto el WAR y que JAX-RS quedo activo, sin que el resultado
 * dependa de que el resto del sistema funcione.</p>
 *
 * <p>URL: {@code GET http://localhost:8080/mediconecta/api/salud}</p>
 */
@Path("/salud")
public class RecursoSalud {

    /**
     * Devuelve el mensaje de vida del contenedor.
     *
     * <p>Se declara {@code charset=UTF-8} de forma explicita para que el signo
     * de apertura del mensaje se vea correctamente en el navegador y no como
     * caracteres raros.</p>
     *
     * @return el texto {@code ¡Hola desde el contenedor!}
     */
    @GET
    @Produces("text/plain;charset=UTF-8")
    public String saludar() {
        return "¡Hola desde el contenedor!";
    }
}
