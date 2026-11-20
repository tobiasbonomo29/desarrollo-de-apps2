package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioIdempotencia;

import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Borra las {@code Idempotency-Key} vencidas una vez por hora (EJB Timer
 * Service, {@code @Schedule}).
 *
 * <p>Una clave solo protege de reintentos durante
 * {@link FachadaReservas#VIGENCIA_CLAVE}; pasado ese tiempo ocupa lugar sin
 * aportar nada. El timer no es persistente: si el servidor estaba apagado a la
 * hora programada, la limpieza corre en la proxima.</p>
 */
@Stateless
public class LimpiezaIdempotencia {

    private static final Logger LOG = Logger.getLogger(LimpiezaIdempotencia.class.getName());

    @Inject
    private RepositorioIdempotencia repositorio;

    @Schedule(hour = "*", minute = "17", persistent = false)
    public void limpiar() {
        int borradas = repositorio.eliminarAnterioresA(LocalDateTime.now().minus(FachadaReservas.VIGENCIA_CLAVE));
        if (borradas > 0) {
            LOG.info("[IDEMPOTENCIA] Se borraron " + borradas + " claves vencidas.");
        }
    }
}
