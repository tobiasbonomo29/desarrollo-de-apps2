package com.mediconecta.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Emite y valida los JWT de MediConecta (firma HMAC-SHA256, "HS256").
 *
 * <p>Esta implementado con la JDK y JSON-P (que aporta WildFly), sin librerias
 * externas: el WAR sigue teniendo una unica dependencia {@code provided}. Un JWT
 * son tres partes en Base64URL separadas por puntos:
 * {@code cabecera.contenido.firma}. La firma es HMAC-SHA256 de
 * {@code cabecera.contenido} con un secreto que solo conoce el servidor: si
 * alguien cambia un rol dentro del token, la firma deja de coincidir.</p>
 *
 * <p><b>Secreto.</b> Se toma, en este orden, de la propiedad del sistema
 * {@code mediconecta.jwt.secreto} o de la variable de entorno
 * {@code MEDICONECTA_JWT_SECRETO}, y tiene que tener al menos 32 bytes. En el
 * codigo <b>no hay ningun secreto por defecto</b>: si no se configuro, se
 * genera uno aleatorio al arrancar (con {@link SecureRandom}) y se avisa en el
 * log. Asi nadie puede fabricar un token conociendo el repositorio; el costo es
 * que los tokens dejan de valer al reiniciar el servidor.</p>
 *
 * <p><b>Validez.</b> {@code mediconecta.jwt.minutos} (120 por omision).</p>
 */
@ApplicationScoped
public class ServicioJwt {

    private static final Logger LOG = Logger.getLogger(ServicioJwt.class.getName());

    static final String EMISOR = "mediconecta";

    private static final String ALGORITMO_JWT = "HS256";

    private static final String ALGORITMO_MAC = "HmacSHA256";

    /** Largo del secreto aleatorio que se genera si no se configuro ninguno. */
    static final int BYTES_SECRETO_ALEATORIO = 32;

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

    private final byte[] secreto;

    private final Duration validez;

    private final Clock reloj;

    /** Constructor que usa el contenedor: lee la configuracion del entorno. */
    public ServicioJwt() {
        this(secretoConfigurado(), validezConfigurada(), Clock.systemUTC());
    }

    /** Constructor explicito, pensado para los tests unitarios. */
    public ServicioJwt(byte[] secreto, Duration validez, Clock reloj) {
        if (secreto == null || secreto.length < 32) {
            throw new IllegalArgumentException("El secreto del JWT debe tener al menos 32 bytes.");
        }
        this.secreto = secreto.clone();
        this.validez = validez;
        this.reloj = reloj;
    }

    /** Duracion de un token recien emitido. */
    public Duration validez() {
        return validez;
    }

    /** Emite un token para el usuario con sus roles. */
    public String emitir(String usuario, Collection<String> roles) {
        Instant ahora = reloj.instant();

        JsonArrayBuilder grupos = Json.createArrayBuilder();
        roles.forEach(grupos::add);

        JsonObject cabecera = Json.createObjectBuilder()
                .add("alg", ALGORITMO_JWT)
                .add("typ", "JWT")
                .build();

        JsonObject contenido = Json.createObjectBuilder()
                .add("iss", EMISOR)
                .add("sub", usuario)
                .add("upn", usuario)
                .add("groups", grupos)
                .add("iat", ahora.getEpochSecond())
                .add("exp", ahora.plus(validez).getEpochSecond())
                .add("jti", UUID.randomUUID().toString())
                .build();

        String firmable = codificar(cabecera.toString()) + "." + codificar(contenido.toString());
        return firmable + "." + B64.encodeToString(firmar(firmable));
    }

    /**
     * Valida firma, algoritmo, emisor y vencimiento.
     *
     * @throws TokenInvalidoException si algo no cierra
     */
    public ClaimsJwt validar(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException("Falta el token.");
        }
        String[] partes = token.split("\\.", -1);
        if (partes.length != 3) {
            throw new TokenInvalidoException("El token no tiene el formato cabecera.contenido.firma.");
        }

