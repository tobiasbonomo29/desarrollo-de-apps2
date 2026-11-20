package com.mediconecta.seguridad;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServicioJwtTest {

    private static final byte[] SECRETO =
            "un-secreto-de-prueba-de-al-menos-32-bytes!!".getBytes(StandardCharsets.UTF_8);

    private static final Instant AHORA = Instant.parse("2026-11-13T12:00:00Z");

    private static ServicioJwt servicio(Instant momento) {
        return new ServicioJwt(SECRETO, Duration.ofMinutes(60), Clock.fixed(momento, ZoneOffset.UTC));
    }

    @Test
    void unTokenEmitidoSeValidaConSuUsuarioYRoles() {
        String token = servicio(AHORA).emitir("recepcion", List.of("RECEPCION"));

        ClaimsJwt claims = servicio(AHORA.plusSeconds(60)).validar(token);

        assertEquals("recepcion", claims.usuario());
        assertEquals(Set.of("RECEPCION"), claims.roles());
        assertEquals(AHORA.plus(Duration.ofMinutes(60)), claims.expira());
    }

    @Test
    void unTokenVencidoSeRechaza() {
        String token = servicio(AHORA).emitir("recepcion", List.of("RECEPCION"));

        assertThrows(TokenInvalidoException.class,
                () -> servicio(AHORA.plus(Duration.ofMinutes(61))).validar(token));
    }

    @Test
    void cambiarUnRolDentroDelTokenRompeLaFirma() {
        String token = servicio(AHORA).emitir("paciente", List.of("PACIENTE"));
        String[] partes = token.split("\\.");
        String contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        String adulterado = contenido.replace("PACIENTE", "ADMIN");
        String falso = partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(adulterado.getBytes(StandardCharsets.UTF_8))
                + "." + partes[2];

        assertThrows(TokenInvalidoException.class, () -> servicio(AHORA).validar(falso));
    }

    @Test
    void unTokenFirmadoConOtroSecretoSeRechaza() {
        ServicioJwt otro = new ServicioJwt(
                "otro-secreto-distinto-tambien-de-32-bytes!!".getBytes(StandardCharsets.UTF_8),
                Duration.ofMinutes(60), Clock.fixed(AHORA, ZoneOffset.UTC));
        String token = otro.emitir("admin", List.of("ADMIN"));

        assertThrows(TokenInvalidoException.class, () -> servicio(AHORA).validar(token));
    }

    @Test
    void elAlgoritmoNoneSeRechaza() {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String cabecera = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String contenido = b64.encodeToString(("{\"iss\":\"mediconecta\",\"sub\":\"admin\",\"groups\":[\"ADMIN\"],\"exp\":"
                + AHORA.plusSeconds(3600).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));

        assertThrows(TokenInvalidoException.class, () -> servicio(AHORA).validar(cabecera + "." + contenido + "."));
    }

    @Test
    void basuraNoEsUnToken() {
        ServicioJwt servicio = servicio(AHORA);
        assertThrows(TokenInvalidoException.class, () -> servicio.validar(null));
        assertThrows(TokenInvalidoException.class, () -> servicio.validar("abc"));
        assertThrows(TokenInvalidoException.class, () -> servicio.validar("a.b.c"));
    }

    @Test
    void unSecretoCortoNoSeAcepta() {
        assertThrows(IllegalArgumentException.class, () -> new ServicioJwt(
                "corto".getBytes(StandardCharsets.UTF_8), Duration.ofMinutes(5), Clock.systemUTC()));
    }

    @Test
    void sinSecretoConfiguradoSeGeneraUnoAleatorio() {
        byte[] uno = ServicioJwt.resolverSecreto(null);
        byte[] otro = ServicioJwt.resolverSecreto("  ");

        assertEquals(ServicioJwt.BYTES_SECRETO_ALEATORIO, uno.length);
        assertFalse(Arrays.equals(uno, otro),
                "dos arranques no comparten secreto: no hay uno fijo en el codigo");
    }

    @Test
    void elSecretoConfiguradoSeUsaTalCual() {
        String configurado = "un-secreto-de-al-menos-treinta-y-dos-bytes!!";
        assertArrayEquals(configurado.getBytes(StandardCharsets.UTF_8),
                ServicioJwt.resolverSecreto(configurado));
    }

    @Test
    void dosSecretosAleatoriosNoSeAceptanEntreSi() {
        ServicioJwt emisor = new ServicioJwt(ServicioJwt.resolverSecreto(null), Duration.ofMinutes(5), Clock.systemUTC());
        ServicioJwt otro = new ServicioJwt(ServicioJwt.resolverSecreto(null), Duration.ofMinutes(5), Clock.systemUTC());

        String token = emisor.emitir("admin", List.of("ADMIN"));
        assertThrows(TokenInvalidoException.class, () -> otro.validar(token));
    }
}
