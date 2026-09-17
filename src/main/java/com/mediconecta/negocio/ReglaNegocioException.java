package com.mediconecta.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Una regla del dominio no se cumplio.
 *
 * <p>No menciona HTTP a proposito: es la capa de presentacion la que la traduce
 * a un 400 mediante un {@code ExceptionMapper}. Gracias a eso el mismo negocio
 * puede exponerse manana por SOAP o consumirse desde una cola sin cambios.</p>
 *
 * <p>{@code @ApplicationException(rollback = false)} le dice al contenedor que
 * esto es un resultado de negocio previsto y no una falla de sistema: sin esa
 * anotacion envolveria la excepcion en {@code EJBException}, escribiria un stack
 * trace completo en el log, marcaria la transaccion para rollback y descartaria
 * la instancia del pool.</p>
 */
@ApplicationException(rollback = false)
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
