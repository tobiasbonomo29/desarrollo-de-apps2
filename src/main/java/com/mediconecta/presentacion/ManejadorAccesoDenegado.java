package com.mediconecta.presentacion;

import jakarta.ejb.EJBAccessException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce a {@code 403 Forbidden} el rechazo que produce el contenedor de EJB
 * cuando un usuario autenticado invoca un metodo que su rol no tiene permitido.
 *
 * <p>La decision de rechazar <b>no</b> se toma aca: la toma el contenedor al
 * evaluar la anotacion {@code @RolesAllowed} del metodo de negocio. Esta clase
 * solo se ocupa de que el cliente reciba un Problem Details
 * ({@code urn:mediconecta:problema:acceso-denegado}) en lugar de una pagina de
 * error HTML.</p>
 */
@Provider
public class ManejadorAccesoDenegado implements ExceptionMapper<EJBAccessException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(EJBAccessException excepcion) {
        return Problemas.respuesta(excepcion, uriInfo);
    }
}
