package com.mediconecta.negocio;

import com.mediconecta.datos.memoria.RepositorioUsuariosEnMemoria;
import com.mediconecta.modelo.Usuario;
import com.mediconecta.seguridad.HashDeClaves;
import com.mediconecta.seguridad.LimitadorDeFrecuencia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioAutenticacionTest {

    private ServicioAutenticacion servicio;

    private RepositorioUsuariosEnMemoria repositorio;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioUsuariosEnMemoria();
        repositorio.guardar(new Usuario("recepcion", HashDeClaves.calcular("Demo.2026"), Set.of(Roles.RECEPCION)));
        servicio = new ServicioAutenticacion();
        Inyeccion.campo(servicio, "repositorio", repositorio);
        Inyeccion.campo(servicio, "limitador", new LimitadorDeFrecuencia());
    }

    @Test
    void conLasCredencialesCorrectasDevuelveElUsuarioConSusRoles() {
        Usuario usuario = servicio.autenticar("recepcion", "Demo.2026");
        assertEquals("recepcion", usuario.getUsuario());
        assertEquals(Set.of(Roles.RECEPCION), usuario.getRoles());
    }

    @Test
    void conLaClaveIncorrectaSeRechaza() {
        assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar("recepcion", "otra"));
    }

    @Test
    void unUsuarioInexistenteRecibeElMismoRechazo() {
        CredencialesInvalidasException inexistente = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.autenticar("nadie", "Demo.2026"));
        CredencialesInvalidasException claveMala = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.autenticar("recepcion", "otra"));
        assertEquals(claveMala.getMessage(), inexistente.getMessage());
    }

    @Test
    void unUsuarioInactivoNoPuedeEntrar() {
        repositorio.buscarPorUsuario("recepcion").orElseThrow().setActivo(false);
        assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar("recepcion", "Demo.2026"));
    }

    @Test
    void sinDatosSeRechaza() {
        assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar(null, null));
        assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar(" ", "x"));
    }

    @Test
    void despuesDeCincoFallosSeBloqueaConUn429AunqueLaClaveSeaCorrecta() {
        for (int i = 0; i < ServicioAutenticacion.MAXIMO_FALLOS_POR_USUARIO; i++) {
            assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar("recepcion", "mala", "10.0.0.1"));
        }
        DemasiadasSolicitudesException bloqueo = assertThrows(DemasiadasSolicitudesException.class,
                () -> servicio.autenticar("RECEPCION ", "Demo.2026", "10.0.0.2"));
        assertTrue(bloqueo.segundosDeEspera() > 0);
    }

    @Test
    void unLoginCorrectoBorraLosFallosAnteriores() {
        for (int i = 0; i < ServicioAutenticacion.MAXIMO_FALLOS_POR_USUARIO - 1; i++) {
            assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar("recepcion", "mala"));
        }
        servicio.autenticar("recepcion", "Demo.2026");
        assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar("recepcion", "mala"));
        assertEquals("recepcion", servicio.autenticar("recepcion", "Demo.2026").getUsuario());
    }

    @Test
    void muchosFallosDesdeUnaMismaIpBloqueanEsaIp() {
        for (int i = 0; i < ServicioAutenticacion.MAXIMO_FALLOS_POR_ORIGEN; i++) {
            String usuario = "inexistente" + i;
            assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar(usuario, "x", "10.9.9.9"));
        }
        assertThrows(DemasiadasSolicitudesException.class, () -> servicio.autenticar("recepcion", "Demo.2026", "10.9.9.9"));
        assertEquals("recepcion", servicio.autenticar("recepcion", "Demo.2026", "10.1.1.1").getUsuario());
    }
}
