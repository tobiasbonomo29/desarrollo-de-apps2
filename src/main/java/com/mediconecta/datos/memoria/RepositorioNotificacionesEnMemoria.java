package com.mediconecta.datos.memoria;

import com.mediconecta.datos.RepositorioNotificaciones;
import com.mediconecta.modelo.Notificacion;
import com.mediconecta.modelo.TipoNotificacion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** {@link RepositorioNotificaciones} en memoria (plan B sin base y tests). */
@Alternative
@ApplicationScoped
public class RepositorioNotificacionesEnMemoria implements RepositorioNotificaciones {

    private final List<Notificacion> notificaciones = new CopyOnWriteArrayList<>();

    private final AtomicLong generadorDeId = new AtomicLong(0);

    @Override
    public boolean existe(String eventoId, TipoNotificacion tipo) {
        return notificaciones.stream().anyMatch(n -> n.getEventoId().equals(eventoId) && n.getTipo() == tipo);
    }

    @Override
    public Notificacion guardar(Notificacion notificacion) {
        notificacion.setId(generadorDeId.incrementAndGet());
        notificaciones.add(notificacion);
        return notificacion;
    }

    @Override
    public List<Notificacion> listarPorTurno(Long turnoId) {
        return notificaciones.stream()
                .filter(n -> n.getTurnoId().equals(turnoId))
                .sorted(Comparator.comparing(Notificacion::getCreadaEn).thenComparing(Notificacion::getId))
                .toList();
    }
}
