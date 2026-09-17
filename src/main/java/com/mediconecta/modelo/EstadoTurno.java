package com.mediconecta.modelo;

/**
 * Estados por los que puede pasar un turno.
 *
 * <p>El estado lo fija siempre la capa de negocio: el cliente HTTP no puede
 * elegirlo. Un turno nace {@link #PROGRAMADO} y solo el negocio lo mueve a
 * {@link #ATENDIDO} o {@link #CANCELADO}.</p>
 */
public enum EstadoTurno {

    PROGRAMADO,

    ATENDIDO,

    CANCELADO
}
