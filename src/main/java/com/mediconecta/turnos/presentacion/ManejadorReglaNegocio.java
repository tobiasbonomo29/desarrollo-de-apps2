package com.mediconecta.turnos.presentacion;

import com.mediconecta.turnos.negocio.ReglaNegocioException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce una violacion de regla de negocio a una respuesta HTTP 400.
 *
 * <p>Este manejador es el que permite que la capa de negocio no sepa nada de
 * HTTP: el negocio lanza una excepcion en su propio lenguaje y la capa de
 * presentacion decide como se ve eso "por afuera". Ademas evita repetir
 * bloques {@code try/catch} en cada metodo de {@link RecursoTurnos}.</p>
 */
@Provider
public class ManejadorReglaNegocio implements ExceptionMapper<ReglaNegocioException> {

    @Override
    public Response toResponse(ReglaNegocioException excepcion) {
        // 400 Bad Request: los datos que mando el cliente no son validos.
        int codigo = Response.Status.BAD_REQUEST.getStatusCode();
        return Response.status(codigo)
                .entity(new ErrorRespuesta(excepcion.getMessage(), codigo))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
