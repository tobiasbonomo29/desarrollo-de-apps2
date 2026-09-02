package com.mediconecta.turnos.presentacion;

/**
 * Cuerpo JSON que se devuelve cuando una operacion falla.
 *
 * <p>Existe para que la API conteste siempre JSON, tambien en los errores:
 * un cliente que hace {@code response.json()} no deberia romperse porque el
 * servidor le devolvio texto plano o una pagina de error de WildFly.</p>
 *
 * <p>Ejemplo de salida:
 * {@code {"error":"No se puede agendar un turno en el pasado.","codigo":400}}</p>
 */
public class ErrorRespuesta {

    /** Mensaje explicativo, pensado para que lo lea una persona. */
    private String error;

    /** Codigo HTTP con el que se responde, repetido en el cuerpo por comodidad del cliente. */
    private int codigo;

    public ErrorRespuesta() {
        // Requerido por el proveedor JSON.
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
