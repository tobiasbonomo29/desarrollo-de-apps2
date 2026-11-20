package com.mediconecta.modelo;

import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Un mensaje JMS que ningun consumidor pudo procesar despues de todos los
 * reintentos y termino en la cola de mensajes muertos (DLQ).
 *
 * <p>Queda registrado para que un administrador lo vea
 * ({@code GET /api/mensajeria/mensajes-fallidos}) en lugar de perderse en el
 * log. {@code mensajeId} es unico: si el monitor de la DLQ recibiera dos veces
 * el mismo mensaje, se registra una sola vez.</p>
 */
@Entity
@Table(name = "mensajes_fallidos")
public class MensajeFallido {

    /** Largo maximo del cuerpo que se guarda; el resto se descarta. */
    public static final int LARGO_CUERPO = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mensaje_id", nullable = false, unique = true, length = 120)
    private String mensajeId;

    @Column(name = "evento_id", length = 60)
    private String eventoId;

    @Column(name = "destino_original", length = 200)
    private String destinoOriginal;

    @Column(length = LARGO_CUERPO)
    private String cuerpo;

    @JsonbDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "recibido_en", nullable = false)
    private LocalDateTime recibidoEn;

    public MensajeFallido() {
    }

    public MensajeFallido(String mensajeId, String eventoId, String destinoOriginal, String cuerpo,
                          LocalDateTime recibidoEn) {
        this.mensajeId = mensajeId;
        this.eventoId = eventoId;
        this.destinoOriginal = destinoOriginal;
        this.cuerpo = cuerpo != null && cuerpo.length() > LARGO_CUERPO ? cuerpo.substring(0, LARGO_CUERPO) : cuerpo;
        this.recibidoEn = recibidoEn;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMensajeId() {
        return mensajeId;
    }

    public String getEventoId() {
        return eventoId;
    }

    public String getDestinoOriginal() {
        return destinoOriginal;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public LocalDateTime getRecibidoEn() {
        return recibidoEn;
    }
}
