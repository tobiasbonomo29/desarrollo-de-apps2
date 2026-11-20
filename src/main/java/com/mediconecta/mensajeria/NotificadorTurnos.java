package com.mediconecta.mensajeria;

import com.mediconecta.modelo.Turno;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.jms.Topic;

import java.time.Clock;
import java.util.UUID;

/**
 * Productor JMS: avisa que un turno quedo confirmado.
 *
 * <p>Envia el mismo evento ({@link EventoTurno}, JSON versionado) por dos
 * patrones de mensajeria distintos:</p>
 * <ul>
 *   <li><b>Cola (punto a punto).</b> {@code ColaConfirmaciones}: la procesa
 *   exactamente un consumidor ({@link ReceptorConfirmacion}), el que registra
 *   el mail de confirmacion.</li>
 *   <li><b>Topico (publicacion/suscripcion).</b> {@code TopicoRecordatorios}:
 *   la reciben, cada uno por su lado, todos los suscriptores interesados
 *   ({@link SuscriptorAvisoPaciente} y {@link SuscriptorAvisoProfesional}).</li>
 * </ul>
 *
 * <p>Se invoca desde {@link com.mediconecta.negocio.FachadaReservas#reservar}
 * dentro de la misma transaccion JTA que crea el turno. El {@link JMSContext}
 * se obtiene de la connection factory XA ({@code java:/JmsXA}), asi que queda
 * enlistado en esa transaccion: si el commit de la base de datos no llega a
 * ocurrir, ningun mensaje se entrega. Es la demostracion concreta de la
 * transaccion declarativa sobre un flujo critico de varios pasos.</p>
 *
 * <p>Los mensajes son persistentes ({@link DeliveryMode#PERSISTENT}): si el
 * servidor se reinicia antes de que un consumidor los procese, no se pierden.</p>
 */
@Stateless
public class NotificadorTurnos {

    /** Propiedades JMS que acompanan al cuerpo, para filtrar con selectores. */
    static final String PROPIEDAD_EVENTO = "eventoId";

    static final String PROPIEDAD_TIPO = "tipoEvento";

    static final String PROPIEDAD_VERSION = "versionEvento";

    static final String PROPIEDAD_TURNO = "turnoId";

    @Inject
    @JMSConnectionFactory("java:/JmsXA")
    private JMSContext contexto;

    @Resource(lookup = "java:/jms/queue/ColaConfirmaciones")
    private Queue colaConfirmaciones;

    @Resource(lookup = "java:/jms/topic/TopicoRecordatorios")
    private Topic topicoRecordatorios;

    public void turnoConfirmado(Turno turno) {
        EventoTurno evento = EventoTurno.turnoConfirmado(turno, Clock.systemUTC());
        String cuerpo = evento.aJson();
        enviar(colaConfirmaciones, evento, cuerpo);
        enviar(topicoRecordatorios, evento, cuerpo);
    }

    /**
     * Manda a la cola de confirmaciones un mensaje que ningun consumidor puede
     * entender. Sirve para demostrar, en vivo, los reintentos y la DLQ: el
     * consumidor lo rechaza, el broker lo reintenta y al final lo mueve a
     * {@code MensajesFallidos}. Solo lo puede pedir un ADMIN (el control esta en
     * {@code GestionNotificaciones}).
     */
    public String enviarMensajeDefectuoso() {
        String id = "prueba-" + UUID.randomUUID();
        contexto.createProducer()
                .setDeliveryMode(DeliveryMode.PERSISTENT)
                .setProperty(PROPIEDAD_EVENTO, id)
                .send(colaConfirmaciones, "{\"esto\": \"no es un evento de MediConecta\"}");
        return id;
    }

    private void enviar(Destination destino, EventoTurno evento, String cuerpo) {
        contexto.createProducer()
                .setDeliveryMode(DeliveryMode.PERSISTENT)
                .setJMSType(evento.tipo())
                .setProperty(PROPIEDAD_EVENTO, evento.eventoId())
                .setProperty(PROPIEDAD_TIPO, evento.tipo())
                .setProperty(PROPIEDAD_VERSION, evento.version())
                .setProperty(PROPIEDAD_TURNO, evento.turnoId())
                .send(destino, cuerpo);
    }
}
