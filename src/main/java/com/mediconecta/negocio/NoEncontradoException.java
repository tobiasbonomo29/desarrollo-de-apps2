package com.mediconecta.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Se pidio una entidad que no existe. La presentacion la traduce a un 404.
 *
 * <p>Los metodos de fabrica arman el mensaje para que sea consistente en toda la
 * aplicacion y para no repetir el texto en cada componente.</p>
 */
@ApplicationException(rollback = false)
public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static NoEncontradoException turno(Long id) {
        return new NoEncontradoException("No existe un turno con id " + id);
    }

    public static NoEncontradoException paciente(String documento) {
        return new NoEncontradoException("No existe un paciente con documento " + documento);
    }

    public static NoEncontradoException profesional(Long id) {
        return new NoEncontradoException("No existe un profesional con id " + id);
    }
}
