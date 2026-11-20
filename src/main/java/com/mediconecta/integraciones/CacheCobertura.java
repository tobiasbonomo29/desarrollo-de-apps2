package com.mediconecta.integraciones;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ultimo resultado bueno de cada consulta de cobertura, para usarlo de
 * respaldo (fallback) si el sistema externo no responde a tiempo.
 *
 * <p>Un resultado sirve como respaldo durante {@link #VIGENCIA}: pasado ese
 * tiempo se considera demasiado viejo para confiar en el.</p>
 */
@ApplicationScoped
public class CacheCobertura {

    static final Duration VIGENCIA = Duration.ofHours(24);

    private record Registro(ResultadoCobertura resultado, Instant momento) {
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    private Clock reloj = Clock.systemUTC();

    public void guardar(String clave, ResultadoCobertura resultado) {
        registros.put(clave, new Registro(copia(resultado), reloj.instant()));
    }

    public Optional<ResultadoCobertura> buscar(String clave) {
        Registro registro = registros.get(clave);
        if (registro == null || registro.momento().isBefore(reloj.instant().minus(VIGENCIA))) {
            return Optional.empty();
        }
        return Optional.of(copia(registro.resultado()));
    }

    private static ResultadoCobertura copia(ResultadoCobertura original) {
        ResultadoCobertura copia = new ResultadoCobertura(original.isVigente(), original.getPlan(),
                original.getCopago(), original.getOrigen());
        copia.setContingencia(original.isContingencia());
        copia.setMensaje(original.getMensaje());
        return copia;
    }
}
