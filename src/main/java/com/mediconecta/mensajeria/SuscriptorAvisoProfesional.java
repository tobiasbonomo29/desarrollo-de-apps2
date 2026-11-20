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
 * El otro suscriptor del topico de recordatorios: registra el aviso en la
 * agenda del profesional.
 *
 * <p>Recibe su propia copia del mismo mensaje que procesa
 * {@link SuscriptorAvisoPaciente}, de forma independiente y con su propia
 * suscripcion durable. Ver esa clase para la explicacion del patron
 * publicacion/suscripcion.</p>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/TopicoRecordatorios"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "mediconecta-aviso-profesional"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "aviso-profesional"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class SuscriptorAvisoProfesional implements MessageListener {

    private static final Logger LOG = Logger.getLogger(SuscriptorAvisoProfesional.class.getName());

    @Inject
    private GestionNotificaciones gestionNotificaciones;

    @Resource
    private MessageDrivenContext contexto;

    @Override
    public void onMessage(Message mensaje) {
        ConsumoDeEventos.procesar(mensaje, contexto, LOG, "[JMS-TOPICO] aviso al profesional:",
                evento -> gestionNotificaciones.registrarAvisoProfesional(evento.eventoId(), evento.turnoId()));
    }
}
