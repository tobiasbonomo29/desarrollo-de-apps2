package com.mediconecta.presentacion;

import com.mediconecta.negocio.CredencialesInvalidasException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Traduce credenciales incorrectas a {@code 401 Unauthorized} (Problem Details). */
@Provider
public class ManejadorCredencialesInvalidas implements ExceptionMapper<CredencialesInvalidasException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(CredencialesInvalidasException excepcion) {
        return Problemas.respuesta(excepcion, uriInfo);
    }
}
