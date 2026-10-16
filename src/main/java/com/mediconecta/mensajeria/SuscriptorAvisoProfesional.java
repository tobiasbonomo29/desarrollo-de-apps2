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
 * El otro suscriptor del topico de recordatorios: el que sincronizaria la
 * agenda del profesional.
 *
 * <p>Recibe su propia copia del mismo mensaje que procesa
 * {@link SuscriptorAvisoPaciente}, de forma independiente. Ver esa clase para
 * la explicacion del patron publicacion/suscripcion.</p>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/TopicoRecordatorios"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class SuscriptorAvisoProfesional implements MessageListener {

    private static final Logger LOG = Logger.getLogger(SuscriptorAvisoProfesional.class.getName());

    @Override
    public void onMessage(Message mensaje) {
        try {
            String texto = ((TextMessage) mensaje).getText();
            LOG.info(() -> "[JMS-TOPICO] recordatorio para el profesional -> " + texto);
        } catch (JMSException error) {
            LOG.log(Level.SEVERE, "No se pudo leer el recordatorio del profesional", error);
        }
    }
}
