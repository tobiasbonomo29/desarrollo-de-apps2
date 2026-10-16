package com.mediconecta.integraciones;

import com.mediconecta.integraciones.simulacion.RespuestaPartner;
import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.ejb.Stateless;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Adapter que habla REST con el partner moderno de obras sociales.
 *
 * <p>La URL base es configurable con la propiedad de sistema
 * {@code mediconecta.partner.baseUrl} porque en produccion apuntaria al
 * dominio real del partner, no al propio WAR de MediConecta.</p>
 */
@Stateless
public class AdaptadorObraSocialPartner implements ValidadorCobertura {

    private static final String URL_BASE_POR_DEFECTO =
            "http://localhost:8080/mediconecta/partner/cobertura";

    @Override
    public ResultadoCobertura validar(String obraSocial, String numeroAfiliado) {
        Client cliente = ClientBuilder.newClient();
        try {
            String urlBase = System.getProperty("mediconecta.partner.baseUrl", URL_BASE_POR_DEFECTO);

            try (Response respuesta = cliente.target(urlBase)
                    .queryParam("obraSocial", obraSocial)
                    .queryParam("afiliado", numeroAfiliado)
                    .request(MediaType.APPLICATION_JSON)
                    .get()) {

                if (respuesta.getStatus() != Response.Status.OK.getStatusCode()) {
                    throw new IntegracionExternaException(
                            "El partner de obras sociales respondio con codigo " + respuesta.getStatus() + ".");
                }

                RespuestaPartner cuerpo = respuesta.readEntity(RespuestaPartner.class);
                return new ResultadoCobertura(cuerpo.isVigente(), cuerpo.getPlan(), cuerpo.getCopago(), "PARTNER");
            }
        } catch (ProcessingException error) {
            throw new IntegracionExternaException("El partner de obras sociales no respondio.", error);
        } finally {
            cliente.close();
        }
    }
}
