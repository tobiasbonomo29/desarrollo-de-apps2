package com.mediconecta.modelo;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Turno agendado: un paciente, un profesional y un momento.
 *
 * <p>fechaHora es ahora un {@link LocalDateTime} real. En la Entrega 1 era texto
 * para evitar sorpresas de serializacion; con JPA pasa a ser una columna
 * timestamp, tal como estaba previsto en el documento de arquitectura. La salida
 * JSON no cambio: JSON-B serializa LocalDateTime en ISO-8601
 * (2026-09-18T09:30), que es exactamente el texto que se devolvia antes.</p>
 *
 * <p>Las relaciones son unidireccionales (el turno conoce al paciente y al
 * profesional, no al reves), lo que mantiene el JSON libre de ciclos.</p>
 */
@Entity
@Table(name = "turnos")
public class Turno {

    /** Formato canonico usado para mostrar y comparar horarios. */
    public static final DateTimeFormatter FORMATO_FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Version para el control de concurrencia optimista (JPA {@code @Version}):
     * cada modificacion la incrementa y viaja al cliente en el ETag. No forma
     * parte del JSON.
     */
    @Version
    @JsonbTransient
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "profesional_id", nullable = false)
    private Profesional profesional;

    @Column(nullable = false, length = 80)
    private String especialidad;

    /**
     * El formato explicito mantiene la salida JSON identica a la de la primera
     * entrega ({@code 2026-09-18T09:30}). Sin el, JSON-B agrega los segundos.
     */
    @JsonbDateFormat("yyyy-MM-dd'T'HH:mm")
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTurno estado;

    @Column(length = 200)
    private String motivo;

    /**
     * Si el turno se sacó de la agenda operativa.
     *
     * <p>Es un archivado lógico, no un borrado: el registro sigue en la base y
     * sigue apareciendo en el historial del paciente. Solo un turno
     * {@code CANCELADO} puede archivarse; al hacerlo deja de listarse en la
     * agenda general ({@code RepositorioTurnos.listarAgenda}).</p>
     */
    @Column(nullable = false)
    private boolean archivado = false;

    public Turno() {
    }

    public long getVersion() {
        return version;
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

    public Profesional getProfesional() {
        return profesional;
    }

    public void setProfesional(Profesional profesional) {
        this.profesional = profesional;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public void setEstado(EstadoTurno estado) {
        this.estado = estado;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public boolean isArchivado() {
        return archivado;
    }

    public void setArchivado(boolean archivado) {
        this.archivado = archivado;
    }

    // ------------------------------------------------------------------
    // Patron State: el turno es el "contexto" y delega cada transicion en el
    // objeto de su estado actual. Si la transicion no corresponde, el estado
    // lanza TransicionInvalidaException (409 en la API).
    // ------------------------------------------------------------------

    /** PROGRAMADO -> CANCELADO. */
    public void cancelar() {
        estado.comportamiento().cancelar(this);
    }

    /** PROGRAMADO -> ATENDIDO. */
    public void atender() {
        estado.comportamiento().atender(this);
    }

    /** Saca de la agenda un turno CANCELADO sin borrarlo. */
    public void archivar() {
        estado.comportamiento().archivar(this);
    }

    /** Si el turno bloquea el horario del profesional (los cancelados no). */
    public boolean ocupaHorario() {
        return estado.comportamiento().ocupaHorario();
    }
}
