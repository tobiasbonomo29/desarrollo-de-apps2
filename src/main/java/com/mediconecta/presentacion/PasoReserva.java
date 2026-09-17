package com.mediconecta.presentacion;

/**
 * Cuerpo JSON de cada paso del asistente de reserva.
 *
 * <p>Es deliberadamente chico: en cada paso el navegador manda <b>solo el dato
 * nuevo</b>. Lo elegido en los pasos anteriores no viaja en el request porque lo
 * recuerda el EJB stateful del servidor.</p>
 */
public class PasoReserva {

    private String documentoPaciente;

    private String especialidad;

    private Long profesionalId;

    private String fechaHora;

    public String getDocumentoPaciente() {
        return documentoPaciente;
    }

    public void setDocumentoPaciente(String documentoPaciente) {
        this.documentoPaciente = documentoPaciente;
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

    public String getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(String fechaHora) {
        this.fechaHora = fechaHora;
    }
}
