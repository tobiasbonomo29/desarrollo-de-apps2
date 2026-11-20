package com.mediconecta.integraciones;

import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.StringReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Adapter que habla con un modelo de lenguaje por la API de Groq (compatible
 * con el formato "chat completions" de OpenAI).
 *
 * <p>Configuracion, en el servidor y nunca en el codigo versionado:</p>
 * <ul>
 *   <li>clave: propiedad {@code mediconecta.ia.clave} o variable de entorno
 *       {@code MEDICONECTA_IA_CLAVE} (obligatoria);</li>
 *   <li>modelo: {@code mediconecta.ia.modelo} (por omision
 *       {@value #MODELO_POR_DEFECTO});</li>
 *   <li>URL: {@code mediconecta.ia.url} (por omision la de Groq).</li>
 * </ul>
 *
 * <p>La clave solo vive en el servidor: el navegador habla con MediConecta y
 * MediConecta habla con Groq, igual que con los sistemas de obras sociales.</p>
 */
// NOT_SUPPORTED: una llamada remota no tiene que retener una transaccion, y asi
// una falla del sistema externo no marca para rollback la del llamador (que
// necesita seguir viva para responder con el fallback).
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AdaptadorIaGroq implements OrientadorEspecialidad {

    private static final Logger LOG = Logger.getLogger(AdaptadorIaGroq.class.getName());

    static final String URL_POR_DEFECTO = "https://api.groq.com/openai/v1/chat/completions";

    static final String MODELO_POR_DEFECTO = "openai/gpt-oss-120b";

    @Override
    public SugerenciaEspecialidad orientar(List<MensajeChat> conversacion, List<String> especialidades) {
        String clave = configuracion("mediconecta.ia.clave", "MEDICONECTA_IA_CLAVE", null);
        if (clave == null) {
            LOG.warning("[IA] No se configuro mediconecta.ia.clave / MEDICONECTA_IA_CLAVE.");
            throw new IntegracionExternaException(
                    "El asistente de IA no esta configurado en el servidor. Elegi la especialidad de la lista.");
        }

        String modelo = configuracion("mediconecta.ia.modelo", "MEDICONECTA_IA_MODELO", MODELO_POR_DEFECTO);
        String url = configuracion("mediconecta.ia.url", "MEDICONECTA_IA_URL", URL_POR_DEFECTO);
        String cuerpo = armarPedido(modelo, conversacion, especialidades).toString();

        Client cliente = ClientBuilder.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        try (Response respuesta = cliente.target(url)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + clave)
                .post(Entity.entity(cuerpo, MediaType.APPLICATION_JSON))) {

            String texto = respuesta.readEntity(String.class);
            if (respuesta.getStatus() != Response.Status.OK.getStatusCode()) {
                LOG.warning("[IA] El proveedor respondio " + respuesta.getStatus() + ": " + recortar(texto));
                throw new IntegracionExternaException(
                        "El asistente de IA no esta disponible en este momento. Elegi la especialidad de la lista.");
            }
            return interpretarRespuesta(texto);
        } catch (ProcessingException error) {
            throw new IntegracionExternaException(
                    "El asistente de IA no respondio. Elegi la especialidad de la lista.", error);
        } finally {
            cliente.close();
        }
    }

    /** Instrucciones de sistema: que puede y que no puede hacer el asistente. */
    static String instrucciones(List<String> especialidades) {
        return """
                Sos el asistente de orientación de MediConecta, un sistema de turnos médicos de Argentina.
                Tu única tarea es ayudar a la persona a elegir con qué especialidad sacar un turno.

                Especialidades disponibles (usá el nombre exactamente como aparece acá):
                %s

                Reglas:
                - No diagnostiques, no nombres enfermedades probables y no indiques tratamientos ni medicamentos.
                - Si con lo que te contaron no alcanza para elegir, hacé una sola pregunta corta y concreta, y dejá "especialidad" en null.
                - Si ninguna especialidad de la lista encaja, sugerí Clinica Medica si está en la lista; \
                si no está, explicá que no hay una adecuada y dejá "especialidad" en null.
                - Si aparece algún signo de emergencia (dolor fuerte en el pecho, falta de aire, desmayo, \
                convulsiones, sangrado abundante, signos de ACV como boca torcida o debilidad de un lado del \
                cuerpo, ideas de hacerse daño, o un bebé con fiebre muy alta o muy decaído), poné "urgente" \
                en true y decí que no espere un turno: que llame al 107 (SAME) o al 911, o que vaya a la \
                guardia más cercana.
                - Si te piden algo que no tiene que ver con elegir una especialidad, explicá con amabilidad que solo podés ayudar con eso.
                - Hablá en español rioplatense, con calidez, en no más de tres oraciones.

                Respondé únicamente con un objeto JSON con esta forma:
                {"mensaje": "texto para la persona", "especialidad": "una de la lista, o null", "urgente": false}
                """.formatted("- " + String.join("\n- ", especialidades));
    }

    /** Cuerpo del pedido en el formato "chat completions". */
    static JsonObject armarPedido(String modelo, List<MensajeChat> conversacion, List<String> especialidades) {
        JsonArrayBuilder mensajes = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("role", "system")
                        .add("content", instrucciones(especialidades)));

        for (MensajeChat mensaje : conversacion) {
            mensajes.add(Json.createObjectBuilder()
                    .add("role", MensajeChat.USUARIO.equals(mensaje.getRol()) ? "user" : "assistant")
                    .add("content", mensaje.getTexto()));
        }

        JsonObjectBuilder pedido = Json.createObjectBuilder()
                .add("model", modelo)
                .add("temperature", 0.2)
                .add("max_completion_tokens", 1024)
                .add("response_format", Json.createObjectBuilder().add("type", "json_object"))
                .add("messages", mensajes);

        // Los modelos gpt-oss razonan antes de responder; para elegir una
        // especialidad alcanza con poco razonamiento y la respuesta llega antes.
        if (modelo.startsWith("openai/gpt-oss")) {
            pedido.add("reasoning_effort", "low");
        }
        return pedido.build();
    }

    /**
     * Saca la sugerencia de la respuesta del proveedor. Si el modelo no
     * respeto el formato JSON, su texto se muestra igual, sin especialidad.
     */
    static SugerenciaEspecialidad interpretarRespuesta(String cuerpo) {
        String contenido;
        try (JsonReader lector = Json.createReader(new StringReader(cuerpo))) {
            JsonArray opciones = lector.readObject().getJsonArray("choices");
            contenido = opciones.getJsonObject(0).getJsonObject("message").getString("content", "");
        } catch (JsonException | ClassCastException | NullPointerException | IndexOutOfBoundsException error) {
            throw new IntegracionExternaException("El asistente de IA devolvio una respuesta que no se pudo leer.", error);
        }

        SugerenciaEspecialidad sugerencia;
        try (JsonReader lector = Json.createReader(new StringReader(contenido))) {
            JsonObject json = lector.readObject();
            sugerencia = new SugerenciaEspecialidad(
                    json.getString("mensaje", "").trim(),
                    textoONulo(json.get("especialidad")),
                    json.getBoolean("urgente", false));
        } catch (JsonException | ClassCastException error) {
            sugerencia = new SugerenciaEspecialidad(contenido.trim(), null, false);
        }

        if (sugerencia.getMensaje().isEmpty()) {
            throw new IntegracionExternaException("El asistente de IA devolvio una respuesta vacia.");
        }
        return sugerencia;
    }

    private static String textoONulo(JsonValue valor) {
        if (valor == null || valor.getValueType() != JsonValue.ValueType.STRING) {
            return null;
        }
        String texto = ((JsonString) valor).getString().trim();
        return texto.isEmpty() || texto.equalsIgnoreCase("null") ? null : texto;
    }

    private static String configuracion(String propiedad, String variableDeEntorno, String porDefecto) {
        String valor = System.getProperty(propiedad);
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(variableDeEntorno);
        }
        return valor == null || valor.isBlank() ? porDefecto : valor.trim();
    }

    private static String recortar(String texto) {
        return texto == null || texto.length() <= 300 ? texto : texto.substring(0, 300) + "...";
    }
}
