package com.mediconecta.negocio;

import jakarta.ejb.ApplicationException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Un sistema externo (el legado por SOAP, el partner por REST o el proveedor de
 * IA) no respondio o respondio con error.
 *
 * <p>A diferencia de {@link ReglaNegocioException}, esto no es un resultado
 * de negocio previsto sino una falla de infraestructura externa: por eso se
 * mapea a {@code 502 Bad Gateway} en lugar de {@code 400}, y por eso
 * {@code @ApplicationException(rollback = true)} marca la transaccion para
 * deshacerse si el llamador no la atrapa.</p>
 *
 * <p>Si la causa fue que se agoto el tiempo de espera (los adaptadores tienen
 * timeouts de conexion y de respuesta), {@link #esPorTiempo()} lo indica y la
 * presentacion responde {@code 504 Gateway Timeout}.</p>
 */
@ApplicationException(rollback = true)
public class IntegracionExternaException extends RuntimeException {

    private final boolean porTiempo;

    public IntegracionExternaException(String mensaje) {
        super(mensaje);
        this.porTiempo = false;
    }

    public IntegracionExternaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.porTiempo = fueTiempoAgotado(causa);
    }

    /** {@code true} si la causa fue un timeout de conexion o de respuesta. */
    public boolean esPorTiempo() {
        return porTiempo;
    }

    /** Recorre la cadena de causas buscando una excepcion de tiempo agotado. */
    public static boolean fueTiempoAgotado(Throwable causa) {
        for (Throwable actual = causa; actual != null; actual = actual.getCause()) {
            if (actual instanceof SocketTimeoutException
                    || actual instanceof HttpTimeoutException
                    || actual instanceof TimeoutException) {
                return true;
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return false;
    }
}
