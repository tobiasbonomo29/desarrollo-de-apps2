package com.mediconecta.negocio;

import com.mediconecta.datos.memoria.RepositorioMensajesFallidosEnMemoria;
import com.mediconecta.datos.memoria.RepositorioNotificacionesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioTurnosEnMemoria;
import com.mediconecta.mensajeria.NotificadorTurnos;
import com.mediconecta.modelo.CanalNotificacion;
import com.mediconecta.modelo.MensajeFallido;
import com.mediconecta.modelo.Notificacion;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.TipoNotificacion;
import com.mediconecta.modelo.Turno;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que hacen los consumidores JMS: avisos idempotentes y registro de la DLQ. */
class GestionNotificacionesTest {

    private GestionNotificaciones gestion;

    private RepositorioNotificacionesEnMemoria notificaciones;

    private RepositorioMensajesFallidosEnMemoria fallidos;

    private GestionTurnos gestionTurnos;

    private Turno turno;

    @BeforeEach
    void preparar() {
        RepositorioTurnosEnMemoria turnos = new RepositorioTurnosEnMemoria();
        notificaciones = new RepositorioNotificacionesEnMemoria();
        fallidos = new RepositorioMensajesFallidosEnMemoria();

        Paciente ana = new Paciente("Ana Gomez", "30111222");
        ana.setId(1L);
        ana.setUsuario("paciente");
        ana.setEmail("ana.gomez@mail.com");
        ana.setTelefono("11-4455-6677");
        Profesional laura = new Profesional("Dra. Laura Fernandez", "Cardiologia", "MP-10234");
        laura.setId(1L);
        turno = turnos.guardar(new FabricaTurnos().crear(ana, laura, "Cardiologia",
                LocalDateTime.now().plusDays(3).withHour(9).withMinute(30), "Control anual"));

        gestionTurnos = new GestionTurnos();
        Inyeccion.campo(gestionTurnos, "repositorio", turnos);
        Inyeccion.campo(gestionTurnos, "fabricaTurnos", new FabricaTurnos());
        Inyeccion.campo(gestionTurnos, "contexto", Inyeccion.llamador("recepcion", Roles.RECEPCION));

        gestion = new GestionNotificaciones();
        Inyeccion.campo(gestion, "repositorioNotificaciones", notificaciones);
        Inyeccion.campo(gestion, "repositorioMensajesFallidos", fallidos);
        Inyeccion.campo(gestion, "repositorioTurnos", turnos);
        Inyeccion.campo(gestion, "gestionTurnos", gestionTurnos);
        Inyeccion.campo(gestion, "notificadorTurnos", new NotificadorTurnos() {
            @Override
            public String enviarMensajeDefectuoso() {
                return "prueba-123";
            }
        });
    }

    @Test
    void laConfirmacionVaPorMailAlPaciente() {
        assertTrue(gestion.registrarConfirmacion("evento-1", turno.getId()));

        Notificacion aviso = notificaciones.listarPorTurno(turno.getId()).get(0);
        assertEquals(TipoNotificacion.CONFIRMACION, aviso.getTipo());
        assertEquals(CanalNotificacion.EMAIL, aviso.getCanal());
        assertEquals("ana.gomez@mail.com", aviso.getDestinatario());
        assertTrue(aviso.getMensaje().contains("Dra. Laura Fernandez"));
        assertNull(aviso.getProgramadaPara());
    }

    @Test
    void elMismoEventoDosVecesGeneraUnSoloAviso() {
        assertTrue(gestion.registrarConfirmacion("evento-1", turno.getId()));
        assertFalse(gestion.registrarConfirmacion("evento-1", turno.getId()), "la segunda entrega se ignora");

        assertEquals(1, notificaciones.listarPorTurno(turno.getId()).size());
    }

    @Test
    void cadaConsumidorGeneraSuPropioAvisoDelMismoEvento() {
        gestion.registrarConfirmacion("evento-1", turno.getId());
        gestion.registrarRecordatorio("evento-1", turno.getId());
        gestion.registrarAvisoProfesional("evento-1", turno.getId());
        gestion.registrarRecordatorio("evento-1", turno.getId());
        gestion.registrarAvisoProfesional("evento-1", turno.getId());

        List<Notificacion> avisos = notificaciones.listarPorTurno(turno.getId());
        assertEquals(3, avisos.size());
    }

