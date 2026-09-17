package com.mediconecta.presentacion;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Activa JAX-RS y fija el prefijo de la API.
 *
 * <p>Con esta clase alcanza para configurar REST; el {@code web.xml} del
 * proyecto existe unicamente por la seguridad declarativa.</p>
 */
@ApplicationPath("/api")
public class AplicacionRest extends Application {
}
