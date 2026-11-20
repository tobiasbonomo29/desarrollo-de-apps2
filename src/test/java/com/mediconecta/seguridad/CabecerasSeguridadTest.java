package com.mediconecta.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cabeceras de seguridad segun el tipo de recurso. */
class CabecerasSeguridadTest {

    private static HttpServletRequest peticion(String uri, boolean segura, String reenviadoComo) {
        return (HttpServletRequest) Proxy.newProxyInstance(CabecerasSeguridadTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "/mediconecta";
                    case "isSecure" -> segura;
                    case "getHeader" -> "X-Forwarded-Proto".equals(args[0]) ? reenviadoComo : null;
                    default -> throw new UnsupportedOperationException(metodo.getName());
                });
    }

    private static Map<String, String> cabecerasPara(HttpServletRequest peticion) {
        Map<String, String> cabeceras = new HashMap<>();
        HttpServletResponse respuesta = (HttpServletResponse) Proxy.newProxyInstance(
                CabecerasSeguridadTest.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, metodo, args) -> {
                    if ("setHeader".equals(metodo.getName())) {
                        cabeceras.put((String) args[0], (String) args[1]);
                        return null;
                    }
                    throw new UnsupportedOperationException(metodo.getName());
                });
        CabecerasSeguridad.aplicar(peticion, respuesta);
        return cabeceras;
    }

    @Test
    void unaPantallaRecibeLaPoliticaEstricta() {
        Map<String, String> cabeceras = cabecerasPara(peticion("/mediconecta/app/turnos.html", false, null));

        String csp = cabeceras.get("Content-Security-Policy");
        assertTrue(csp.contains("script-src 'self'"));
        assertFalse(csp.contains("unsafe-inline"), "sin estilos ni scripts en linea");
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertEquals("nosniff", cabeceras.get("X-Content-Type-Options"));
        assertEquals("DENY", cabeceras.get("X-Frame-Options"));
        assertEquals("no-referrer", cabeceras.get("Referrer-Policy"));
        assertTrue(cabeceras.containsKey("Permissions-Policy"));
        assertNull(cabeceras.get("Cache-Control"));
    }

    @Test
    void laApiNoSeCacheaYNoCargaNada() {
        Map<String, String> cabeceras = cabecerasPara(peticion("/mediconecta/api/turnos", false, null));

        assertEquals(CabecerasSeguridad.CSP_API, cabeceras.get("Content-Security-Policy"));
        assertEquals("no-store", cabeceras.get("Cache-Control"));
    }

    @Test
    void losSistemasSimuladosSeTratanComoApi() {
        assertTrue(CabecerasSeguridad.esServicio("/partner/cobertura"));
        assertTrue(CabecerasSeguridad.esServicio("/ServicioObraSocialLegadoImpl"));
        assertTrue(CabecerasSeguridad.esServicio("/api"));
        assertFalse(CabecerasSeguridad.esServicio("/app/api.html"));
    }

    @Test
    void laDocumentacionPuedeUsarElCdn() {
        String csp = cabecerasPara(peticion("/mediconecta/api-docs.html", false, null)).get("Content-Security-Policy");

        assertEquals(CabecerasSeguridad.CSP_DOCUMENTACION, csp);
        assertTrue(csp.contains("https://cdn.jsdelivr.net"));
    }

    @Test
    void hstsSoloPorHttps() {
        assertNull(cabecerasPara(peticion("/mediconecta/", false, null)).get("Strict-Transport-Security"));
        assertEquals(CabecerasSeguridad.HSTS,
                cabecerasPara(peticion("/mediconecta/", true, null)).get("Strict-Transport-Security"));
        assertEquals(CabecerasSeguridad.HSTS,
                cabecerasPara(peticion("/mediconecta/", false, "https")).get("Strict-Transport-Security"));
    }

    @Test
    void sinUriSeTomaLaRaiz() {
        assertTrue(cabecerasPara(peticion(null, false, null)).get("Content-Security-Policy")
                .startsWith("default-src 'self'"));
    }
}
