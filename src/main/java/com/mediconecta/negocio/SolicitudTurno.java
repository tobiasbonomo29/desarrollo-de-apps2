package com.mediconecta.negocio;

/**
 * Pedido de reserva de turno, expresado en el lenguaje del negocio.
 *
 * <p>Es el objeto que la capa de presentacion arma a partir del JSON y que el
 * asistente stateful arma a partir de lo que el usuario fue eligiendo paso a
 * paso. Que los dos caminos terminen en el mismo objeto es lo que garantiza que
 * las reglas se apliquen una sola vez y en un unico lugar.</p>
 *
 * <p>Acepta identificar al profesional por id (lo que usa la interfaz nueva) o
 * por nombre (lo que enviaba la API de la primera entrega), de modo que los
 * clientes viejos siguen funcionando.</p>
 */
public class SolicitudTurno {

    private String documentoPaciente;

    private String nombrePaciente;

    private Long profesionalId;

    private String nombreProfesional;

    private String especialidad;

    private String fechaHora;

    private String motivo;

    public SolicitudTurno() {
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

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
