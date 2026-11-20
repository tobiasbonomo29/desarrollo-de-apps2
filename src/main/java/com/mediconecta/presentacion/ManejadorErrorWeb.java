package com.mediconecta.presentacion;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Errores que genera JAX-RS antes de llegar a un recurso: ruta inexistente
 * (404), metodo no permitido (405), tipo de contenido no soportado (415)...
 *
 * <p>Sin este manejador el servidor responderia con su propia pagina HTML. Se
 * conserva la cabecera {@code Allow} del 405, que le dice al cliente que
 * metodos si acepta el recurso.</p>
 */
@Provider
public class ManejadorErrorWeb implements ExceptionMapper<WebApplicationException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException excepcion) {
        Response problema = Problemas.respuesta(excepcion, uriInfo);
        Object permitidos = excepcion.getResponse().getHeaders().getFirst(HttpHeaders.ALLOW);
        if (permitidos == null) {
            return problema;
        }
        return Response.fromResponse(problema).header(HttpHeaders.ALLOW, permitidos).build();
    }
}
