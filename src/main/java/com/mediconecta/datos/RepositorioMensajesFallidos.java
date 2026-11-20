package com.mediconecta.datos;

import com.mediconecta.modelo.MensajeFallido;

import java.util.List;

/** Mensajes JMS que terminaron en la cola de mensajes muertos (DLQ). */
public interface RepositorioMensajesFallidos {

    boolean existe(String mensajeId);

    MensajeFallido guardar(MensajeFallido mensaje);

    /** Los ultimos {@code maximo} mensajes, del mas nuevo al mas viejo. */
    List<MensajeFallido> listarRecientes(int maximo);
}
