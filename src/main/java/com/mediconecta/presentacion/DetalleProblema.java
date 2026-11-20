package com.mediconecta.presentacion;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cuerpo de error de la API en formato <b>Problem Details for HTTP APIs</b>
 * (RFC 9457, {@code application/problem+json}). Reemplaza al
 * {@code {"error": ..., "codigo": ...}} propio de las primeras entregas.
 *
 * <pre>
 * {
 *   "type": "urn:mediconecta:problema:regla-negocio",
 *   "title": "Regla de negocio incumplida",
 *   "status": 400,
 *   "detail": "El profesional ya tiene un turno en ese horario.",
 *   "instance": "/mediconecta/api/turnos"
 * }
 * </pre>
 *
 * <p>Se serializa con JSON-P y no con JSON-B a proposito: asi el formato no
 * depende de que el proveedor JSON-B del servidor reconozca el tipo de medio
 * {@code application/problem+json}.</p>
 */
public final class DetalleProblema {

    /** Tipo de medio de RFC 9457. */
    public static final String TIPO_MEDIO = "application/problem+json";

    /** Tipo generico de RFC 9457, para errores HTTP sin semantica propia. */
    public static final String SIN_TIPO = "about:blank";

    private final String tipo;

    private final String titulo;

    private final int estado;

    private final String detalle;

    private final String instancia;

    private final Map<String, String> extensiones;

    public DetalleProblema(String tipo, String titulo, int estado, String detalle, String instancia) {
        this(tipo, titulo, estado, detalle, instancia, Map.of());
    }

    public DetalleProblema(String tipo, String titulo, int estado, String detalle, String instancia,
                           Map<String, String> extensiones) {
        this.tipo = tipo;
        this.titulo = titulo;
        this.estado = estado;
        this.detalle = detalle;
        this.instancia = instancia;
        this.extensiones = Collections.unmodifiableMap(new LinkedHashMap<>(extensiones));
    }

    public static DetalleProblema de(TipoProblema tipo, String detalle, String instancia) {
        return new DetalleProblema(tipo.uri(), tipo.titulo(), tipo.estado(), detalle, instancia);
    }

    public String tipo() {
        return tipo;
    }

    public String titulo() {
        return titulo;
    }

    public int estado() {
        return estado;
    }

    public String detalle() {
        return detalle;
    }

    public String instancia() {
        return instancia;
    }

    public Map<String, String> extensiones() {
        return extensiones;
    }

    /** JSON con los miembros estandar en el orden de la RFC, mas las extensiones. */
    public String aJson() {
        JsonObjectBuilder json = Json.createObjectBuilder()
                .add("type", tipo)
                .add("title", titulo)
                .add("status", estado);
        if (detalle != null) {
            json.add("detail", detalle);
        }
        if (instancia != null) {
            json.add("instance", instancia);
        }
        extensiones.forEach(json::add);
        return json.build().toString();
    }
}
