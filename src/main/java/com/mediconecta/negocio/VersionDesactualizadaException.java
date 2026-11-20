package com.mediconecta.negocio;

import jakarta.ejb.ApplicationException;

/**
 * El cliente pidio modificar un recurso indicando la version que conocia
 * ({@code If-Match} con el ETag) y esa version ya no es la actual: alguien lo
 * modifico en el medio. La presentacion responde {@code 412 Precondition
 * Failed}; el cliente tiene que volver a leer el recurso antes de reintentar.
 * Es el control de concurrencia optimista de la API.
 */
@ApplicationException(rollback = true)
public class VersionDesactualizadaException extends RuntimeException {

    public VersionDesactualizadaException(String mensaje) {
        super(mensaje);
    }

    /** Si se indico una version esperada y no coincide con la actual, lanza la excepcion. */
    static void exigir(long actual, Long esperada, String recurso) {
        if (esperada != null && esperada != actual) {
            throw new VersionDesactualizadaException("El " + recurso + " cambio desde que lo consulto (version "
                    + esperada + ", actual " + actual + "). Vuelva a cargarlo antes de modificarlo.");
        }
    }
}
