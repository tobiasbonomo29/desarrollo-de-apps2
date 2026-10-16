package com.mediconecta.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Un sistema externo (el legado por SOAP o el partner por REST) no respondio
 * o respondio con error.
 *
 * <p>A diferencia de {@link ReglaNegocioException}, esto no es un resultado
 * de negocio previsto sino una falla de infraestructura externa: por eso se
 * mapea a {@code 502 Bad Gateway} en lugar de {@code 400}, y por eso
 * {@code @ApplicationException(rollback = true)} marca la transaccion para
 * deshacerse si el llamador no la atrapa.</p>
 */
@ApplicationException(rollback = true)
public class IntegracionExternaException extends RuntimeException {

    public IntegracionExternaException(String mensaje) {
        super(mensaje);
    }

    public IntegracionExternaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
