package com.mediconecta.negocio;

import com.mediconecta.datos.memoria.RepositorioIdempotenciaEnMemoria;
import com.mediconecta.datos.memoria.RepositorioPacientesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioProfesionalesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioTurnosEnMemoria;
import com.mediconecta.mensajeria.NotificadorTurnos;
import com.mediconecta.modelo.ClaveIdempotencia;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FachadaReservas#reservarConClave}: la proteccion con
 * {@code Idempotency-Key} contra pedidos repetidos.
 */
class FachadaReservasTest {

    private static final String CLAVE = "5f0c7d2e-8b1a-4f3e-9c4d-2a6b7e8f9a01";

    private FachadaReservas fachada;

    private RepositorioTurnosEnMemoria turnos;

    private RepositorioIdempotenciaEnMemoria claves;

    private final List<Turno> notificados = new ArrayList<>();

    private Long profesionalId;

    /** Productor JMS falso: anota lo que se habria enviado. */
    private final class NotificadorDePrueba extends NotificadorTurnos {
        @Override
        public void turnoConfirmado(Turno turno) {
            notificados.add(turno);
        }
    }

    @BeforeEach
    void preparar() {
        turnos = new RepositorioTurnosEnMemoria();
        claves = new RepositorioIdempotenciaEnMemoria();
        RepositorioPacientesEnMemoria pacientes = new RepositorioPacientesEnMemoria();
        RepositorioProfesionalesEnMemoria profesionales = new RepositorioProfesionalesEnMemoria();

        Paciente ana = new Paciente("Ana Gomez", "30111222");
        ana.setUsuario("paciente");
        pacientes.guardar(ana);
        profesionalId = profesionales.guardar(new Profesional("Dra. Laura Fernandez", "Cardiologia", "MP-10234")).getId();

        var llamador = Inyeccion.llamador("recepcion", Roles.RECEPCION);

        GestionTurnos gestionTurnos = new GestionTurnos();
        Inyeccion.campo(gestionTurnos, "repositorio", turnos);
        Inyeccion.campo(gestionTurnos, "fabricaTurnos", new FabricaTurnos());
        Inyeccion.campo(gestionTurnos, "contexto", llamador);

        GestionPacientes gestionPacientes = new GestionPacientes();
        Inyeccion.campo(gestionPacientes, "repositorio", pacientes);
        Inyeccion.campo(gestionPacientes, "gestionTurnos", gestionTurnos);
        Inyeccion.campo(gestionPacientes, "contexto", llamador);

        GestionProfesionales gestionProfesionales = new GestionProfesionales();
        Inyeccion.campo(gestionProfesionales, "repositorio", profesionales);
        Inyeccion.campo(gestionProfesionales, "gestionTurnos", gestionTurnos);
        Inyeccion.campo(gestionProfesionales, "contexto", llamador);

        fachada = new FachadaReservas();
        Inyeccion.campo(fachada, "gestionTurnos", gestionTurnos);
        Inyeccion.campo(fachada, "gestionPacientes", gestionPacientes);
        Inyeccion.campo(fachada, "gestionProfesionales", gestionProfesionales);
        Inyeccion.campo(fachada, "notificadorTurnos", new NotificadorDePrueba());
        Inyeccion.campo(fachada, "repositorioIdempotencia", claves);
        Inyeccion.campo(fachada, "contexto", llamador);
    }

    private SolicitudTurno pedido(String hora, String motivo) {
        SolicitudTurno solicitud = new SolicitudTurno();
        solicitud.setDocumentoPaciente("30111222");
        solicitud.setProfesionalId(profesionalId);
        solicitud.setEspecialidad("Cardiologia");
        solicitud.setFechaHora(LocalDate.now().plusDays(2) + "T" + hora);
        solicitud.setMotivo(motivo);
        return solicitud;
    }

    @Test
    void sinClaveEsUnaReservaComun() {
        ResultadoReserva resultado = fachada.reservarConClave(pedido("09:30", "Control"), null);

        assertFalse(resultado.repetida());
        assertEquals(1, turnos.listarTodos().size());
        assertEquals(1, notificados.size(), "se avisa por JMS una vez");
    }

    @Test
    void elMismoPedidoConLaMismaClaveNoCreaOtroTurno() {
        ResultadoReserva primero = fachada.reservarConClave(pedido("09:30", "Control"), CLAVE);
        ResultadoReserva repetido = fachada.reservarConClave(pedido("09:30", "Control"), CLAVE);

        assertFalse(primero.repetida());
        assertTrue(repetido.repetida());
        assertEquals(primero.turno().getId(), repetido.turno().getId());
        assertEquals(1, turnos.listarTodos().size(), "un solo turno");
        assertEquals(1, notificados.size(), "un solo aviso JMS");
    }

    @Test
    void laClavePuedeVenirEntreComillas() {
        fachada.reservarConClave(pedido("09:30", "Control"), CLAVE);
        ResultadoReserva repetido = fachada.reservarConClave(pedido("09:30", "Control"), "\"" + CLAVE + "\"");

        assertTrue(repetido.repetida());
    }

    @Test
    void laMismaClaveConOtroPedidoSeRechazaCon422() {
        fachada.reservarConClave(pedido("09:30", "Control"), CLAVE);

        ConflictoIdempotenciaException error = assertThrows(ConflictoIdempotenciaException.class,
                () -> fachada.reservarConClave(pedido("10:00", "Otro motivo"), CLAVE));

        assertEquals(ConflictoIdempotenciaException.Motivo.PEDIDO_DISTINTO, error.motivo());
        assertEquals(1, turnos.listarTodos().size());
    }

