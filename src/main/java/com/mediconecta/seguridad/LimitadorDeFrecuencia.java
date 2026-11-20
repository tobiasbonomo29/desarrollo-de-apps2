package com.mediconecta.seguridad;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de frecuencia con ventana deslizante, en memoria.
 *
 * <p>Lo usan el login (fuerza bruta: despues de varios intentos fallidos se
 * responde {@code 429 Too Many Requests} con {@code Retry-After}) y el gateway
 * de IA (cada llamada al proveedor cuesta: un usuario no puede hacer mas de un
 * cierto numero por minuto).</p>
 *
 * <p>Vive en memoria de la instancia: alcanza para un solo servidor, que es el
 * despliegue del TP. Con varias instancias habria que llevarlo a un almacen
 * compartido (Redis, la base).</p>
 */
@ApplicationScoped
public class LimitadorDeFrecuencia {

    private final Map<String, Deque<Instant>> eventos = new ConcurrentHashMap<>();

    private Clock reloj = Clock.systemUTC();

    public LimitadorDeFrecuencia() {
    }

    /** Para los tests: un reloj que se puede adelantar. */
    LimitadorDeFrecuencia(Clock reloj) {
        this.reloj = reloj;
    }

    /** Anota un evento (por ejemplo, un intento de login fallido) para esa clave. */
    public void registrar(String clave) {
        Instant ahora = reloj.instant();
        eventos.compute(clave, (k, cola) -> {
            Deque<Instant> lista = cola == null ? new ArrayDeque<>() : cola;
            lista.addLast(ahora);
            return lista;
        });
    }

    /**
     * Si la clave ya tiene {@code maximo} eventos dentro de la ventana, cuanto
     * falta para que se libere uno; si no, vacio.
     */
    public Optional<Duration> bloqueo(String clave, int maximo, Duration ventana) {
        Instant ahora = reloj.instant();
        Deque<Instant> cola = eventos.get(clave);
        if (cola == null) {
            return Optional.empty();
        }
        synchronized (cola) {
            while (!cola.isEmpty() && !cola.peekFirst().isAfter(ahora.minus(ventana))) {
                cola.pollFirst();
            }
            if (cola.size() < maximo) {
                return Optional.empty();
            }
            Duration espera = Duration.between(ahora, cola.peekFirst().plus(ventana));
            return Optional.of(espera.isNegative() ? Duration.ZERO : espera);
        }
    }

    /**
     * Consume un turno: si hay lugar en la ventana lo anota y devuelve vacio; si
     * no, devuelve cuanto esperar (y no lo anota).
     */
    public Optional<Duration> consumir(String clave, int maximo, Duration ventana) {
        Optional<Duration> bloqueo = bloqueo(clave, maximo, ventana);
        if (bloqueo.isEmpty()) {
            registrar(clave);
        }
        return bloqueo;
    }

    /** Olvida los eventos de una clave (por ejemplo, tras un login correcto). */
    public void limpiar(String clave) {
        eventos.remove(clave);
    }
}
