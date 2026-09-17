package com.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Paciente de la clinica.
 *
 * <p>El documento es la clave de negocio (unica) y por eso es el identificador
 * que usan las URLs de la API; el id numerico es la clave tecnica que necesita
 * la base de datos.</p>
 *
 * <p>El campo usuario vincula de forma opcional la ficha del paciente con el
 * usuario con el que inicia sesion. Es lo que permite que alguien con rol
 * PACIENTE consulte su propio historial y no el de otro.</p>
 */
@Entity
@Table(name = "pacientes")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String documento;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 120)
    private String email;

    @Column(length = 40)
    private String telefono;

    @Column(length = 60)
    private String cobertura;

    @Column(length = 60)
    private String usuario;

    @Column(nullable = false)
    private boolean activo = true;

    public Paciente() {
    }

    public Paciente(String nombre, String documento) {
        this.nombre = nombre;
        this.documento = documento;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCobertura() {
        return cobertura;
    }

    public void setCobertura(String cobertura) {
        this.cobertura = cobertura;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
