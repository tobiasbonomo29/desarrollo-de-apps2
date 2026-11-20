package com.mediconecta.datos.memoria;

import com.mediconecta.datos.RepositorioIdempotencia;
import com.mediconecta.modelo.ClaveIdempotencia;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link RepositorioIdempotencia} en memoria (plan B sin base y tests). Como
 * las demas de este paquete, esta desactivada con {@code @Alternative}.
 */
@Alternative
@ApplicationScoped
public class RepositorioIdempotenciaEnMemoria implements RepositorioIdempotencia {

    private final Map<String, ClaveIdempotencia> claves = new ConcurrentHashMap<>();

    private final AtomicLong generadorDeId = new AtomicLong(0);

    private static String llave(String usuario, String clave) {
        return usuario + "\u0000" + clave;
    }

    @Override
    public Optional<ClaveIdempotencia> buscar(String usuario, String clave) {
        return Optional.ofNullable(claves.get(llave(usuario, clave)));
    }

    @Override
    public boolean registrar(ClaveIdempotencia clave) {
        ClaveIdempotencia previa = claves.putIfAbsent(llave(clave.getUsuario(), clave.getClave()), clave);
        if (previa != null) {
            return false;
        }
        clave.setId(generadorDeId.incrementAndGet());
        return true;
    }

    @Override
    public void eliminar(ClaveIdempotencia clave) {
        claves.remove(llave(clave.getUsuario(), clave.getClave()));
    }

    @Override
    public int eliminarAnterioresA(LocalDateTime limite) {
        int antes = claves.size();
        claves.values().removeIf(c -> c.getCreadaEn().isBefore(limite));
        return antes - claves.size();
    }
}
