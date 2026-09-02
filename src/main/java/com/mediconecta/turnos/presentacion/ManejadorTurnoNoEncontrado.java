package com.mediconecta.turnos.presentacion;

import com.mediconecta.turnos.negocio.TurnoNoEncontradoException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce "el turno no existe" a una respuesta HTTP 404.
 *
 * <p>Se separa de {@link ManejadorReglaNegocio} porque el significado es otro:
 * el pedido esta bien formado, pero el recurso solicitado no existe.</p>
 */
@Provider
public class ManejadorTurnoNoEncontrado implements ExceptionMapper<TurnoNoEncontradoException> {

    @Override
    public Response toResponse(TurnoNoEncontradoException excepcion) {
        // 404 Not Found: el id pedido no corresponde a ningun turno.
        int codigo = Response.Status.NOT_FOUND.getStatusCode();
        return Response.status(codigo)
                .entity(new ErrorRespuesta(excepcion.getMessage(), codigo))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
