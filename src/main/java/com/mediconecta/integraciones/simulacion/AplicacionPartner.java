package com.mediconecta.integraciones.simulacion;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Activa el JAX-RS que simula la API publica del partner externo moderno,
 * bajo el prefijo {@code /partner} (separado de {@code /api}, que es la API
 * propia de MediConecta). Ver {@link RecursoPartnerCobertura}.
 */
@ApplicationPath("/partner")
public class AplicacionPartner extends Application {
}
