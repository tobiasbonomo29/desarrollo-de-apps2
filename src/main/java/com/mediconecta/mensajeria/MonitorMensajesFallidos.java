package com.mediconecta.mensajeria;

import com.mediconecta.negocio.GestionNotificaciones;

import jakarta.annotation.Resource;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.MessageDrivenContext;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consumidor de la cola de mensajes muertos (DLQ) {@code MensajesFallidos}.
 *
 * <p>Cuando un mensaje de la cola o del topico falla en todos sus reintentos,
 * el broker lo mueve aca. Este MDB lo registra en la tabla
 * {@code mensajes_fallidos} (visible para un ADMIN en
 * {@code GET /api/mensajeria/mensajes-fallidos}) y deja una alerta en el log.
 * Asi un fallo nunca se pierde en silencio y se puede analizar o reprocesar.</p>
 *
 * <p>El broker agrega la propiedad {@code _AMQ_ORIG_ADDRESS} con el destino del
 * que vino el mensaje.</p>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/MensajesFallidos"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue")
})
public class MonitorMensajesFallidos implements MessageListener {

    private static final Logger LOG = Logger.getLogger(MonitorMensajesFallidos.class.getName());

    static final String PROPIEDAD_ORIGEN = "_AMQ_ORIG_ADDRESS";

    @Inject
    private GestionNotificaciones gestionNotificaciones;

    @Resource
    private MessageDrivenContext contexto;

    @Override
    public void onMessage(Message mensaje) {
        try {
            String id = mensaje.getJMSMessageID() != null ? mensaje.getJMSMessageID() : "sin-id-" + UUID.randomUUID();
            String evento = mensaje.getStringProperty(NotificadorTurnos.PROPIEDAD_EVENTO);
            String origen = mensaje.getStringProperty(PROPIEDAD_ORIGEN);
            String cuerpo = mensaje instanceof TextMessage texto
                    ? texto.getText()
                    : "(mensaje de tipo " + mensaje.getClass().getSimpleName() + ")";

            boolean nuevo = gestionNotificaciones.registrarMensajeFallido(id, evento, origen, cuerpo);
            if (nuevo) {
                LOG.severe("[JMS-DLQ] mensaje " + id + " (evento " + evento + ", origen " + origen
                        + ") agoto los reintentos y quedo registrado en mensajes_fallidos.");
            }
        } catch (JMSException | RuntimeException error) {
            LOG.log(Level.SEVERE, "[JMS-DLQ] no se pudo registrar un mensaje fallido; se reintenta.", error);
            contexto.setRollbackOnly();
        }
    }
}
