package com.mediconecta.presentacion;

import com.mediconecta.negocio.NoEncontradoException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Traduce "la entidad no existe" a {@code 404 Not Found}. */
@Provider
public class ManejadorNoEncontrado implements ExceptionMapper<NoEncontradoException> {

    @Override
    public Response toResponse(NoEncontradoException excepcion) {
        int codigo = Response.Status.NOT_FOUND.getStatusCode();
        return Response.status(codigo)
                .entity(new ErrorRespuesta(excepcion.getMessage(), codigo))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
