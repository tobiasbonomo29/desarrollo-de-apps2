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
 * Consumidor punto a punto de la cola de confirmaciones: registra el mail de
 * confirmacion para el paciente.
 *
 * <p>Cada mensaje lo procesa exactamente una instancia de este MDB, aunque el
 * contenedor mantenga un pool de varias: es la cola la que garantiza que un
 * mismo mensaje no se entregue a dos consumidores a la vez. Lo que la cola no
 * garantiza es que no llegue dos veces (un reintento, un productor que reenvia):
 * por eso el consumidor es idempotente (ver {@link GestionNotificaciones}).</p>
 *
 * <p>Si el procesamiento falla, la transaccion del MDB se deshace y el mensaje
 * vuelve a la cola para reintentarse; despues del ultimo intento va a la DLQ
 * (ver {@link ConsumoDeEventos}).</p>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/ColaConfirmaciones"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue")
})
public class ReceptorConfirmacion implements MessageListener {

    private static final Logger LOG = Logger.getLogger(ReceptorConfirmacion.class.getName());

    @Inject
    private GestionNotificaciones gestionNotificaciones;

    @Resource
    private MessageDrivenContext contexto;

    @Override
    public void onMessage(Message mensaje) {
        ConsumoDeEventos.procesar(mensaje, contexto, LOG, "[JMS-COLA] confirmacion:",
                evento -> gestionNotificaciones.registrarConfirmacion(evento.eventoId(), evento.turnoId()));
    }
}
