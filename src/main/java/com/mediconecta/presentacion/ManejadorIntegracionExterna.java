package com.mediconecta.presentacion;

import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce una falla de un sistema externo (SOAP legado o partner REST) a
 * {@code 502 Bad Gateway}, para distinguirla de un {@code 400} por un pedido
 * mal formado.
 */
@Provider
public class ManejadorIntegracionExterna implements ExceptionMapper<IntegracionExternaException> {

    @Override
    public Response toResponse(IntegracionExternaException excepcion) {
        int codigo = Response.Status.BAD_GATEWAY.getStatusCode();
        return Response.status(codigo)
                .entity(new ErrorRespuesta(excepcion.getMessage(), codigo))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
