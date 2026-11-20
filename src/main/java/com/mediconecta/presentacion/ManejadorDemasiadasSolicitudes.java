package com.mediconecta.presentacion;

import com.mediconecta.negocio.DemasiadasSolicitudesException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@code 429 Too Many Requests} con {@code Retry-After} (segundos): fuerza bruta
 * sobre el login o demasiadas consultas al asistente de IA.
 */
@Provider
public class ManejadorDemasiadasSolicitudes implements ExceptionMapper<DemasiadasSolicitudesException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(DemasiadasSolicitudesException excepcion) {
        return Response.fromResponse(Problemas.respuesta(excepcion, uriInfo))
                .header(HttpHeaders.RETRY_AFTER, excepcion.segundosDeEspera())
                .build();
    }
}
