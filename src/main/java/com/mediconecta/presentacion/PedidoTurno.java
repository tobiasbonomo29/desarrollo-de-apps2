package com.mediconecta.presentacion;

import com.mediconecta.negocio.SolicitudTurno;

/**
 * Cuerpo JSON aceptado por {@code POST /api/turnos}.
 *
 * <p>Acepta las dos formas de pedir un turno y por eso existe como DTO separado
 * del modelo:</p>
 *
 * <ul>
 *   <li>la forma de la interfaz actual:
 *       {@code {"documentoPaciente":"30111222","profesionalId":1,"fechaHora":"..."}}</li>
 *   <li>la forma que usaba la API de la primera entrega:
 *       {@code {"paciente":{"nombre":"...","documento":"..."},"profesional":"Dr. Martin Ruiz",...}}</li>
 * </ul>
 *
 * <p>La traduccion a {@link SolicitudTurno} es un mapeo, no una regla: no decide
 * nada, solo acomoda nombres de campos. Las reglas siguen viviendo en negocio.</p>
 */
public class PedidoTurno {

    private PacienteBreve paciente;

    private String documentoPaciente;

    private String nombrePaciente;

    private Long profesionalId;

    private String profesional;

    private String especialidad;

    private String fechaHora;

    private String motivo;

    public SolicitudTurno aSolicitud() {
        SolicitudTurno solicitud = new SolicitudTurno();
        solicitud.setDocumentoPaciente(primeroNoVacio(
                documentoPaciente, paciente == null ? null : paciente.getDocumento()));
        solicitud.setNombrePaciente(primeroNoVacio(
                nombrePaciente, paciente == null ? null : paciente.getNombre()));
        solicitud.setProfesionalId(profesionalId);
        solicitud.setNombreProfesional(profesional);
        solicitud.setEspecialidad(especialidad);
        solicitud.setFechaHora(fechaHora);
        solicitud.setMotivo(motivo);
        return solicitud;
    }

    private String primeroNoVacio(String preferido, String alternativo) {
        return (preferido != null && !preferido.isBlank()) ? preferido : alternativo;
    }

    public PacienteBreve getPaciente() {
        return paciente;
    }

    public void setPaciente(PacienteBreve paciente) {
        this.paciente = paciente;
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

    public String getProfesional() {
        return profesional;
    }

    public void setProfesional(String profesional) {
        this.profesional = profesional;
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

    /** Paciente embebido, tal como lo enviaba la API de la Entrega 1. */
    public static class PacienteBreve {

        private String nombre;

        private String documento;

        public String getNombre() {
            return nombre;
        }

        public void setNombre(String nombre) {
            this.nombre = nombre;
        }

        public String getDocumento() {
            return documento;
        }

        public void setDocumento(String documento) {
            this.documento = documento;
        }
    }
}
