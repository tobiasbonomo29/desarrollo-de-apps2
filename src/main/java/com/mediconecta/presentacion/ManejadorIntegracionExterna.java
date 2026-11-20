package com.mediconecta.presentacion;

import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce una falla de un sistema externo (SOAP legado, partner REST o IA) a
 * {@code 502 Bad Gateway}, o a {@code 504 Gateway Timeout} si se agoto el
 * tiempo de espera, para distinguirla de un {@code 400} por un pedido mal
 * formado.
 */
@Provider
public class ManejadorIntegracionExterna implements ExceptionMapper<IntegracionExternaException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(IntegracionExternaException excepcion) {
        return Problemas.respuesta(excepcion, uriInfo);
    }
}
