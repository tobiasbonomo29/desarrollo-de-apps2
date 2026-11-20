package com.mediconecta.negocio;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resultado del health check: {@code UP} si todas las dependencias responden,
 * {@code DOWN} si alguna no. Solo informa el estado de cada componente, nunca
 * el detalle del error (el endpoint es publico).
 */
public class EstadoSalud {

    public static final String UP = "UP";

    public static final String DOWN = "DOWN";

    private final String estado;

    private final Map<String, String> componentes;

    private final String verificadoEn;

    public EstadoSalud(Map<String, Boolean> resultados, Instant momento) {
        Map<String, String> estados = new LinkedHashMap<>();
        resultados.forEach((nombre, responde) -> estados.put(nombre, responde ? UP : DOWN));
        this.componentes = Collections.unmodifiableMap(estados);
        this.estado = resultados.values().stream().allMatch(Boolean::booleanValue) ? UP : DOWN;
        this.verificadoEn = momento.toString();
    }

    public String getEstado() {
        return estado;
    }

    public Map<String, String> getComponentes() {
        return componentes;
    }

    public String getVerificadoEn() {
        return verificadoEn;
    }

    public boolean estaArriba() {
        return UP.equals(estado);
    }
}
