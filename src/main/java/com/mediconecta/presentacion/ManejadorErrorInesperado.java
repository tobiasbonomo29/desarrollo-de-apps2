package com.mediconecta.presentacion;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Ultima red: cualquier excepcion que no tenga un manejador mas especifico.
 *
 * <p>JAX-RS elige siempre el {@code ExceptionMapper} del tipo mas cercano, asi
 * que este solo actua cuando ningun otro aplica. Responde un {@code 500} en
 * Problem Details sin la traza ni el mensaje interno (que pueden revelar
 * detalles de la implementacion); el error completo queda en el log con el
 * mismo {@code idError} que recibe el cliente.</p>
 */
@Provider
public class ManejadorErrorInesperado implements ExceptionMapper<Exception> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(Exception excepcion) {
        return Problemas.respuesta(excepcion, uriInfo);
    }
}
