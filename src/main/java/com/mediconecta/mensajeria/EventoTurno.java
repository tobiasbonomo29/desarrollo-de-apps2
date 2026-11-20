package com.mediconecta.mensajeria;

import com.mediconecta.modelo.Turno;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Contrato del mensaje "turno confirmado" que viaja por JMS.
 *
 * <p>Hasta la Entrega 2 el mensaje era un texto armado a mano
 * ({@code "CONFIRMACION|Turno 5: Ana Gomez con ..."}): para leerlo habia que
 * partir el string y cualquier cambio de redaccion rompia a los consumidores.
 * Ahora es un JSON con forma fija y <b>version</b>:</p>
 *
 * <pre>
 * {
 *   "eventoId": "0b6c0f0e-...",      identificador unico: base de la idempotencia
 *   "tipo": "TURNO_CONFIRMADO",
 *   "version": 1,
 *   "ocurridoEn": "2026-11-19T13:05:00Z",
 *   "turnoId": 42,
 *   "pacienteId": 7,
 *   "profesionalId": 3,
 *   "especialidad": "Cardiologia",
 *   "fechaHora": "2026-11-24T09:30"
 * }
 * </pre>
 *
 * <p>Lleva identificadores y no datos personales (ni nombre, ni documento, ni
 * mail): el consumidor que los necesita los busca en la base. Asi el broker y
 * sus logs no guardan informacion sensible de pacientes.</p>
 *
 * <p>Los mismos datos clave van tambien como propiedades JMS ({@code eventoId},
 * {@code tipoEvento}, {@code versionEvento}, {@code turnoId}) para poder
 * filtrar con selectores sin abrir el cuerpo.</p>
 */
public final class EventoTurno {

    public static final String TIPO_TURNO_CONFIRMADO = "TURNO_CONFIRMADO";

    /** Version del contrato que produce y entiende esta clase. */
    public static final int VERSION = 1;

    private final String eventoId;

    private final String tipo;

    private final int version;

    private final Instant ocurridoEn;

    private final long turnoId;

    private final long pacienteId;

    private final long profesionalId;

    private final String especialidad;

    private final LocalDateTime fechaHora;

    EventoTurno(String eventoId, String tipo, int version, Instant ocurridoEn, long turnoId, long pacienteId,
                long profesionalId, String especialidad, LocalDateTime fechaHora) {
        this.eventoId = Objects.requireNonNull(eventoId);
        this.tipo = Objects.requireNonNull(tipo);
        this.version = version;
        this.ocurridoEn = Objects.requireNonNull(ocurridoEn);
        this.turnoId = turnoId;
        this.pacienteId = pacienteId;
        this.profesionalId = profesionalId;
        this.especialidad = especialidad;
        this.fechaHora = Objects.requireNonNull(fechaHora);
    }

    /** Evento nuevo, con un {@code eventoId} recien generado. */
    public static EventoTurno turnoConfirmado(Turno turno, Clock reloj) {
        return new EventoTurno(UUID.randomUUID().toString(), TIPO_TURNO_CONFIRMADO, VERSION,
                reloj.instant().truncatedTo(ChronoUnit.SECONDS),
                turno.getId(), turno.getPaciente().getId(), turno.getProfesional().getId(),
                turno.getEspecialidad(), turno.getFechaHora());
    }

    public String eventoId() {
        return eventoId;
    }

    public String tipo() {
        return tipo;
    }

    public int version() {
        return version;
    }

    public Instant ocurridoEn() {
        return ocurridoEn;
    }

    public long turnoId() {
        return turnoId;
    }

    public long pacienteId() {
        return pacienteId;
    }

    public long profesionalId() {
        return profesionalId;
    }

    public String especialidad() {
        return especialidad;
    }

    public LocalDateTime fechaHora() {
        return fechaHora;
    }

    public String aJson() {
        return Json.createObjectBuilder()
                .add("eventoId", eventoId)
                .add("tipo", tipo)
                .add("version", version)
                .add("ocurridoEn", ocurridoEn.toString())
                .add("turnoId", turnoId)
                .add("pacienteId", pacienteId)
                .add("profesionalId", profesionalId)
                .add("especialidad", especialidad == null ? "" : especialidad)
                .add("fechaHora", fechaHora.format(Turno.FORMATO_FECHA_HORA))
                .build()
                .toString();
    }

    /** Lee el cuerpo de un mensaje JMS. */
    public static EventoTurno desdeMensaje(Message mensaje) {
        if (!(mensaje instanceof TextMessage texto)) {
            throw new MensajeInvalidoException("Se esperaba un TextMessage con JSON y llego "
                    + (mensaje == null ? "null" : mensaje.getClass().getSimpleName()) + ".");
        }
        try {
            return desdeJson(texto.getText());
        } catch (JMSException e) {
            throw new MensajeInvalidoException("No se pudo leer el cuerpo del mensaje.", e);
        }
    }

    /**
     * Valida y convierte el JSON. Rechaza versiones mayores a la conocida: un
     * productor nuevo no puede romper en silencio a un consumidor viejo.
     */
    public static EventoTurno desdeJson(String json) {
        if (json == null || json.isBlank()) {
            throw new MensajeInvalidoException("El mensaje llego vacio.");
        }
        JsonObject objeto;
        try (JsonReader lector = Json.createReader(new StringReader(json))) {
            objeto = lector.readObject();
        } catch (JsonException | IllegalStateException e) {
            throw new MensajeInvalidoException("El mensaje no es un objeto JSON valido.", e);
        }

        String tipo = texto(objeto, "tipo");
        if (!TIPO_TURNO_CONFIRMADO.equals(tipo)) {
            throw new MensajeInvalidoException("Tipo de evento desconocido: " + tipo + ".");
        }
        int version = (int) numero(objeto, "version");
        if (version < 1 || version > VERSION) {
            throw new MensajeInvalidoException("Version de evento no soportada: " + version
                    + " (este consumidor entiende hasta la " + VERSION + ").");
        }
        try {
            return new EventoTurno(
                    texto(objeto, "eventoId"),
                    tipo,
                    version,
                    Instant.parse(texto(objeto, "ocurridoEn")),
                    numero(objeto, "turnoId"),
                    numero(objeto, "pacienteId"),
                    numero(objeto, "profesionalId"),
                    objeto.getString("especialidad", null),
                    LocalDateTime.parse(texto(objeto, "fechaHora"), Turno.FORMATO_FECHA_HORA));
        } catch (DateTimeParseException e) {
            throw new MensajeInvalidoException("El mensaje tiene una fecha con formato invalido.", e);
        }
    }

    private static String texto(JsonObject objeto, String campo) {
        JsonValue valor = objeto.get(campo);
        if (!(valor instanceof JsonString cadena) || cadena.getString().isBlank()) {
            throw new MensajeInvalidoException("Falta el campo de texto \"" + campo + "\".");
        }
        return cadena.getString();
    }

    private static long numero(JsonObject objeto, String campo) {
        JsonValue valor = objeto.get(campo);
        if (!(valor instanceof JsonNumber numero)) {
            throw new MensajeInvalidoException("Falta el campo numerico \"" + campo + "\".");
        }
        return numero.longValue();
    }
}
