package com.mediconecta.turnos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Se lanza cuando una operacion viola una regla de negocio de MediConecta
 * (por ejemplo: pedir un turno en el pasado o en un horario ya ocupado).
 *
 * <p>Es una excepcion propia de la capa de negocio y no menciona HTTP ni
 * codigos de estado: traducirla a una respuesta 400 es responsabilidad de la
 * capa de presentacion. Asi el negocio se puede reutilizar tal cual desde un
 * servicio SOAP o desde un consumidor de mensajeria en las proximas entregas.</p>
 *
 * <p><b>Por que lleva {@code @ApplicationException(rollback = false)}:</b> sin
 * esa anotacion el contenedor de EJB considera a cualquier {@code
 * RuntimeException} un fallo de <i>sistema</i>: la envuelve en {@code
 * EJBException}, escribe un stack trace completo en el log, marca la
 * transaccion para rollback y descarta la instancia del pool. Pero esto no es
 * una falla del sistema: es un resultado de negocio previsto y perfectamente
 * normal. Al declararla excepcion de aplicacion, el contenedor la deja pasar
 * tal cual, sin ruido en el log y sin rollback, y la capa de presentacion la
 * puede traducir al codigo HTTP que corresponde.</p>
 */
@ApplicationException(rollback = false)
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
