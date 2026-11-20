package com.mediconecta.presentacion;

import com.mediconecta.negocio.VersionDesactualizadaException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** {@code 412 Precondition Failed}: el {@code If-Match} no coincide con la version actual. */
@Provider
public class ManejadorVersionDesactualizada implements ExceptionMapper<VersionDesactualizadaException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(VersionDesactualizadaException excepcion) {
        return Problemas.respuesta(excepcion, uriInfo);
    }
}
