package com.mediconecta.turnos.presentacion;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Punto de entrada de la API REST.
 *
 * <p>La sola presencia de esta clase activa JAX-RS en el WAR y fija el prefijo
 * comun de todos los recursos en {@code /api}. Por eso el proyecto no necesita
 * un {@code web.xml}: la configuracion esta en el codigo.</p>
 *
 * <p>Queda vacia a proposito. Al no sobrescribir {@code getClasses()}, el
 * contenedor descubre solo todas las clases anotadas con {@code @Path} y
 * {@code @Provider}, y no hay que acordarse de registrar cada recurso nuevo.</p>
 *
 * <p>URL resultante: {@code http://localhost:8080/mediconecta/api/...}, donde
 * {@code mediconecta} es el context root (definido por el nombre del WAR).</p>
 */
@ApplicationPath("/api")
public class AplicacionRest extends Application {
    // Sin contenido: el descubrimiento automatico de recursos alcanza.
}
