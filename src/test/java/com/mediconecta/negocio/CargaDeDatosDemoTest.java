package com.mediconecta.negocio;

import com.mediconecta.datos.memoria.RepositorioPacientesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioProfesionalesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioTurnosEnMemoria;
import com.mediconecta.datos.memoria.RepositorioUsuariosEnMemoria;
import com.mediconecta.seguridad.HashDeClaves;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La carga inicial no deja usuarios con una contrasena escrita en el codigo. */
class CargaDeDatosDemoTest {

    private static final String PROPIEDAD = "mediconecta.demo.clave";

    private CargaDeDatosDemo carga;

    private RepositorioUsuariosEnMemoria usuarios;

    private RepositorioProfesionalesEnMemoria profesionales;

    private RepositorioTurnosEnMemoria turnos;

    @BeforeEach
    void preparar() {
        usuarios = new RepositorioUsuariosEnMemoria();
        profesionales = new RepositorioProfesionalesEnMemoria();
        turnos = new RepositorioTurnosEnMemoria();
        carga = new CargaDeDatosDemo();
        Inyeccion.campo(carga, "repositorioUsuarios", usuarios);
        Inyeccion.campo(carga, "repositorioProfesionales", profesionales);
        Inyeccion.campo(carga, "repositorioPacientes", new RepositorioPacientesEnMemoria());
        Inyeccion.campo(carga, "repositorioTurnos", turnos);
        Inyeccion.campo(carga, "fabricaTurnos", new FabricaTurnos());
    }

    @AfterEach
    void limpiar() {
        System.clearProperty(PROPIEDAD);
    }

    @Test
    void sinContrasenaConfiguradaNoSeCreaNingunUsuario() {
        // Solo vale si tampoco esta la variable de entorno en la maquina que corre el test.
        if (System.getenv("MEDICONECTA_DEMO_CLAVE") != null) {
            return;
        }
        carga.cargar();

        assertEquals(0, usuarios.contar());
        assertEquals(4, profesionales.listarTodos().size(), "los datos de ejemplo se cargan igual");
        assertEquals(3, turnos.listarTodos().size());
    }

    @Test
    void conContrasenaConfiguradaSeCreanLosCuatroUsuarios() {
        System.setProperty(PROPIEDAD, "Clave.De.Prueba");
        carga.cargar();

        assertEquals(4, usuarios.contar());
        assertTrue(HashDeClaves.coincide("Clave.De.Prueba",
                usuarios.buscarPorUsuario("admin").orElseThrow().getHashClave()));
    }

    @Test
    void unaContrasenaDemasiadoCortaSeRechaza() {
        System.setProperty(PROPIEDAD, "corta");
        carga.cargar();

        assertEquals(0, usuarios.contar());
    }

    @Test
    void esIdempotente() {
        System.setProperty(PROPIEDAD, "Clave.De.Prueba");
        carga.cargar();
        carga.cargar();

        assertEquals(4, usuarios.contar());
        assertEquals(4, profesionales.listarTodos().size());
    }
}
