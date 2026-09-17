package com.mediconecta.presentacion;

import com.mediconecta.negocio.ReglaNegocioException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce una regla de negocio incumplida a {@code 400 Bad Request}.
 *
 * <p>Es el mecanismo que mantiene al negocio libre de cualquier referencia a
 * HTTP y evita repetir {@code try/catch} en cada metodo de cada recurso.</p>
 */
@Provider
public class ManejadorReglaNegocio implements ExceptionMapper<ReglaNegocioException> {

    @Override
    public Response toResponse(ReglaNegocioException excepcion) {
        int codigo = Response.Status.BAD_REQUEST.getStatusCode();
        return Response.status(codigo)
                .entity(new ErrorRespuesta(excepcion.getMessage(), codigo))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
