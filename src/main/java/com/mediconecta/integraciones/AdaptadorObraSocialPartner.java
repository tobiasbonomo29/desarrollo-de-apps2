package com.mediconecta.integraciones;

import com.mediconecta.integraciones.simulacion.RespuestaPartner;
import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.concurrent.TimeUnit;

/**
 * Adapter que habla REST con el partner moderno de obras sociales.
 *
 * <p>La URL base es configurable con la propiedad de sistema
 * {@code mediconecta.partner.baseUrl} porque en produccion apuntaria al
 * dominio real del partner, no al propio WAR de MediConecta.</p>
 *
 * <p>La llamada tiene timeout de conexion y de respuesta
 * ({@link TiemposDeEspera}): si el partner no contesta a tiempo se corta y se
 * informa un {@code 504}.</p>
 */
// @LocalBean: un EJB que implementa una interfaz solo expone esa interfaz;
// GestorCobertura inyecta cada adaptador por su clase concreta para elegir cual usar.
@LocalBean
// NOT_SUPPORTED: una llamada remota no tiene que retener una transaccion, y asi
// una falla del sistema externo no marca para rollback la del llamador (que
// necesita seguir viva para responder con el fallback).
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AdaptadorObraSocialPartner implements ValidadorCobertura {

    private static final String URL_BASE_POR_DEFECTO =
            "http://localhost:8080/mediconecta/partner/cobertura";

    @Override
    public ResultadoCobertura validar(String obraSocial, String numeroAfiliado) {
        Client cliente = ClientBuilder.newBuilder()
                .connectTimeout(TiemposDeEspera.conexion().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(TiemposDeEspera.respuesta().toMillis(), TimeUnit.MILLISECONDS)
                .build();
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
            String mensaje = IntegracionExternaException.fueTiempoAgotado(error)
                    ? "El partner de obras sociales no respondio a tiempo."
                    : "El partner de obras sociales no respondio.";
            throw new IntegracionExternaException(mensaje, error);
        } finally {
            cliente.close();
        }
    }
}
