package com.mediconecta.mensajeria;

import jakarta.ejb.MessageDrivenContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Esqueleto comun de los tres consumidores del evento "turno confirmado".
 *
 * <ol>
 *   <li>Convierte el mensaje en un {@link EventoTurno} (valida el contrato).</li>
 *   <li>Ejecuta la accion del consumidor. La accion es <b>idempotente</b>:
 *       devuelve {@code false} si ese evento ya se habia procesado, y en ese
 *       caso no se repite nada.</li>
 *   <li>Si algo falla, <b>no se traga el error</b> (como hacian los MDB de la
 *       Entrega 2): marca la transaccion del MDB para deshacerse. El mensaje
 *       vuelve a la cola y el broker lo reentrega con espera creciente; despues
 *       del ultimo intento lo mueve a la DLQ ({@link MonitorMensajesFallidos}).
 *       Los reintentos y la DLQ se configuran en el servidor:
 *       {@code scripts/wildfly/configurar-mensajeria.cli}.</li>
 * </ol>
 *
 * <p>Como la escritura en la base y el acuse del mensaje estan en la misma
 * transaccion del MDB, o pasan las dos cosas o no pasa ninguna.</p>
 */
final class ConsumoDeEventos {

    /** Lo que hace cada consumidor con un evento valido. */
    @FunctionalInterface
    interface Accion {
        /** @return {@code true} si se proceso; {@code false} si era un duplicado */
        boolean ejecutar(EventoTurno evento);
    }

    private ConsumoDeEventos() {
    }

    static void procesar(Message mensaje, MessageDrivenContext contexto, Logger log, String etiqueta,
                         Accion accion) {
        int intento = intentoDeEntrega(mensaje);
        try {
            EventoTurno evento = EventoTurno.desdeMensaje(mensaje);
            if (accion.ejecutar(evento)) {
                log.info(() -> etiqueta + " turno " + evento.turnoId() + " procesado (evento " + evento.eventoId()
                        + ", intento " + intento + ")");
            } else {
                log.info(() -> etiqueta + " evento " + evento.eventoId() + " repetido: ya estaba procesado, se ignora");
            }
        } catch (MensajeInvalidoException invalido) {
            log.warning(etiqueta + " mensaje invalido (intento " + intento + "): " + invalido.getMessage()
                    + " Se devuelve para que termine en la DLQ.");
            contexto.setRollbackOnly();
        } catch (RuntimeException error) {
            log.log(Level.WARNING, etiqueta + " no se pudo procesar el mensaje (intento " + intento
                    + "): se devuelve a la cola para reintentar.", error);
            contexto.setRollbackOnly();
        }
    }

    /** Numero de entrega del mensaje segun el broker (1 la primera vez). */
    static int intentoDeEntrega(Message mensaje) {
        try {
            return mensaje == null ? 0 : Math.max(1, mensaje.getIntProperty("JMSXDeliveryCount"));
        } catch (JMSException | NumberFormatException sinDato) {
            return 1;
        }
    }
}