    @Test
    void claveDistintaEsOtroPedido() {
        fachada.reservarConClave(pedido("09:30", "Control"), CLAVE);
        ResultadoReserva otro = fachada.reservarConClave(pedido("10:00", "Control"), "otra-clave-0001");

        assertFalse(otro.repetida());
        assertEquals(2, turnos.listarTodos().size());
    }

    @Test
    void unaClaveVencidaYaNoProtege() {
        Turno viejo = fachada.reservarConClave(pedido("09:30", "Control"), null).turno();
        claves.registrar(new ClaveIdempotencia(CLAVE, "recepcion", "huella-de-otro-pedido", viejo,
                LocalDateTime.now().minus(FachadaReservas.VIGENCIA_CLAVE).minusMinutes(1)));

        ResultadoReserva nuevo = fachada.reservarConClave(pedido("10:00", "Control"), CLAVE);

        assertFalse(nuevo.repetida());
        assertNotEquals(viejo.getId(), nuevo.turno().getId());
    }

    @Test
    void siOtraTransaccionRegistroLaClaveAntesSeInformaEnCurso() {
        // Simula la carrera: la consulta no encuentra la clave, pero al
        // registrarla ya existe (otra transaccion la confirmo en el medio).
        Inyeccion.campo(fachada, "repositorioIdempotencia", new RepositorioIdempotenciaEnMemoria() {
            @Override
            public boolean registrar(ClaveIdempotencia clave) {
                return false;
            }
        });

        ConflictoIdempotenciaException error = assertThrows(ConflictoIdempotenciaException.class,
                () -> fachada.reservarConClave(pedido("09:30", "Control"), CLAVE));
        assertEquals(ConflictoIdempotenciaException.Motivo.EN_CURSO, error.motivo());
    }

    @Test
    void unaClaveConFormatoInvalidoSeRechaza() {
        assertThrows(ReglaNegocioException.class, () -> fachada.reservarConClave(pedido("09:30", "x"), "corta"));
        assertThrows(ReglaNegocioException.class,
                () -> fachada.reservarConClave(pedido("09:30", "x"), "tiene espacios adentro"));
    }

    @Test
    void sinCuerpoSeRechaza() {
        assertThrows(ReglaNegocioException.class, () -> fachada.reservarConClave(null, CLAVE));
    }

    @Test
    void normalizarClaveSinValorDevuelveNull() {
        assertNull(FachadaReservas.normalizarClave(null));
        assertNull(FachadaReservas.normalizarClave("   "));
        assertEquals(CLAVE, FachadaReservas.normalizarClave("  " + CLAVE + " "));
    }

    @Test
    void laHuellaIgnoraEspaciosYDistingueCampos() {
        SolicitudTurno a = pedido("09:30", "Control");
        SolicitudTurno b = pedido("09:30", "  Control  ");
        SolicitudTurno c = pedido("09:30", "Control anual");

        assertEquals(HuellaSolicitud.de(a), HuellaSolicitud.de(b));
        assertNotEquals(HuellaSolicitud.de(a), HuellaSolicitud.de(c));
        assertEquals(64, HuellaSolicitud.de(a).length(), "SHA-256 en hexadecimal");
    }

    @Test
    void unPacienteNuevoSeDaDeAltaAlReservarSiTraeNombre() {
        SolicitudTurno solicitud = pedido("11:00", null);
        solicitud.setDocumentoPaciente("40111222");
        assertThrows(ReglaNegocioException.class, () -> fachada.reservar(solicitud), "sin nombre no se da de alta");

        solicitud.setNombrePaciente("Paciente Nuevo");
        Turno turno = fachada.reservar(solicitud);
        assertEquals("Paciente Nuevo", turno.getPaciente().getNombre());
    }

    @Test
    void elProfesionalSePuedeIndicarPorNombreComoEnLaEntrega1() {
        SolicitudTurno solicitud = pedido("11:30", null);
        solicitud.setProfesionalId(null);
        solicitud.setNombreProfesional(" dra. laura fernandez ");
        assertEquals(profesionalId, fachada.reservar(solicitud).getProfesional().getId());

        solicitud.setNombreProfesional("Dr. Inexistente");
        assertThrows(ReglaNegocioException.class, () -> fachada.reservar(solicitud));
        solicitud.setNombreProfesional(" ");
        assertThrows(ReglaNegocioException.class, () -> fachada.reservar(solicitud));
    }

    @Test
    void sinDocumentoNoHayReserva() {
        SolicitudTurno solicitud = pedido("11:30", null);
        solicitud.setDocumentoPaciente(" ");
        assertThrows(ReglaNegocioException.class, () -> fachada.reservar(solicitud));
        assertThrows(ReglaNegocioException.class, () -> fachada.reservar(null));
    }

    @Test
    void elTableroResumeElDia() {
        fachada.reservar(pedido("09:30", null));

        ResumenAgenda resumen = fachada.resumenDelDia();
        assertEquals(1, resumen.getPendientes());
        assertEquals(1, resumen.getPacientesActivos());
        assertEquals(1, resumen.getProfesionalesActivos());
        assertEquals(1, resumen.getProximosTurnos().size());
        assertEquals(0, resumen.getTurnosDeHoy());

        assertEquals(List.of("Cardiologia"), fachada.especialidades());
        assertEquals(1, fachada.profesionalesDe("Cardiologia").size());
        assertTrue(fachada.horariosLibres(profesionalId, LocalDate.now().plusDays(2)).isEmpty(), "sin agenda cargada");
    }
}
