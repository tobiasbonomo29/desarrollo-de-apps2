package com.mediconecta.mensajeria;

import com.mediconecta.negocio.GestionNotificaciones;

import jakarta.annotation.Resource;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.MessageDrivenContext;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import java.util.logging.Logger;

/**
 * Un suscriptor del topico de recordatorios: programa el recordatorio al
 * paciente para 24 horas antes del turno.
 *
 * <p>Comparte el topico {@code TopicoRecordatorios} con
 * {@link SuscriptorAvisoProfesional}: ambos reciben una copia independiente
 * del mismo mensaje, que es exactamente lo que distingue a un topico
 * (publicacion/suscripcion) de una cola (punto a punto).</p>
 *
 * <p>La suscripcion es <b>durable</b>: si este consumidor esta caido cuando se
 * publica un evento, el broker se lo guarda y lo entrega cuando vuelve. Con una
 * suscripcion comun ese aviso se perderia.</p>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/TopicoRecordatorios"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "mediconecta-aviso-paciente"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "aviso-paciente"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class SuscriptorAvisoPaciente implements MessageListener {

    private static final Logger LOG = Logger.getLogger(SuscriptorAvisoPaciente.class.getName());

    @Inject
    private GestionNotificaciones gestionNotificaciones;

    @Resource
    private MessageDrivenContext contexto;

    @Override
    public void onMessage(Message mensaje) {
        ConsumoDeEventos.procesar(mensaje, contexto, LOG, "[JMS-TOPICO] recordatorio del paciente:",
                evento -> gestionNotificaciones.registrarRecordatorio(evento.eventoId(), evento.turnoId()));
    }
}
