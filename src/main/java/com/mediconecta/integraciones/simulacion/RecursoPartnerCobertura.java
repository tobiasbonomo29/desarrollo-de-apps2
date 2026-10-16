package com.mediconecta.integraciones.simulacion;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Simula la API REST publica de un partner moderno de obras sociales.
 *
 * <p>MediConecta la consume desde
 * {@code com.mediconecta.integraciones.AdaptadorObraSocialPartner}. En un
 * escenario real esta clase no existiria en este WAR: el partner publicaria
 * su propia API en su propio dominio, con su propio esquema de autenticacion
 * (API key, OAuth). Por eso vive bajo {@code /partner}, un
 * {@code @ApplicationPath} distinto de {@code /api} y fuera del
 * {@code security-constraint} de {@code web.xml}: no comparte la sesion ni la
 * autenticacion FORM del resto de MediConecta.</p>
 */
@Path("/cobertura")
@Produces(MediaType.APPLICATION_JSON)
public class RecursoPartnerCobertura {

    private record Plan(String nombre, double copago) {
    }

    private static final Map<String, Plan> PLANES = Map.of(
            "SWISS MEDICAL", new Plan("SMG20", 3500.0),
            "GALENO", new Plan("Galeno 220", 2800.0),
            "MEDICUS", new Plan("Medicus 3.3", 3100.0));

    @GET
    public RespuestaPartner consultar(@QueryParam("obraSocial") String obraSocial,
                                       @QueryParam("afiliado") String afiliado) {
        Plan plan = obraSocial == null ? null : PLANES.get(obraSocial.trim().toUpperCase());
        boolean vigente = plan != null && afiliado != null && !afiliado.isBlank();

        return new RespuestaPartner(vigente, vigente ? plan.nombre() : null, vigente ? plan.copago() : 0);
    }
}
