package com.mediconecta.datos;

/** Comprobacion de salud de la base de datos, para {@code GET /api/salud}. */
public interface SondaBaseDeDatos {

    /** {@code true} si la base responde a una consulta trivial. */
    boolean responde();
}
