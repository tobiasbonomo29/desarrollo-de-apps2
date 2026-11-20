package com.mediconecta.integraciones;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Timeouts de las integraciones y direccion del servicio SOAP. */
class TiemposDeEsperaTest {

    @AfterEach
    void limpiar() {
        System.clearProperty(TiemposDeEspera.PROPIEDAD_CONEXION);
        System.clearProperty(TiemposDeEspera.PROPIEDAD_RESPUESTA);
        System.clearProperty("mediconecta.soap.url");
        System.clearProperty("mediconecta.soap.wsdl");
    }

    @Test
    void sinConfiguracionUsaLosValoresPorOmision() {
        assertEquals(Duration.ofMillis(TiemposDeEspera.CONEXION_POR_DEFECTO_MS), TiemposDeEspera.conexion());
        assertEquals(Duration.ofMillis(TiemposDeEspera.RESPUESTA_POR_DEFECTO_MS), TiemposDeEspera.respuesta());
    }

    @Test
    void seConfiguranEnMilisegundos() {
        System.setProperty(TiemposDeEspera.PROPIEDAD_CONEXION, "750");
        System.setProperty(TiemposDeEspera.PROPIEDAD_RESPUESTA, " 12000 ");

        assertEquals(Duration.ofMillis(750), TiemposDeEspera.conexion());
        assertEquals(Duration.ofSeconds(12), TiemposDeEspera.respuesta());
    }

    @Test
    void unValorInvalidoNoDejaSinTimeout() {
        System.setProperty(TiemposDeEspera.PROPIEDAD_CONEXION, "infinito");
        System.setProperty(TiemposDeEspera.PROPIEDAD_RESPUESTA, "0");

        assertEquals(Duration.ofMillis(TiemposDeEspera.CONEXION_POR_DEFECTO_MS), TiemposDeEspera.conexion());
        assertEquals(Duration.ofMillis(TiemposDeEspera.RESPUESTA_POR_DEFECTO_MS), TiemposDeEspera.respuesta());
    }

    @Test
    void elClienteSoapRecibeLosTimeoutsDeCadaImplementacion() {
        Map<String, Object> contexto = new HashMap<>();
        TiemposDeEspera.aplicarSoap(contexto, Duration.ofSeconds(2), Duration.ofSeconds(5));

        assertEquals(2000, contexto.get("jakarta.xml.ws.client.connectionTimeout"));
        assertEquals(5000, contexto.get("jakarta.xml.ws.client.receiveTimeout"));
        assertEquals(2000, contexto.get("javax.xml.ws.client.connectionTimeout"));
        assertEquals(5000, contexto.get("com.sun.xml.ws.request.timeout"));
    }

    @Test
    void laDireccionSoapAceptaLaPropiedadNuevaYLaVieja() {
        assertEquals(AdaptadorObraSocialLegado.DIRECCION_POR_DEFECTO, AdaptadorObraSocialLegado.direccion());

        System.setProperty("mediconecta.soap.wsdl", "http://localhost:8180/mediconecta/ServicioObraSocialLegadoImpl?wsdl");
        assertEquals("http://localhost:8180/mediconecta/ServicioObraSocialLegadoImpl", AdaptadorObraSocialLegado.direccion());

        System.setProperty("mediconecta.soap.url", "http://legado:9000/servicio");
        assertEquals("http://legado:9000/servicio", AdaptadorObraSocialLegado.direccion());
    }

    @Test
    void elContratoWsdlViajaDentroDelWar() {
        assertEquals(true, AdaptadorObraSocialLegado.class.getResource(AdaptadorObraSocialLegado.WSDL_LOCAL) != null,
                "falta " + AdaptadorObraSocialLegado.WSDL_LOCAL);
    }
}
