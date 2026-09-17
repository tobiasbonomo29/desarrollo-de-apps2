package com.mediconecta.presentacion;

import jakarta.ejb.EJBAccessException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce a {@code 403 Forbidden} el rechazo que produce el contenedor de EJB
 * cuando un usuario autenticado invoca un metodo que su rol no tiene permitido.
 *
 * <p>La decision de rechazar <b>no</b> se toma aca: la toma el contenedor al
 * evaluar la anotacion {@code @RolesAllowed} del metodo de negocio. Esta clase
 * solo se ocupa de que el cliente reciba un JSON entendible en lugar de una
 * pagina de error HTML.</p>
 */
@Provider
public class ManejadorAccesoDenegado implements ExceptionMapper<EJBAccessException> {

    @Override
    public Response toResponse(EJBAccessException excepcion) {
        int codigo = Response.Status.FORBIDDEN.getStatusCode();
        return Response.status(codigo)
                .entity(new ErrorRespuesta(
                        "Su rol no tiene permiso para realizar esta operacion.", codigo))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