        JsonObject cabecera = leerJson(partes[0]);
        // Se rechaza cualquier algoritmo que no sea el esperado: es la defensa
        // contra el ataque clasico de mandar "alg":"none" sin firma.
        if (!ALGORITMO_JWT.equals(cabecera.getString("alg", ""))) {
            throw new TokenInvalidoException("Algoritmo de firma no admitido.");
        }

        byte[] firmaRecibida;
        try {
            firmaRecibida = B64_DEC.decode(partes[2]);
        } catch (IllegalArgumentException e) {
            throw new TokenInvalidoException("La firma del token no es valida.");
        }
        byte[] firmaEsperada = firmar(partes[0] + "." + partes[1]);
        if (!MessageDigest.isEqual(firmaEsperada, firmaRecibida)) {
            throw new TokenInvalidoException("La firma del token no es valida.");
        }

        JsonObject contenido = leerJson(partes[1]);
        if (!EMISOR.equals(contenido.getString("iss", ""))) {
            throw new TokenInvalidoException("El token no fue emitido por MediConecta.");
        }
        String usuario = contenido.getString("sub", "");
        if (usuario.isBlank()) {
            throw new TokenInvalidoException("El token no identifica al usuario.");
        }
        if (!contenido.containsKey("exp")
                || contenido.get("exp").getValueType() != JsonValue.ValueType.NUMBER) {
            throw new TokenInvalidoException("El token no tiene vencimiento.");
        }
        Instant expira = Instant.ofEpochSecond(contenido.getJsonNumber("exp").longValue());
        if (!reloj.instant().isBefore(expira)) {
            throw new TokenInvalidoException("El token vencio. Vuelva a iniciar sesion.");
        }

        Set<String> roles = new LinkedHashSet<>();
        if (contenido.containsKey("groups")
                && contenido.get("groups").getValueType() == JsonValue.ValueType.ARRAY) {
            for (JsonValue valor : contenido.getJsonArray("groups")) {
                if (valor instanceof JsonString texto) {
                    roles.add(texto.getString());
                }
            }
        }
        return new ClaimsJwt(usuario, Set.copyOf(roles), expira);
    }

    // ------------------------------------------------------------------

    private static String codificar(String json) {
        return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject leerJson(String parteBase64) {
        try (JsonReader lector = Json.createReader(new StringReader(
                new String(B64_DEC.decode(parteBase64), StandardCharsets.UTF_8)))) {
            return lector.readObject();
        } catch (IllegalArgumentException | JsonException | ClassCastException e) {
            throw new TokenInvalidoException("El token esta mal formado.");
        }
    }

    private byte[] firmar(String datos) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO_MAC);
            mac.init(new SecretKeySpec(secreto, ALGORITMO_MAC));
            return mac.doFinal(datos.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    private static byte[] secretoConfigurado() {
        String valor = System.getProperty("mediconecta.jwt.secreto");
        if (valor == null || valor.isBlank()) {
            valor = System.getenv("MEDICONECTA_JWT_SECRETO");
        }
        return resolverSecreto(valor);
    }

    /**
     * Devuelve el secreto configurado o, si no hay ninguno, uno aleatorio.
     * Separado de la lectura del entorno para poder probarlo.
     */
    static byte[] resolverSecreto(String configurado) {
        if (configurado == null || configurado.isBlank()) {
            LOG.warning("[JWT] No se configuro mediconecta.jwt.secreto / MEDICONECTA_JWT_SECRETO: se genero "
                    + "un secreto aleatorio para esta ejecucion. Los tokens emitidos dejan de valer al "
                    + "reiniciar el servidor.");
            byte[] aleatorio = new byte[BYTES_SECRETO_ALEATORIO];
            new SecureRandom().nextBytes(aleatorio);
            return aleatorio;
        }
        return configurado.getBytes(StandardCharsets.UTF_8);
    }

    private static Duration validezConfigurada() {
        String valor = System.getProperty("mediconecta.jwt.minutos", "120");
        try {
            return Duration.ofMinutes(Long.parseLong(valor.trim()));
        } catch (NumberFormatException e) {
            return Duration.ofMinutes(120);
        }
    }
}
