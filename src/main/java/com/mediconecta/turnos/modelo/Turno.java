package com.mediconecta.turnos.modelo;

import java.time.format.DateTimeFormatter;

/**
 * Turno medico: la entidad central del componente de esta entrega.
 *
 * <p>Al igual que {@link Paciente}, es un objeto de datos puro. No valida nada
 * por su cuenta: las reglas de negocio (fecha futura, profesional libre, etc.)
 * viven en la capa de negocio, que es la unica responsable de hacerlas cumplir.</p>
 *
 * <p><b>Sobre {@code fechaHora}:</b> se guarda como texto en formato ISO-8601
 * ({@code "2026-09-10T09:00"}) y no como {@code LocalDateTime}. El motivo es
 * practico: distintos proveedores JSON serializan {@code LocalDateTime} de
 * forma diferente (algunos como un arreglo {@code [2026,9,10,9,0]}), lo que
 * haria fragil la demo en el navegador. La capa de negocio igual lo interpreta
 * como fecha real con {@code LocalDateTime.parse} para poder validarlo.
 * Cuando en la Entrega 2 se migre a JPA, este campo pasa a ser una columna
 * {@code LocalDateTime} y el cambio queda contenido en el modelo y la capa de
 * datos.</p>
 */
public class Turno {

    /**
     * Formato canonico de {@code fechaHora}: {@code aaaa-MM-ddTHH:mm}, sin segundos.
     *
     * <p>Existe porque el mismo instante se puede escribir de varias formas
     * ({@code "2026-09-03T09:30"} y {@code "2026-09-03T09:30:00"} son la misma
     * hora pero son textos distintos). Como la regla que detecta horarios
     * ocupados compara textos, todo turno se guarda normalizado a este formato:
     * de lo contrario dos turnos en el mismo instante no se reconocerian como
     * superpuestos y se podria sobrevender la agenda de un profesional.</p>
     */
    public static final DateTimeFormatter FORMATO_FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /** Identificador unico. Lo asigna la capa de datos al guardar, no el cliente. */
    private Long id;

    /** Paciente que solicito el turno. */
    private Paciente paciente;

    /** Nombre del profesional que atiende. */
    private String profesional;

    /** Especialidad medica del turno (por ejemplo: Cardiologia). */
    private String especialidad;

    /** Fecha y hora del turno en formato ISO-8601: {@code aaaa-MM-ddTHH:mm}. */
    private String fechaHora;

    /** Situacion actual del turno. */
    private EstadoTurno estado;

    public Turno() {
        // Requerido por el proveedor JSON (JSON-B / Jackson).
    }

    public Turno(Paciente paciente, String profesional, String especialidad, String fechaHora) {
        this.paciente = paciente;
        this.profesional = profesional;
        this.especialidad = especialidad;
        this.fechaHora = fechaHora;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public void setPaciente(Paciente paciente) {
        this.paciente = paciente;
    }

    public String getProfesional() {
        return profesional;
    }

    public void setProfesional(String profesional) {
        this.profesional = profesional;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }

    public String getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(String fechaHora) {
        this.fechaHora = fechaHora;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public void setEstado(EstadoTurno estado) {
        this.estado = estado;
    }
}
