package com.mediconecta.presentacion;

import com.mediconecta.negocio.AsistenteReservaTurno;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Puente entre la sesion HTTP y el EJB {@code @Stateful}.
 *
 * <p>Este es el punto que hace que la conversacion del asistente sea realmente
 * de <b>ese</b> usuario. Un EJB stateful no se ata solo a una sesion web: hay
 * que guardar su referencia en algo que viva lo mismo que la sesion HTTP. Eso es
 * este bean CDI {@code @SessionScoped}, que CDI asocia a la cookie
 * {@code JSESSIONID}.</p>
 *
 * <p>Se inyecta {@code Instance<AsistenteReservaTurno>} y no el EJB directamente
 * porque hace falta poder <b>destruir</b> la instancia cuando la conversacion
 * termina y pedir una nueva para la siguiente reserva. Al destruirla, CDI invoca
 * el metodo {@code @Remove} del EJB y el contenedor ejecuta su
 * {@code @PreDestroy}: el ciclo de vida sigue siendo del contenedor.</p>
 *
 * <p>Vive en la capa de presentacion a proposito: el alcance de sesion es un
 * concepto web. El negocio no sabe que existe una sesion HTTP.</p>
 */
@SessionScoped
public class ConversacionDeReserva implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(ConversacionDeReserva.class.getName());

    @Inject
    private Instance<AsistenteReservaTurno> proveedor;

    private AsistenteReservaTurno asistente;

    /** Devuelve el asistente de esta sesion, creandolo la primera vez. */
    public AsistenteReservaTurno asistente() {
        if (asistente == null) {
            asistente = proveedor.get();
            LOG.info("[STATEFUL] la sesion HTTP tomo un asistente de reserva nuevo");
        }
        return asistente;
    }

    /**
     * Cierra la conversacion actual.
     *
     * <p>Primero se invoca {@code finalizar()}, que es el metodo anotado con
     * {@code @Remove}: esa llamada es la que le dice al contenedor que la
     * conversacion termino, y a continuacion el contenedor ejecuta el
     * {@code @PreDestroy} del bean y descarta la instancia. Despues se libera el
     * contexto de CDI; como el EJB ya no existe, esa segunda llamada puede
     * fallar y por eso va dentro de su propio try.</p>
     */
    public void cerrar() {
        if (asistente == null) {
            return;
        }
        try {
            asistente.finalizar();
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "El asistente ya no estaba disponible al finalizarlo", e);
        }
        try {
            proveedor.destroy(asistente);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "El contenedor ya habia descartado el asistente", e);
        }
        asistente = null;
    }

    @PreDestroy
    void alTerminarLaSesion() {
        LOG.info("[STATEFUL] la sesion HTTP termina: se cierra la conversacion pendiente");
        cerrar();
    }
}
