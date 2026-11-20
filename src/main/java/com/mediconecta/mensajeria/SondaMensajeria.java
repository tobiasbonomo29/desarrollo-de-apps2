package com.mediconecta.mensajeria;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;

/**
 * Comprobacion de salud del broker JMS, para {@code GET /api/salud}: abre una
 * conexion y recorre (sin consumir) la cola de confirmaciones.
 *
 * <p>Usa la connection factory comun ({@code java:/ConnectionFactory}) y no la
 * XA, y corre fuera de transaccion: un health check no tiene que enlistar nada.</p>
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class SondaMensajeria {

    @Resource(lookup = "java:/ConnectionFactory")
    private ConnectionFactory fabrica;

    @Resource(lookup = "java:/jms/queue/ColaConfirmaciones")
    private Queue colaConfirmaciones;

    public boolean responde() {
        try (JMSContext prueba = fabrica.createContext();
             QueueBrowser navegador = prueba.createBrowser(colaConfirmaciones)) {
            navegador.getEnumeration();
            return true;
        } catch (Exception caida) {
            // JMSRuntimeException, JMSException al cerrar el navegador, etc.
            return false;
        }
    }
}
