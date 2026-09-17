package com.mediconecta.negocio;

import jakarta.json.bind.annotation.JsonbDateFormat;

import java.time.LocalDateTime;

/**
 * Foto del estado de una conversacion de reserva.
 *
 * <p>Es lo que el asistente {@code @Stateful} responde en cada paso. Lo
 * importante para la defensa: todos estos datos los recuerda el <b>servidor</b>
 * entre request y request; el navegador no los reenvia.</p>
 */
public class EstadoReserva {

    private String idConversacion;

    private String instancia;

    private int paso;

    private String pasoActual;

    private LocalDateTime iniciadaEn;

    private String documentoPaciente;

    private String nombrePaciente;

    private String especialidad;

    private Long profesionalId;

    private String nombreProfesional;

    @JsonbDateFormat("yyyy-MM-dd'T'HH:mm")
    private LocalDateTime fechaHora;

    private boolean finalizada;

    private Long turnoId;

    public EstadoReserva() {
    }

    public String getIdConversacion() {
        return idConversacion;
    }

    public void setIdConversacion(String idConversacion) {
        this.idConversacion = idConversacion;
    }

    public String getInstancia() {
        return instancia;
    }

    public void setInstancia(String instancia) {
        this.instancia = instancia;
    }

    public int getPaso() {
        return paso;
    }

    public void setPaso(int paso) {
        this.paso = paso;
    }

    public String getPasoActual() {
        return pasoActual;
    }

    public void setPasoActual(String pasoActual) {
        this.pasoActual = pasoActual;
    }

    public LocalDateTime getIniciadaEn() {
        return iniciadaEn;
    }

    public void setIniciadaEn(LocalDateTime iniciadaEn) {
        this.iniciadaEn = iniciadaEn;
    }

    public String getDocumentoPaciente() {
        return documentoPaciente;
    }

    public void setDocumentoPaciente(String documentoPaciente) {
        this.documentoPaciente = documentoPaciente;
    }

    public String getNombrePaciente() {
        return nombrePaciente;
    }

    public void setNombrePaciente(String nombrePaciente) {
        this.nombrePaciente = nombrePaciente;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public void setProfesionalId(Long profesionalId) {
        this.profesionalId = profesionalId;
    }

    public String getNombreProfesional() {
        return nombreProfesional;
    }

    public void setNombreProfesional(String nombreProfesional) {
        this.nombreProfesional = nombreProfesional;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public boolean isFinalizada() {
        return finalizada;
    }

    public void setFinalizada(boolean finalizada) {
        this.finalizada = finalizada;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public void setTurnoId(Long turnoId) {
        this.turnoId = turnoId;
    }
}
