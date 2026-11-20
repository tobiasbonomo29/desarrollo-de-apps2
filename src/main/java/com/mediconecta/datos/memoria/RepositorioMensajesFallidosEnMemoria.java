package com.mediconecta.datos.memoria;

import com.mediconecta.datos.RepositorioMensajesFallidos;
import com.mediconecta.modelo.MensajeFallido;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** {@link RepositorioMensajesFallidos} en memoria (plan B sin base y tests). */
@Alternative
@ApplicationScoped
public class RepositorioMensajesFallidosEnMemoria implements RepositorioMensajesFallidos {

    private final List<MensajeFallido> mensajes = new CopyOnWriteArrayList<>();

    private final AtomicLong generadorDeId = new AtomicLong(0);

    @Override
    public boolean existe(String mensajeId) {
        return mensajes.stream().anyMatch(m -> m.getMensajeId().equals(mensajeId));
    }

    @Override
    public MensajeFallido guardar(MensajeFallido mensaje) {
        mensaje.setId(generadorDeId.incrementAndGet());
        mensajes.add(mensaje);
        return mensaje;
    }

    @Override
    public List<MensajeFallido> listarRecientes(int maximo) {
        return mensajes.stream()
                .sorted(Comparator.comparing(MensajeFallido::getRecibidoEn)
                        .thenComparing(MensajeFallido::getId).reversed())
                .limit(maximo)
                .toList();
    }
}
