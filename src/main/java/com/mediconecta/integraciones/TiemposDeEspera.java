package com.mediconecta.integraciones;

import java.time.Duration;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Tiempos de espera de las llamadas a sistemas externos (obras sociales).
 *
 * <p>Sin timeouts, un sistema externo colgado deja colgado tambien al hilo de
 * MediConecta que lo llamo, y con el a la transaccion y a la conexion de base
 * de datos que tiene tomadas: con pocos pedidos simultaneos el servidor se
 * queda sin hilos. Con timeouts la falla queda acotada y el cliente recibe un
 * {@code 504 Gateway Timeout} claro.</p>
 *
 * <ul>
 *   <li>conexion: {@code mediconecta.integraciones.timeout.conexion.ms}
 *       ({@value #CONEXION_POR_DEFECTO_MS} ms por omision);</li>
 *   <li>respuesta: {@code mediconecta.integraciones.timeout.respuesta.ms}
 *       ({@value #RESPUESTA_POR_DEFECTO_MS} ms por omision).</li>
 * </ul>
 *
 * <p>El asistente de IA tiene sus propios tiempos, mas largos, porque un modelo
 * de lenguaje tarda mas en contestar (ver {@link AdaptadorIaGroq}).</p>
 */
final class TiemposDeEspera {

    private static final Logger LOG = Logger.getLogger(TiemposDeEspera.class.getName());

    static final long CONEXION_POR_DEFECTO_MS = 2000;

    static final long RESPUESTA_POR_DEFECTO_MS = 5000;

    static final String PROPIEDAD_CONEXION = "mediconecta.integraciones.timeout.conexion.ms";

    static final String PROPIEDAD_RESPUESTA = "mediconecta.integraciones.timeout.respuesta.ms";

    private TiemposDeEspera() {
    }

    static Duration conexion() {
        return leer(PROPIEDAD_CONEXION, CONEXION_POR_DEFECTO_MS);
    }

    static Duration respuesta() {
        return leer(PROPIEDAD_RESPUESTA, RESPUESTA_POR_DEFECTO_MS);
    }

    /** Lee una propiedad en milisegundos; si falta o es invalida usa el valor por omision. */
    static Duration leer(String propiedad, long porDefectoMs) {
        String valor = System.getProperty(propiedad);
        if (valor == null || valor.isBlank()) {
            return Duration.ofMillis(porDefectoMs);
        }
        try {
            long ms = Long.parseLong(valor.trim());
            if (ms > 0) {
                return Duration.ofMillis(ms);
            }
        } catch (NumberFormatException e) {
            // se informa abajo y se usa el valor por omision
        }
        LOG.warning("[INTEGRACION] " + propiedad + "=" + valor + " no es un numero positivo de milisegundos; se usa "
                + porDefectoMs + ".");
        return Duration.ofMillis(porDefectoMs);
    }

    /**
     * Carga los timeouts en el contexto de un cliente JAX-WS. Cada implementacion
     * lee sus propias claves: Apache CXF 4 (la de WildFly) usa las
     * {@code jakarta.*}; CXF 3 las {@code javax.*}; Metro las {@code com.sun.*}.
     * Se cargan todas para que el adaptador no dependa del servidor.
     */
    static void aplicarSoap(Map<String, Object> contexto, Duration conexion, Duration respuesta) {
        Integer msConexion = Math.toIntExact(conexion.toMillis());
        Integer msRespuesta = Math.toIntExact(respuesta.toMillis());
        contexto.put("jakarta.xml.ws.client.connectionTimeout", msConexion);
        contexto.put("jakarta.xml.ws.client.receiveTimeout", msRespuesta);
        contexto.put("javax.xml.ws.client.connectionTimeout", msConexion);
        contexto.put("javax.xml.ws.client.receiveTimeout", msRespuesta);
        contexto.put("com.sun.xml.ws.connect.timeout", msConexion);
        contexto.put("com.sun.xml.ws.request.timeout", msRespuesta);
    }
}
