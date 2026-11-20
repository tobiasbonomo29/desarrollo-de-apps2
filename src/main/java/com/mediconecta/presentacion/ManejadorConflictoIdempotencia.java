package com.mediconecta.presentacion;

import com.mediconecta.negocio.ConflictoIdempotenciaException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce un conflicto de {@code Idempotency-Key}: {@code 409} si otro pedido
 * con la misma clave esta en curso, {@code 422} si la clave ya se uso con otro
 * cuerpo.
 */
@Provider
public class ManejadorConflictoIdempotencia implements ExceptionMapper<ConflictoIdempotenciaException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(ConflictoIdempotenciaException excepcion) {
        return Problemas.respuesta(excepcion, uriInfo);
    }
}
