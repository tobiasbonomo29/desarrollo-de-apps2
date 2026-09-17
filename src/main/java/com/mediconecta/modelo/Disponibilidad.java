package com.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Franja horaria semanal en la que un profesional atiende.
 *
 * <p>Ejemplo: lunes de 09:00 a 13:00 en turnos de 30 minutos. De aca sale la
 * grilla de horarios que se le ofrece al paciente; los horarios que ya tienen
 * un turno vigente los descuenta GestionProfesionales.horariosLibres.</p>
 *
 * <p>No guarda referencia al profesional: la disponibilidad siempre se navega
 * desde {@link Profesional}, asi el JSON queda sin ciclos.</p>
 */
@Entity
@Table(name = "disponibilidades")
public class Disponibilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 12)
    private DayOfWeek diaSemana;

    @Column(name = "hora_desde", nullable = false)
    private LocalTime horaDesde;

    @Column(name = "hora_hasta", nullable = false)
    private LocalTime horaHasta;

    @Column(name = "duracion_minutos", nullable = false)
    private int duracionMinutos = 30;

    public Disponibilidad() {
    }

    public Disponibilidad(DayOfWeek diaSemana, LocalTime horaDesde, LocalTime horaHasta, int duracionMinutos) {
        this.diaSemana = diaSemana;
        this.horaDesde = horaDesde;
        this.horaHasta = horaHasta;
        this.duracionMinutos = duracionMinutos;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(DayOfWeek diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalTime getHoraDesde() {
        return horaDesde;
    }

    public void setHoraDesde(LocalTime horaDesde) {
        this.horaDesde = horaDesde;
    }

    public LocalTime getHoraHasta() {
        return horaHasta;
    }

    public void setHoraHasta(LocalTime horaHasta) {
        this.horaHasta = horaHasta;
    }

    public int getDuracionMinutos() {
        return duracionMinutos;
    }

    public void setDuracionMinutos(int duracionMinutos) {
        this.duracionMinutos = duracionMinutos;
    }
}
