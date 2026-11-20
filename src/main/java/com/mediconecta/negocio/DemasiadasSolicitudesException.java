package com.mediconecta.negocio;

import jakarta.ejb.ApplicationException;

import java.time.Duration;

/**
 * Se supero el limite de intentos o de solicitudes: fuerza bruta sobre el
 * login, o demasiadas consultas al asistente de IA. La presentacion responde
 * {@code 429 Too Many Requests} con la cabecera {@code Retry-After}.
 */
@ApplicationException(rollback = false)
public class DemasiadasSolicitudesException extends RuntimeException {

    private final long segundosDeEspera;

    public DemasiadasSolicitudesException(String mensaje, Duration espera) {
        super(mensaje);
        this.segundosDeEspera = Math.max(1, (espera.toMillis() + 999) / 1000);
    }

    public long segundosDeEspera() {
        return segundosDeEspera;
    }
}
