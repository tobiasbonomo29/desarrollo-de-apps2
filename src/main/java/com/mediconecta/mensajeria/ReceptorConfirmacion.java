package com.mediconecta.mensajeria;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consumidor punto a punto de la cola de confirmaciones.
 *
 * <p>Cada mensaje lo procesa exactamente una instancia de este MDB, aunque el
 * contenedor mantenga un pool de varias: es la cola la que garantiza que un
 * mismo mensaje no se entregue dos veces. En un sistema real este seria el
 * componente que dispara el mail o el SMS de confirmacion al paciente; aca se
 * simula con un log para poder demostrarlo sin depender de un proveedor de
 * correo externo.</p>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/ColaConfirmaciones"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class ReceptorConfirmacion implements MessageListener {

    private static final Logger LOG = Logger.getLogger(ReceptorConfirmacion.class.getName());

    @Override
    public void onMessage(Message mensaje) {
        try {
            String texto = ((TextMessage) mensaje).getText();
            LOG.info(() -> "[JMS-COLA] confirmacion enviada -> " + texto);
        } catch (JMSException error) {
            LOG.log(Level.SEVERE, "No se pudo leer el mensaje de confirmacion", error);
        }
    }
}
