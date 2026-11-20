package com.mediconecta.negocio;

import com.mediconecta.modelo.Turno;

/**
 * Lo que devuelve una reserva con {@code Idempotency-Key}: el turno y si se
 * creo ahora o es el que ya se habia creado con esa misma clave.
 */
public record ResultadoReserva(Turno turno, boolean repetida) {
}
