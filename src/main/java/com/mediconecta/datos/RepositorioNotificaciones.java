package com.mediconecta.datos;

import com.mediconecta.modelo.Notificacion;
import com.mediconecta.modelo.TipoNotificacion;

import java.util.List;

/** Avisos generados por los consumidores JMS. */
public interface RepositorioNotificaciones {

    /** {@code true} si ese evento ya genero un aviso de ese tipo (consumidor idempotente). */
    boolean existe(String eventoId, TipoNotificacion tipo);

    Notificacion guardar(Notificacion notificacion);

    List<Notificacion> listarPorTurno(Long turnoId);
}
