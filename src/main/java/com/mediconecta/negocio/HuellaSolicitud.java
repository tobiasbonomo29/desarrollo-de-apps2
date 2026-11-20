package com.mediconecta.negocio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Huella (SHA-256) de una {@link SolicitudTurno}: sirve para reconocer si un
 * pedido repetido con la misma {@code Idempotency-Key} es realmente el mismo
 * pedido o uno distinto.
 *
 * <p>Se calcula sobre los campos ya normalizados (sin espacios al costado) y
 * no sobre el JSON crudo, para que dos cuerpos equivalentes (otro orden de
 * campos, otros espacios) den la misma huella.</p>
 */
final class HuellaSolicitud {

    private static final String SEPARADOR = "\u001F";

    private HuellaSolicitud() {
    }

    static String de(SolicitudTurno solicitud) {
        String canonica = Stream.of(
                        solicitud.getDocumentoPaciente(),
                        solicitud.getNombrePaciente(),
                        Objects.toString(solicitud.getProfesionalId(), null),
                        solicitud.getNombreProfesional(),
                        solicitud.getEspecialidad(),
                        solicitud.getFechaHora(),
                        solicitud.getMotivo())
                .map(valor -> valor == null ? "" : valor.trim())
                .collect(Collectors.joining(SEPARADOR));
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(canonica.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("La JVM no tiene SHA-256", imposible);
        }
    }
}
