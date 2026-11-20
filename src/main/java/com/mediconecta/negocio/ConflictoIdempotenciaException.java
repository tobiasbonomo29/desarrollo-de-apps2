package com.mediconecta.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Un pedido con {@code Idempotency-Key} no se puede procesar.
 *
 * <ul>
 *   <li>{@link Motivo#EN_CURSO}: otro pedido con la misma clave se esta
 *       procesando en este momento (o termino entre la consulta y el alta).
 *       La presentacion responde {@code 409}; el cliente puede reintentar.</li>
 *   <li>{@link Motivo#PEDIDO_DISTINTO}: la clave ya se uso con un cuerpo
 *       distinto. Es un error del cliente; se responde {@code 422}.</li>
 * </ul>
 *
 * <p>{@code rollback = true}: si el turno ya se habia insertado cuando se
 * detecta el conflicto, el contenedor lo deshace junto con los mensajes JMS de
 * la misma transaccion. Asi nunca quedan dos turnos para una misma clave.</p>
 */
@ApplicationException(rollback = true)
public class ConflictoIdempotenciaException extends RuntimeException {

    /** Por que no se pudo procesar el pedido. */
    public enum Motivo { EN_CURSO, PEDIDO_DISTINTO }

    private final Motivo motivo;

    public ConflictoIdempotenciaException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
