package com.mediconecta.seguridad;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** El 401 sale en Problem Details y el token se lee de Authorization: Bearer. */
class MecanismoAutenticacionJwtTest {

    private static HttpServletRequest conAutorizacion(String valor) {
        return (HttpServletRequest) Proxy.newProxyInstance(getLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, metodo, args) -> "getHeader".equals(metodo.getName()) ? valor : null);
    }

    private static ClassLoader getLoader() {
        return MecanismoAutenticacionJwtTest.class.getClassLoader();
    }

    @Test
    void elProblemaDel401TieneLosCamposDeLaRfc() {
        JsonObject cuerpo = Json.createReader(new StringReader(
                MecanismoAutenticacionJwt.problema("El token vencio \"ya\".", "/mediconecta/api/turnos"))).readObject();

        assertEquals(MecanismoAutenticacionJwt.TIPO_NO_AUTENTICADO, cuerpo.getString("type"));
        assertEquals(401, cuerpo.getInt("status"));
        assertEquals("El token vencio \"ya\".", cuerpo.getString("detail"), "JSON-P escapa las comillas");
        assertEquals("/mediconecta/api/turnos", cuerpo.getString("instance"));
    }

    @Test
    void sinInstanciaNoSeIncluye() {
        JsonObject cuerpo = Json.createReader(new StringReader(
                MecanismoAutenticacionJwt.problema("Falta el token.", null))).readObject();

        assertFalse(cuerpo.containsKey("instance"));
    }

    @Test
    void elTokenSeLeeDeLaCabeceraBearer() {
        assertEquals("abc.def.ghi", MecanismoAutenticacionJwt.extraerToken(conAutorizacion("Bearer abc.def.ghi")));
        assertEquals("abc.def.ghi", MecanismoAutenticacionJwt.extraerToken(conAutorizacion("bearer   abc.def.ghi ")));
        assertNull(MecanismoAutenticacionJwt.extraerToken(conAutorizacion("Basic dXN1YXJpbzpjbGF2ZQ==")));
        assertNull(MecanismoAutenticacionJwt.extraerToken(conAutorizacion("Bearer ")));
        assertNull(MecanismoAutenticacionJwt.extraerToken(conAutorizacion(null)));
    }
}
