package com.mediconecta.mensajeria;

/**
 * Un mensaje JMS que no respeta el contrato de {@link EventoTurno}: no es texto,
 * no es JSON, le falta un campo o trae una version que este consumidor no
 * entiende. Es un "mensaje envenenado": reintentarlo no lo arregla, pero se
 * reintenta igual hasta el maximo configurado para que termine en la DLQ y
 * quede registrado, en lugar de perderse.
 */
public class MensajeInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MensajeInvalidoException(String mensaje) {
        super(mensaje);
    }

    public MensajeInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
