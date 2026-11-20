package com.mediconecta.integraciones;

/**
 * Respuesta del asistente de orientacion.
 *
 * <p>{@code especialidad} es {@code null} cuando el asistente todavia necesita
 * mas datos (y entonces {@code mensaje} es una pregunta) o cuando ninguna de
 * las especialidades disponibles encaja. {@code urgente} indica que lo que
 * conto la persona parece una emergencia y no deberia esperar un turno.</p>
 */
public class SugerenciaEspecialidad {

    private String mensaje;

    private String especialidad;

    private boolean urgente;

    /** {@code true} si la sugerencia la dio la orientacion de respaldo y no el modelo de IA. */
    private boolean contingencia;

    public SugerenciaEspecialidad() {
    }

    public SugerenciaEspecialidad(String mensaje, String especialidad, boolean urgente) {
        this.mensaje = mensaje;
        this.especialidad = especialidad;
        this.urgente = urgente;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }

    public boolean isUrgente() {
        return urgente;
    }

    public void setUrgente(boolean urgente) {
        this.urgente = urgente;
    }

    public boolean isContingencia() {
        return contingencia;
    }

    public void setContingencia(boolean contingencia) {
        this.contingencia = contingencia;
    }
}
