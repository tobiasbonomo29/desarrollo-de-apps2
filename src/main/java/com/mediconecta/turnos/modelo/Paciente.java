package com.mediconecta.turnos.modelo;

/**
 * Paciente que solicita un turno.
 *
 * <p>Es un objeto del modelo del dominio: solo transporta datos, no tiene
 * logica de negocio ni sabe como se persiste. Esa es justamente la razon por
 * la que puede viajar libremente entre las tres capas.</p>
 *
 * <p>Necesita constructor sin argumentos y getters/setters publicos porque el
 * proveedor JSON del contenedor lo instancia por reflexion al deserializar el
 * cuerpo de un POST.</p>
 */
public class Paciente {

    /** Nombre y apellido del paciente. */
    private String nombre;

    /** Documento Nacional de Identidad, usado como identificador del paciente. */
    private String documento;

    public Paciente() {
        // Requerido por el proveedor JSON (JSON-B / Jackson).
    }

    public Paciente(String nombre, String documento) {
        this.nombre = nombre;
        this.documento = documento;
    }

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