    @Test
    void elRecordatorioSeProgramaUnDiaAntesPorSms() {
        gestion.registrarRecordatorio("evento-1", turno.getId());

        Notificacion recordatorio = notificaciones.listarPorTurno(turno.getId()).get(0);
        assertEquals(CanalNotificacion.SMS, recordatorio.getCanal());
        assertEquals("11-4455-6677", recordatorio.getDestinatario());
        assertEquals(turno.getFechaHora().minusHours(24), recordatorio.getProgramadaPara());
    }

    @Test
    void siFaltanMenosDe24HorasElRecordatorioSaleYa() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 19, 10, 0);
        assertEquals(ahora, GestionNotificaciones.momentoDelRecordatorio(ahora.plusHours(3), ahora));
        assertEquals(ahora.plusHours(6), GestionNotificaciones.momentoDelRecordatorio(ahora.plusHours(30), ahora));
    }

    @Test
    void elAvisoAlProfesionalVaASuAgendaConElMotivo() {
        gestion.registrarAvisoProfesional("evento-1", turno.getId());

        Notificacion aviso = notificaciones.listarPorTurno(turno.getId()).get(0);
        assertEquals(CanalNotificacion.AGENDA, aviso.getCanal());
        assertEquals("Dra. Laura Fernandez", aviso.getDestinatario());
        assertTrue(aviso.getMensaje().contains("Control anual"));
    }

    @Test
    void sinMailNiTelefonoElAvisoLoDice() {
        turno.getPaciente().setEmail(null);
        turno.getPaciente().setTelefono(" ");
        gestion.registrarConfirmacion("evento-1", turno.getId());
        gestion.registrarRecordatorio("evento-1", turno.getId());

        List<Notificacion> avisos = notificaciones.listarPorTurno(turno.getId());
        assertTrue(avisos.get(0).getDestinatario().contains("sin email"));
        assertTrue(avisos.get(1).getDestinatario().contains("sin telefono"));
    }

    @Test
    void unTurnoInexistenteHaceFallarElMensajeParaQueSeReintente() {
        assertThrows(NoEncontradoException.class, () -> gestion.registrarConfirmacion("evento-1", 999L));
    }

    @Test
    void unMensajeFallidoSeRegistraUnaSolaVez() {
        assertTrue(gestion.registrarMensajeFallido("ID:1", "evento-1", "jms.queue.Cola", "{}"));
        assertFalse(gestion.registrarMensajeFallido("ID:1", "evento-1", "jms.queue.Cola", "{}"));

        List<MensajeFallido> lista = gestion.mensajesFallidos();
        assertEquals(1, lista.size());
        assertEquals("jms.queue.Cola", lista.get(0).getDestinoOriginal());
    }

    @Test
    void elCuerpoDeUnMensajeFallidoSeRecorta() {
        gestion.registrarMensajeFallido("ID:2", null, null, "x".repeat(MensajeFallido.LARGO_CUERPO + 50));

        assertEquals(MensajeFallido.LARGO_CUERPO, gestion.mensajesFallidos().get(0).getCuerpo().length());
    }

    @Test
    void unPacienteSoloVeLosAvisosDeSusTurnos() {
        gestion.registrarConfirmacion("evento-1", turno.getId());

        Inyeccion.campo(gestionTurnos, "contexto", Inyeccion.llamador("paciente", Roles.PACIENTE));
        assertEquals(1, gestion.notificacionesDeTurno(turno.getId()).size());

        Inyeccion.campo(gestionTurnos, "contexto", Inyeccion.llamador("otro-paciente", Roles.PACIENTE));
        assertThrows(ReglaNegocioException.class, () -> gestion.notificacionesDeTurno(turno.getId()));
    }

    @Test
    void elMensajeDefectuosoLoMandaElNotificador() {
        assertEquals("prueba-123", gestion.enviarMensajeDefectuoso());
    }
}
