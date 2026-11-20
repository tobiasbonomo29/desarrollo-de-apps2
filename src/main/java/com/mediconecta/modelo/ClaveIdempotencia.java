package com.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Registro de una {@code Idempotency-Key} ya procesada en {@code POST /api/turnos}.
 *
 * <p>Asocia la clave que mando el cliente (por usuario: dos usuarios pueden usar
 * la misma clave sin pisarse) con el turno que se creo y con una huella del
 * pedido. Si el cliente repite el pedido con la misma clave, se le devuelve ese
 * turno en lugar de crear otro; si la repite con otro cuerpo, se rechaza.</p>
 *
 * <p>Se guarda en la misma transaccion que el turno: o quedan los dos o no
 * queda ninguno. La restriccion unica {@code (usuario, clave)} es la que resuelve
 * dos pedidos simultaneos con la misma clave.</p>
 *
 * <p>Nunca se serializa a JSON; la relacion con el turno es unidireccional.</p>
 */
@Entity
@Table(name = "claves_idempotencia",
        uniqueConstraints = @UniqueConstraint(name = "uk_claves_idempotencia_usuario_clave",
                columnNames = {"usuario", "clave"}))
public class ClaveIdempotencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String clave;

    @Column(nullable = false, length = 60)
    private String usuario;

    /** SHA-256 en hexadecimal del pedido normalizado. */
    @Column(nullable = false, length = 64)
    private String huella;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    public ClaveIdempotencia() {
    }

    public ClaveIdempotencia(String clave, String usuario, String huella, Turno turno, LocalDateTime creadaEn) {
        this.clave = clave;
        this.usuario = usuario;
        this.huella = huella;
        this.turno = turno;
        this.creadaEn = creadaEn;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClave() {
        return clave;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getHuella() {
        return huella;
    }

    public Turno getTurno() {
        return turno;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }
}
