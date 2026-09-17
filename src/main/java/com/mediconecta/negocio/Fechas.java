package com.mediconecta.negocio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Interpretacion de las fechas que llegan como texto desde afuera.
 *
 * <p>Vive en negocio porque el formato aceptado y el mensaje de error son
 * decisiones del dominio: tienen que ser identicos venga el pedido por REST,
 * desde el asistente stateful o, manana, desde una cola de mensajes.</p>
 */
public final class Fechas {

    private Fechas() {
    }

    /** Momento exacto, en formato aaaa-MM-ddTHH:mm, truncado al minuto. */
    public static LocalDateTime momento(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ReglaNegocioException("Debe indicar la fecha y hora del turno.");
        }
        try {
            return LocalDateTime.parse(texto.trim()).truncatedTo(ChronoUnit.MINUTES);
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(
                    "La fecha y hora debe tener el formato aaaa-MM-ddTHH:mm, por ejemplo 2026-09-10T09:00.");
        }
    }

    /** Dia, en formato aaaa-MM-dd. */
    public static LocalDate dia(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ReglaNegocioException("Debe indicar la fecha, con formato aaaa-MM-dd.");
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(
                    "La fecha debe tener el formato aaaa-MM-dd, por ejemplo 2026-09-10.");
        }
    }
}
