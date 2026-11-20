package com.mediconecta.presentacion;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El contrato {@code openapi.json} coincide con los recursos JAX-RS reales.
 *
 * <p>Recorre por reflexion todas las clases {@code Recurso*} de este paquete,
 * arma la lista "METODO /ruta" de lo que el codigo expone y la compara con lo
 * que documenta el contrato. Si alguien agrega, cambia o borra un endpoint sin
 * actualizar la documentacion, el build falla.</p>
 */
class ContratoOpenApiTest {

    private static final String CONTRATO = "src/main/webapp/openapi.json";

    private static JsonObject contrato;

    @BeforeAll
    static void leerContrato() throws IOException {
        try (JsonReader lector = Json.createReader(Files.newBufferedReader(Paths.get(CONTRATO)))) {
            contrato = lector.readObject();
        }
    }

    private static Set<String> endpointsDelCodigo() throws IOException, URISyntaxException, ClassNotFoundException {
        var carpeta = Paths.get(RecursoTurnos.class.getResource("RecursoTurnos.class").toURI()).getParent();
        Set<String> endpoints = new TreeSet<>();
        try (Stream<java.nio.file.Path> archivos = Files.list(carpeta)) {
            for (String nombre : archivos.map(a -> a.getFileName().toString())
                    .filter(n -> n.matches("Recurso\\w+\\.class")).toList()) {
                Class<?> clase = Class.forName(RecursoTurnos.class.getPackageName() + "." + nombre.replace(".class", ""));
                Path base = clase.getAnnotation(Path.class);
                if (base == null) {
                    continue;
                }
                for (Method metodo : clase.getDeclaredMethods()) {
                    String verbo = verbo(metodo);
                    if (verbo == null) {
                        continue;
                    }
                    Path sub = metodo.getAnnotation(Path.class);
                    endpoints.add(verbo + " " + normalizar(base.value() + (sub == null ? "" : "/" + sub.value())));
                }
            }
        }
        return endpoints;
    }

    private static String verbo(Method metodo) {
        Map<Class<? extends Annotation>, String> verbos = Map.of(GET.class, "GET", POST.class, "POST",
                PUT.class, "PUT", DELETE.class, "DELETE");
        for (var entrada : verbos.entrySet()) {
            if (metodo.isAnnotationPresent(entrada.getKey())) {
                return entrada.getValue();
            }
        }
        return null;
    }

    private static String normalizar(String ruta) {
        String limpia = ("/" + ruta).replaceAll("/+", "/");
        return limpia.length() > 1 && limpia.endsWith("/") ? limpia.substring(0, limpia.length() - 1) : limpia;
    }

    private static Set<String> endpointsDelContrato() {
        Set<String> endpoints = new TreeSet<>();
        contrato.getJsonObject("paths").forEach((ruta, operaciones) ->
                operaciones.asJsonObject().keySet().forEach(verbo ->
                        endpoints.add(verbo.toUpperCase() + " " + ruta)));
        return endpoints;
    }

    @Test
    void todoLoQueExponeElCodigoEstaDocumentadoYViceversa() throws Exception {
        Set<String> codigo = endpointsDelCodigo();
        Set<String> documentados = endpointsDelContrato();

        Set<String> sinDocumentar = new TreeSet<>(codigo);
        sinDocumentar.removeAll(documentados);
        Set<String> inexistentes = new TreeSet<>(documentados);
        inexistentes.removeAll(codigo);

        assertTrue(sinDocumentar.isEmpty(), "Endpoints sin documentar en openapi.json: " + sinDocumentar);
        assertTrue(inexistentes.isEmpty(), "openapi.json documenta endpoints que no existen: " + inexistentes);
        assertTrue(codigo.size() > 30, "se encontraron " + codigo.size() + " endpoints");
    }

    @Test
    void esOpenApi31ConSeguridadJwt() {
        assertTrue(contrato.getString("openapi").startsWith("3.1"));
        JsonObject esquema = contrato.getJsonObject("components").getJsonObject("securitySchemes")
                .getJsonObject("bearerAuth");
        assertEquals("bearer", esquema.getString("scheme"));
        assertEquals("JWT", esquema.getString("bearerFormat"));
    }

    @Test
    void todasLasOperacionesDocumentanSusErroresComoProblemDetails() {
        contrato.getJsonObject("paths").forEach((ruta, operaciones) ->
                operaciones.asJsonObject().forEach((verbo, valor) -> {
                    JsonObject respuestas = valor.asJsonObject().getJsonObject("responses");
                    assertTrue(respuestas.containsKey("500"), verbo + " " + ruta + " sin 500");
                    respuestas.forEach((codigo, respuesta) -> {
                        if (codigo.startsWith("4") || codigo.startsWith("5")) {
                            String referencia = respuesta.asJsonObject().getString("$ref", "");
                            boolean esProblema = referencia.startsWith("#/components/responses/Problema")
                                    || respuesta.asJsonObject().getJsonObject("content") != null;
                            assertTrue(esProblema, verbo + " " + ruta + " " + codigo);
                        }
                    });
                }));
        JsonObject problema400 = contrato.getJsonObject("components").getJsonObject("responses")
                .getJsonObject("Problema400").getJsonObject("content");
        assertTrue(problema400.containsKey("application/problem+json"));
    }

    @Test
    void lasOperacionesProtegidasDeclaranSusRoles() {
        contrato.getJsonObject("paths").forEach((ruta, operaciones) ->
                operaciones.asJsonObject().forEach((verbo, valor) -> {
                    JsonObject operacion = valor.asJsonObject();
                    boolean publica = operacion.containsKey("security")
                            && operacion.getJsonArray("security").isEmpty();
                    if (!publica) {
                        assertTrue(operacion.containsKey("x-roles"), verbo + " " + ruta + " sin x-roles");
                        assertFalse(operacion.getJsonArray("x-roles").isEmpty());
                    }
                }));
    }

    @Test
    void elAltaDeTurnosDocumentaIdempotencyKey() {
        JsonObject alta = contrato.getJsonObject("paths").getJsonObject("/turnos").getJsonObject("post");
        boolean conClave = alta.getJsonArray("parameters").stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(p -> p.getString("$ref", "").endsWith("/IdempotencyKey"));
        assertTrue(conClave);
        assertTrue(alta.getJsonObject("responses").containsKey("422"));
    }
}
