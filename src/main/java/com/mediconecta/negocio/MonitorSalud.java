package com.mediconecta.negocio;

import com.mediconecta.datos.SondaBaseDeDatos;
import com.mediconecta.mensajeria.SondaMensajeria;

import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Health check de la aplicacion: verifica que respondan las dependencias sin
 * las cuales MediConecta no puede atender (PostgreSQL y el broker JMS).
 *
 * <p>Lo consultan Docker ({@code HEALTHCHECK}), el pipeline de CI antes de
 * correr la prueba de punta a punta y cualquier balanceador. Es
 * {@code @PermitAll} y corre sin transaccion: no lee datos de negocio.</p>
 */
@Stateless
@PermitAll
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class MonitorSalud {

    @Inject
    private SondaBaseDeDatos sondaBaseDeDatos;

    @Inject
    private SondaMensajeria sondaMensajeria;

    private Clock reloj = Clock.systemUTC();

    public EstadoSalud verificar() {
        Map<String, Boolean> resultados = new LinkedHashMap<>();
        resultados.put("baseDeDatos", sinExcepciones(sondaBaseDeDatos::responde));
        resultados.put("mensajeria", sinExcepciones(sondaMensajeria::responde));
        return new EstadoSalud(resultados, reloj.instant());
    }

    private static boolean sinExcepciones(BooleanSupplier sonda) {
        try {
            return sonda.getAsBoolean();
        } catch (RuntimeException caida) {
            return false;
        }
    }
}
