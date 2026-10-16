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
 * Un suscriptor del topico de recordatorios: el que avisaria al paciente.
 *
 * <p>Comparte el topico {@code TopicoRecordatorios} con
 * {@link SuscriptorAvisoProfesional}: ambos reciben una copia independiente
 * del mismo mensaje, que es exactamente lo que distingue a un topico
 * (publicacion/suscripcion) de una cola (punto a punto).</p>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/TopicoRecordatorios"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class SuscriptorAvisoPaciente implements MessageListener {

    private static final Logger LOG = Logger.getLogger(SuscriptorAvisoPaciente.class.getName());

    @Override
    public void onMessage(Message mensaje) {
        try {
            String texto = ((TextMessage) mensaje).getText();
            LOG.info(() -> "[JMS-TOPICO] recordatorio para el paciente -> " + texto);
        } catch (JMSException error) {
            LOG.log(Level.SEVERE, "No se pudo leer el recordatorio del paciente", error);
        }
    }
}
