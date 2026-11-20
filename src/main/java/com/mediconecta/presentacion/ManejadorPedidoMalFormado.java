package com.mediconecta.presentacion;

import jakarta.ws.rs.ProcessingException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Un cuerpo JSON que no se puede leer (sintaxis invalida, un texto donde va un
 * numero...) se responde {@code 400} con
 * {@code urn:mediconecta:problema:pedido-invalido}, no con un 500.
 */
@Provider
public class ManejadorPedidoMalFormado implements ExceptionMapper<ProcessingException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(ProcessingException excepcion) {
        return Problemas.respuesta(excepcion, uriInfo);
    }
}
