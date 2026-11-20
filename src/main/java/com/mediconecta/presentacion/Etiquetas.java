package com.mediconecta.presentacion;

import com.mediconecta.negocio.ReglaNegocioException;

import jakarta.ws.rs.core.EntityTag;

/**
 * ETag de los recursos que se pueden modificar (turnos y pacientes).
 *
 * <p>El ETag es {@code "<recurso>-<id>-v<version>"}, donde la version es la de
 * JPA ({@code @Version}). Sirve para dos cosas:</p>
 * <ul>
 *   <li><b>GET condicional:</b> con {@code If-None-Match} igual al ETag, el
 *       servidor responde {@code 304 Not Modified} sin cuerpo.</li>
 *   <li><b>Concurrencia optimista:</b> un PUT con {@code If-Match} solo se
 *       aplica si el recurso sigue en esa version; si no, {@code 412}. Asi dos
 *       recepcionistas no se pisan los cambios.</li>
 * </ul>
 * <p>{@code If-Match} es opcional: sin la cabecera, el PUT se aplica como antes.</p>
 */
final class Etiquetas {

    private Etiquetas() {
    }

    static EntityTag de(String recurso, Object id, long version) {
        return new EntityTag(valor(recurso, id, version));
    }

    /** El valor del ETag, sin comillas. */
    static String valor(String recurso, Object id, long version) {
        return recurso + "-" + id + "-v" + version;
    }

    /**
     * La version que el cliente espera segun su {@code If-Match}; {@code null}
     * si no mando la cabecera o mando {@code *}. Un ETag de otro recurso o mal
     * formado devuelve {@code -1}, que nunca coincide (y termina en 412).
     */
    static Long versionEsperada(String siCoincide, String recurso, Object id) {
        if (siCoincide == null || siCoincide.isBlank() || siCoincide.trim().equals("*")) {
            return null;
        }
        String valor = siCoincide.trim();
        if (valor.startsWith("W/")) {
            throw new ReglaNegocioException("If-Match no admite ETags debiles (W/...).");
        }
        valor = valor.replace("\"", "");
        String prefijo = recurso + "-" + id + "-v";
        if (!valor.startsWith(prefijo)) {
            return -1L;
        }
        try {
            return Long.parseLong(valor.substring(prefijo.length()));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
