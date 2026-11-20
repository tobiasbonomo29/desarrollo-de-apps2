package com.mediconecta.datos;

import com.mediconecta.modelo.ClaveIdempotencia;

import java.time.LocalDateTime;
import java.util.Optional;

/** Claves {@code Idempotency-Key} ya procesadas en el alta de turnos. */
public interface RepositorioIdempotencia {

    Optional<ClaveIdempotencia> buscar(String usuario, String clave);

    /**
     * Registra la clave. Devuelve {@code false} si otra transaccion registro la
     * misma {@code (usuario, clave)} primero: la restriccion unica de la base es
     * la que decide.
     */
    boolean registrar(ClaveIdempotencia clave);

    void eliminar(ClaveIdempotencia clave);

    /** Borra las claves vencidas. Devuelve cuantas se borraron. */
    int eliminarAnterioresA(LocalDateTime limite);
}
