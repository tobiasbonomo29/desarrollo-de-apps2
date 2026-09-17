package com.mediconecta.presentacion;

/**
 * Cuerpo JSON uniforme para cualquier error de la API:
 * {@code {"error": "...", "codigo": 400}}.
 */
public class ErrorRespuesta {

    private String error;

    private int codigo;

    public ErrorRespuesta() {
    }

    public ErrorRespuesta(String error, int codigo) {
        this.error = error;
        this.codigo = codigo;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getCodigo() {
        return codigo;
    }

    public void setCodigo(int codigo) {
        this.codigo = codigo;
    }
}
