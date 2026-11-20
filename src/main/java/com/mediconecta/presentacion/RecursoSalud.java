package com.mediconecta.presentacion;

import com.mediconecta.negocio.EstadoSalud;
import com.mediconecta.negocio.MonitorSalud;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Health checks publicos (sin autenticacion; ver {@code web.xml}).
 *
 * <ul>
 *   <li>{@code GET /api/salud} (<b>readiness</b>): verifica que respondan
 *       PostgreSQL y el broker JMS. {@code 200} con {@code "estado": "UP"} si
 *       todo responde; {@code 503} con {@code "DOWN"} y el componente caido si
 *       no. Es el que usan el {@code HEALTHCHECK} de Docker y el pipeline de CI
 *       para saber cuando la aplicacion esta lista.</li>
 *   <li>{@code GET /api/salud/vivo} (<b>liveness</b>): solo confirma que el WAR
 *       esta desplegado y atiende; no toca dependencias. Si este falla, hay que
 *       reiniciar el servidor; si falla el otro, alcanza con esperar a la base.</li>
 * </ul>
 */
@Path("/salud")
@Produces(MediaType.APPLICATION_JSON)
public class RecursoSalud {

    @Inject
    private MonitorSalud monitorSalud;

    @GET
    public Response listo() {
        EstadoSalud estado = monitorSalud.verificar();
        return Response.status(estado.estaArriba() ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .entity(estado)
                .header("Cache-Control", "no-store")
                .build();
    }

    @GET
    @Path("/vivo")
    public Map<String, String> vivo() {
        return Map.of("estado", EstadoSalud.UP);
    }
}
