package com.mediconecta.modelo;

import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Un aviso que genero un consumidor JMS (confirmacion, recordatorio o aviso al
 * profesional) a partir del evento "turno confirmado".
 *
 * <p>Es lo que deja a la vista que los consumidores hacen trabajo real y no
 * solo escriben en el log. En un sistema productivo, otro proceso tomaria
 * estas filas y las mandaria por mail, SMS o al calendario del profesional.</p>
 *
 * <p><b>Idempotencia.</b> La restriccion unica {@code (evento_id, tipo)} es la
 * que garantiza que un mismo evento entregado dos veces no genere dos avisos:
 * el consumidor consulta antes de insertar y, si dos entregas llegaran a la
 * vez, la base rechaza la segunda.</p>
 *
 * <p>Guarda el id del turno y no una relacion {@code @ManyToOne}: asi se
 * serializa a JSON sin arrastrar al turno entero y la relacion sigue siendo
 * unidireccional, como en el resto del modelo.</p>
 */
@Entity
@Table(name = "notificaciones",
        uniqueConstraints = @UniqueConstraint(name = "uk_notificaciones_evento_tipo",
                columnNames = {"evento_id", "tipo"}))
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, length = 60)
    private String eventoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacion tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalNotificacion canal;

    @Column(name = "turno_id", nullable = false)
    private Long turnoId;

    @Column(nullable = false, length = 120)
    private String destinatario;

    @Column(nullable = false, length = 500)
    private String mensaje;

    @JsonbDateFormat("yyyy-MM-dd'T'HH:mm")
    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @JsonbDateFormat("yyyy-MM-dd'T'HH:mm")
    @Column(name = "programada_para")
    private LocalDateTime programadaPara;

    public Notificacion() {
    }

    public Notificacion(String eventoId, TipoNotificacion tipo, CanalNotificacion canal, Long turnoId,
                        String destinatario, String mensaje, LocalDateTime creadaEn) {
        this.eventoId = eventoId;
        this.tipo = tipo;
        this.canal = canal;
        this.turnoId = turnoId;
        this.destinatario = destinatario;
        this.mensaje = mensaje;
        this.creadaEn = creadaEn;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventoId() {
        return eventoId;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public CanalNotificacion getCanal() {
        return canal;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getMensaje() {
        return mensaje;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public LocalDateTime getProgramadaPara() {
        return programadaPara;
    }

    public void setProgramadaPara(LocalDateTime programadaPara) {
        this.programadaPara = programadaPara;
    }
}
