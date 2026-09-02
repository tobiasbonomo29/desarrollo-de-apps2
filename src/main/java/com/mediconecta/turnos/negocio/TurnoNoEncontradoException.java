package com.mediconecta.turnos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Se lanza cuando se pide un turno cuyo identificador no existe.
 *
 * <p>Se separa de {@link ReglaNegocioException} porque significa algo distinto
 * y termina en una respuesta HTTP distinta (404 en lugar de 400).</p>
 *
 * <p>Lleva {@code @ApplicationException} por el mismo motivo que
 * {@link ReglaNegocioException}: pedir un id inexistente es un caso previsto,
 * no un error de sistema, y no debe ensuciar el log ni provocar rollback.</p>
 */
@ApplicationException(rollback = false)
public class TurnoNoEncontradoException extends RuntimeException {

    public TurnoNoEncontradoException(Long id) {
        super("No existe un turno con id " + id);
    }
}
