package com.mediconecta.modelo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Profesional que atiende en la clinica.
 *
 * <p>La especialidad se modela como texto y no como entidad propia: para lo que
 * el sistema necesita hoy (listar, filtrar y agrupar) una tabla de
 * especialidades solo agregaria un join sin aportar ninguna regla.</p>
 *
 * <p>Las disponibilidades cuelgan del profesional con cascada total y
 * orphanRemoval: redefinir la agenda es reemplazar la lista completa.</p>
 */
@Entity
@Table(name = "profesionales")
public class Profesional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String especialidad;

    @Column(nullable = false, unique = true, length = 30)
    private String matricula;

    @Column(length = 120)
    private String email;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "profesional_id", nullable = false)
    private List<Disponibilidad> disponibilidades = new ArrayList<>();

    public Profesional() {
    }

    public Profesional(String nombre, String especialidad, String matricula) {
        this.nombre = nombre;
        this.especialidad = especialidad;
        this.matricula = matricula;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public List<Disponibilidad> getDisponibilidades() {
        return disponibilidades;
    }

    public void setDisponibilidades(List<Disponibilidad> disponibilidades) {
        this.disponibilidades = disponibilidades;
    }
}
