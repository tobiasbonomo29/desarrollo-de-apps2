package com.mediconecta.mensajeria;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;
import com.mediconecta.negocio.GestionNotificaciones;

import jakarta.ejb.MessageDrivenContext;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los consumidores JMS: procesan, ignoran duplicados y, si algo falla,
 * devuelven el mensaje (rollback) en lugar de tragarse el error.
 */
class ConsumoDeEventosTest {

    private static final Logger LOG = Logger.getLogger(ConsumoDeEventosTest.class.getName());

    private final AtomicBoolean rollback = new AtomicBoolean();

    private MessageDrivenContext contexto;

    private String json;

    /** Registra que se llamo; no toca ninguna base. */
    private final List<String> llamadas = new ArrayList<>();

    private final class GestionDePrueba extends GestionNotificaciones {
        @Override
        public boolean registrarConfirmacion(String eventoId, Long turnoId) {
            llamadas.add("confirmacion:" + turnoId);
            return true;
        }

        @Override
        public boolean registrarRecordatorio(String eventoId, Long turnoId) {
            llamadas.add("recordatorio:" + turnoId);
            return true;
        }

        @Override
        public boolean registrarAvisoProfesional(String eventoId, Long turnoId) {
            llamadas.add("profesional:" + turnoId);
            return false;
        }

        @Override
        public boolean registrarMensajeFallido(String mensajeId, String eventoId, String destino, String cuerpo) {
            llamadas.add("fallido:" + mensajeId + ":" + eventoId + ":" + destino + ":" + cuerpo);
            return true;
        }
    }

    @BeforeEach
    void preparar() {
        contexto = (MessageDrivenContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MessageDrivenContext.class}, (proxy, metodo, args) -> {
                    if ("setRollbackOnly".equals(metodo.getName())) {
                        rollback.set(true);
                        return null;
                    }
                    throw new UnsupportedOperationException(metodo.getName());
                });

        Paciente ana = new Paciente("Ana Gomez", "30111222");
        ana.setId(7L);
        Profesional laura = new Profesional("Dra. Laura Fernandez", "Cardiologia", "MP-10234");
        laura.setId(3L);
        Turno turno = new Turno();
        turno.setId(42L);
        turno.setPaciente(ana);
        turno.setProfesional(laura);
        turno.setEspecialidad("Cardiologia");
        turno.setFechaHora(LocalDateTime.of(2026, 11, 24, 9, 30));
        json = EventoTurno.turnoConfirmado(turno, Clock.systemUTC()).aJson();
    }

    /** Un TextMessage minimo, con cuerpo y propiedades. */
    private static TextMessage mensaje(String cuerpo, Map<String, Object> propiedades) {
        return (TextMessage) Proxy.newProxyInstance(ConsumoDeEventosTest.class.getClassLoader(),
                new Class<?>[]{TextMessage.class}, (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "getText" -> cuerpo;
                    case "getJMSMessageID" -> propiedades.get("id");
                    case "getStringProperty" -> (String) propiedades.get((String) args[0]);
                    case "getIntProperty" -> {
                        Object valor = propiedades.get((String) args[0]);
                        if (valor == null) {
                            throw new NumberFormatException("sin " + args[0]);
                        }
                        yield (Integer) valor;
                    }
                    case "toString" -> "TextMessage de prueba";
                    default -> throw new UnsupportedOperationException(metodo.getName());
                });
    }

    private static void inyectar(Object destino, String campo, Object valor) throws ReflectiveOperationException {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }

    private <T> T mdb(T mdb) throws ReflectiveOperationException {
        inyectar(mdb, "contexto", contexto);
        try {
            inyectar(mdb, "gestionNotificaciones", new GestionDePrueba());
        } catch (NoSuchFieldException sinCampo) {
            // no todos los MDB lo usan
        }
        return mdb;
    }

    @Test
    void unEventoValidoSeProcesaSinRollback() {
        ConsumoDeEventos.procesar(mensaje(json, Map.of("JMSXDeliveryCount", 1)), contexto, LOG, "[prueba]",
                evento -> llamadas.add("turno " + evento.turnoId()));

        assertEquals(List.of("turno 42"), llamadas);
        assertFalse(rollback.get());
    }

    @Test
    void unDuplicadoNoEsUnError() {
        ConsumoDeEventos.procesar(mensaje(json, Map.of()), contexto, LOG, "[prueba]", evento -> false);

        assertFalse(rollback.get());
    }

    @Test
    void unMensajeInvalidoSeDevuelveParaQueTermineEnLaDlq() {
        ConsumoDeEventos.procesar(mensaje("CONFIRMACION|Turno 5", Map.of()), contexto, LOG, "[prueba]", evento -> {
            throw new AssertionError("no deberia llegar a la accion");
        });

        assertTrue(rollback.get(), "el error ya no se traga: el mensaje vuelve para reintentarse");
    }

    @Test
    void unaFallaDeLaAccionTambienHaceRollback() {
        ConsumoDeEventos.procesar(mensaje(json, Map.of("JMSXDeliveryCount", 3)), contexto, LOG, "[prueba]", evento -> {
            throw new IllegalStateException("la base no responde");
        });

        assertTrue(rollback.get());
    }

    @Test
    void elNumeroDeEntregaSaleDelBroker() {
        assertEquals(3, ConsumoDeEventos.intentoDeEntrega(mensaje(json, Map.of("JMSXDeliveryCount", 3))));
        assertEquals(1, ConsumoDeEventos.intentoDeEntrega(mensaje(json, Map.of())));
        assertEquals(0, ConsumoDeEventos.intentoDeEntrega(null));
    }

    @Test
    void unMensajeQueNoEsDeTextoEsInvalido() {
        Message binario = (Message) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Message.class},
                (proxy, metodo, args) -> {
                    if ("getIntProperty".equals(metodo.getName())) {
                        return 1;
                    }
                    return null;
                });
        ConsumoDeEventos.procesar(binario, contexto, LOG, "[prueba]", evento -> true);

        assertTrue(rollback.get());
    }

    @Test
    void cadaMdbLlamaASuAccion() throws ReflectiveOperationException {
        mdb(new ReceptorConfirmacion()).onMessage(mensaje(json, Map.of()));
        mdb(new SuscriptorAvisoPaciente()).onMessage(mensaje(json, Map.of()));
        mdb(new SuscriptorAvisoProfesional()).onMessage(mensaje(json, Map.of()));

        assertEquals(List.of("confirmacion:42", "recordatorio:42", "profesional:42"), llamadas);
        assertFalse(rollback.get());
    }

    @Test
    void elMonitorDeLaDlqRegistraElMensajeConSuOrigen() throws ReflectiveOperationException {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("id", "ID:99");
        propiedades.put(NotificadorTurnos.PROPIEDAD_EVENTO, "evento-9");
        propiedades.put(MonitorMensajesFallidos.PROPIEDAD_ORIGEN, "jms.queue.ColaConfirmacionesMediConecta");

        mdb(new MonitorMensajesFallidos()).onMessage(mensaje("{}", propiedades));

        assertEquals(List.of("fallido:ID:99:evento-9:jms.queue.ColaConfirmacionesMediConecta:{}"), llamadas);
        assertFalse(rollback.get());
    }
}
