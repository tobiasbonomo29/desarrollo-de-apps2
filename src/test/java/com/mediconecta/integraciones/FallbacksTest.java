package com.mediconecta.integraciones;

import com.mediconecta.datos.memoria.RepositorioPacientesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioProfesionalesEnMemoria;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.negocio.DemasiadasSolicitudesException;
import com.mediconecta.negocio.GestionPacientes;
import com.mediconecta.negocio.GestionProfesionales;
import com.mediconecta.negocio.IntegracionExternaException;
import com.mediconecta.seguridad.LimitadorDeFrecuencia;

import jakarta.ejb.SessionContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resiliencia de las integraciones: timeout con fallback en cobertura y en el
 * gateway de IA, y limite de frecuencia del gateway.
 */
class FallbacksTest {

    private static final List<String> ESPECIALIDADES =
            List.of("Cardiologia", "Clinica Medica", "Pediatria", "Traumatologia");

    private static void campo(Object destino, String nombre, Object valor) {
        try {
            Field f = destino.getClass().getDeclaredField(nombre);
            f.setAccessible(true);
            f.set(destino, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SessionContext comoRecepcion() {
        Principal principal = () -> "recepcion";
        return (SessionContext) Proxy.newProxyInstance(FallbacksTest.class.getClassLoader(),
                new Class<?>[]{SessionContext.class}, (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "getCallerPrincipal" -> principal;
                    case "isCallerInRole" -> "RECEPCION".equals(args[0]);
                    default -> throw new UnsupportedOperationException(metodo.getName());
                });
    }

    private static List<MensajeChat> dice(String texto) {
        return List.of(new MensajeChat(MensajeChat.USUARIO, texto));
    }

    // ------------------------------------------------------------- cobertura --

    private GestorCobertura gestorCobertura;

    /** Respuesta que dan los adaptadores falsos; null = fallan por timeout. */
    private ResultadoCobertura respuestaExterna;

    private final class AdaptadorFalso extends AdaptadorObraSocialPartner {
        @Override
        public ResultadoCobertura validar(String obraSocial, String numeroAfiliado) {
            if (respuestaExterna == null) {
                throw new IntegracionExternaException("El partner de obras sociales no respondio a tiempo.",
                        new SocketTimeoutException("Read timed out"));
            }
            return respuestaExterna;
        }
    }

    @BeforeEach
    void preparar() {
        RepositorioPacientesEnMemoria pacientes = new RepositorioPacientesEnMemoria();
        Paciente bruno = new Paciente("Bruno Diaz", "28999888");
        bruno.setCobertura("Swiss Medical SMG02");
        pacientes.guardar(bruno);
        GestionPacientes gestionPacientes = new GestionPacientes();
        campo(gestionPacientes, "repositorio", pacientes);
        campo(gestionPacientes, "contexto", comoRecepcion());

        gestorCobertura = new GestorCobertura();
        campo(gestorCobertura, "gestionPacientes", gestionPacientes);
        campo(gestorCobertura, "adaptadorPartner", new AdaptadorFalso());
        campo(gestorCobertura, "cache", new CacheCobertura());
    }

    @Test
    void siElPartnerNoRespondeYNoHayCacheSeDevuelveUnResultadoAVerificar() {
        respuestaExterna = null;
        ResultadoCobertura resultado = gestorCobertura.validarCobertura("28999888");

        assertTrue(resultado.isContingencia());
        assertEquals("CONTINGENCIA", resultado.getOrigen());
        assertFalse(resultado.isVigente());
        assertTrue(resultado.getMensaje().contains("no respondio a tiempo"));
    }

    @Test
    void siElPartnerNoRespondeSeUsaElUltimoResultadoBueno() {
        respuestaExterna = new ResultadoCobertura(true, "SMG20", 3500, "PARTNER");
        assertFalse(gestorCobertura.validarCobertura("28999888").isContingencia());

        respuestaExterna = null;
        ResultadoCobertura respaldo = gestorCobertura.validarCobertura("28999888");
        assertTrue(respaldo.isContingencia());
        assertEquals("CACHE", respaldo.getOrigen());
        assertTrue(respaldo.isVigente());
        assertEquals("SMG20", respaldo.getPlan());
    }

    // --------------------------------------------------------- gateway de IA --

    private GestorOrientacion gateway(OrientadorEspecialidad orientador) {
        RepositorioProfesionalesEnMemoria profesionales = new RepositorioProfesionalesEnMemoria();
        ESPECIALIDADES.forEach(e -> profesionales.guardar(new Profesional("Dr. " + e, e, "MP-" + e)));
        GestionProfesionales gestionProfesionales = new GestionProfesionales();
        campo(gestionProfesionales, "repositorio", profesionales);
        GestorOrientacion gestor = new GestorOrientacion();
        campo(gestor, "gestionProfesionales", gestionProfesionales);
        campo(gestor, "orientador", orientador);
        campo(gestor, "contexto", comoRecepcion());
        campo(gestor, "limitador", new LimitadorDeFrecuencia());
        return gestor;
    }

    @Test
    void siLaIaNoRespondeElGatewayUsaLaOrientacionDeRespaldo() {
        GestorOrientacion gestor = gateway((conversacion, especialidades) -> {
            throw new IntegracionExternaException("El asistente de IA no respondio.", new SocketTimeoutException());
        });

        SugerenciaEspecialidad sugerencia = gestor.sugerirEspecialidad(dice("Me cai y me duele mucho la rodilla"));
        assertTrue(sugerencia.isContingencia());
        assertEquals("Traumatologia", sugerencia.getEspecialidad());
    }

    @Test
    void conLaIaDisponibleNoHayContingencia() {
        GestorOrientacion gestor = gateway((conversacion, especialidades) ->
                new SugerenciaEspecialidad("Cardiologia te puede ayudar.", "cardiologia", false));

        SugerenciaEspecialidad sugerencia = gestor.sugerirEspecialidad(dice("Tengo palpitaciones"));
        assertFalse(sugerencia.isContingencia());
        assertEquals("Cardiologia", sugerencia.getEspecialidad());
    }

    @Test
    void elGatewayLimitaLasConsultasPorMinuto() {
        GestorOrientacion gestor = gateway((conversacion, especialidades) ->
                new SugerenciaEspecialidad("ok", null, false));
        for (int i = 0; i < GestorOrientacion.MAXIMO_POR_MINUTO; i++) {
            gestor.sugerirEspecialidad(dice("hola"));
        }
        assertThrows(DemasiadasSolicitudesException.class, () -> gestor.sugerirEspecialidad(dice("hola")));
    }

    @Test
    void elRespaldoDetectaEmergencias() {
        SugerenciaEspecialidad sugerencia = OrientacionDeRespaldo.orientar(
                dice("Tengo un dolor fuerte en el pecho y falta de aire"), ESPECIALIDADES);
        assertTrue(sugerencia.isUrgente());
        assertNull(sugerencia.getEspecialidad());
        assertTrue(sugerencia.getMensaje().contains("107"));
    }

    @Test
    void elRespaldoReconoceVariasEspecialidadesYCaeEnClinica() {
        assertEquals("Pediatria", OrientacionDeRespaldo.orientar(dice("Mi hijo tiene fiebre"), ESPECIALIDADES).getEspecialidad());
        assertEquals("Cardiologia", OrientacionDeRespaldo.orientar(dice("Siento palpitaciones"), ESPECIALIDADES).getEspecialidad());
        assertEquals("Clinica Medica", OrientacionDeRespaldo.orientar(dice("Quiero hacerme un chequeo"), ESPECIALIDADES).getEspecialidad());
        assertEquals("Clinica Medica", OrientacionDeRespaldo.orientar(dice("algo raro"), ESPECIALIDADES).getEspecialidad());
    }

    @Test
    void elRespaldoSoloSugiereEspecialidadesDeLaLista() {
        SugerenciaEspecialidad sinDermato = OrientacionDeRespaldo.orientar(dice("Tengo una mancha en la piel"),
                List.of("Cardiologia"));
        assertNull(sinDermato.getEspecialidad());
        assertTrue(sinDermato.isContingencia());
    }
}
