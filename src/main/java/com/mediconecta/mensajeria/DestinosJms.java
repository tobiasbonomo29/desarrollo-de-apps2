package com.mediconecta.mensajeria;

import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.JMSDestinationDefinitions;

/**
 * Declara los dos destinos JMS que usa MediConecta de forma portable, en
 * lugar de crearlos a mano con la consola o el CLI de WildFly.
 *
 * <p>Al estar en una anotacion, el contenedor crea estos destinos solo al
 * desplegar el WAR, siempre que el perfil del servidor tenga activo el
 * subsistema de mensajeria ({@code standalone-full.xml} o
 * {@code standalone-full-ha.xml}; el perfil {@code standalone.xml} que usaba
 * el proyecto hasta ahora no lo trae). Ver docs/Entrega_Obligatoria_2.md,
 * seccion de mensajeria asincronica, para el detalle de como arrancar el
 * servidor con ese perfil.</p>
 *
 * <p>{@code MensajesFallidos} es la <b>cola de mensajes muertos</b> (DLQ) de
 * los otros dos destinos: ahi el broker deja lo que no se pudo procesar despues
 * de todos los reintentos. Que destino usa cada cola como DLQ y cuantas veces se
 * reintenta no se puede declarar con anotaciones: lo fija
 * {@code scripts/wildfly/configurar-mensajeria.cli} en el servidor (Artemis
 * "address-settings").</p>
 */
@JMSDestinationDefinitions({
        @JMSDestinationDefinition(
                name = "java:/jms/queue/ColaConfirmaciones",
                interfaceName = "jakarta.jms.Queue",
                destinationName = "ColaConfirmacionesMediConecta"),
        @JMSDestinationDefinition(
                name = "java:/jms/topic/TopicoRecordatorios",
                interfaceName = "jakarta.jms.Topic",
                destinationName = "TopicoRecordatoriosMediConecta"),
        @JMSDestinationDefinition(
                name = "java:/jms/queue/MensajesFallidos",
                interfaceName = "jakarta.jms.Queue",
                destinationName = "MensajesFallidosMediConecta")
})
public final class DestinosJms {

    private DestinosJms() {
    }
}
