package com.mediconecta.integraciones;

import com.mediconecta.integraciones.simulacion.ServicioObraSocialLegado;
import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;

import javax.xml.namespace.QName;

import java.net.URL;
import java.util.Map;

/**
 * Adapter que habla SOAP con el sistema legado de obras sociales.
 *
 * <p><b>Contrato primero.</b> El WSDL del servicio viaja dentro del WAR
 * ({@value #WSDL_LOCAL}) en lugar de descargarse del servidor en cada llamada:
 * el cliente no depende de que el WSDL remoto este publicado ni paga una
 * descarga sin timeout antes de cada consulta. La direccion a la que se manda el
 * pedido es configurable con {@code mediconecta.soap.url} (por compatibilidad
 * tambien se acepta {@code mediconecta.soap.wsdl}, sin el {@code ?wsdl}). El
 * valor por omision asume el despliegue estandar del proyecto
 * ({@code http://localhost:8080/mediconecta/...}).</p>
 *
 * <p>La llamada tiene timeout de conexion y de respuesta
 * ({@link TiemposDeEspera}): si el sistema legado no contesta a tiempo se corta
 * y se informa un {@code 504}.</p>
 */
// @LocalBean: un EJB que implementa una interfaz solo expone esa interfaz;
// GestorCobertura inyecta cada adaptador por su clase concreta para elegir cual usar.
@LocalBean
// NOT_SUPPORTED: una llamada remota no tiene que retener una transaccion, y asi
// una falla del sistema externo no marca para rollback la del llamador (que
// necesita seguir viva para responder con el fallback).
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AdaptadorObraSocialLegado implements ValidadorCobertura {

    static final String WSDL_LOCAL = "/wsdl/ServicioObraSocialLegado.wsdl";

    static final String DIRECCION_POR_DEFECTO =
            "http://localhost:8080/mediconecta/ServicioObraSocialLegadoImpl";

    private static final QName NOMBRE_SERVICIO =
            new QName("http://simulacion.integraciones.mediconecta.com/", "ServicioObraSocialLegadoImplService");

    @Override
    public ResultadoCobertura validar(String obraSocial, String numeroAfiliado) {
        URL wsdl = AdaptadorObraSocialLegado.class.getResource(WSDL_LOCAL);
        if (wsdl == null) {
            throw new IntegracionExternaException("Falta el contrato WSDL del sistema legado dentro del WAR.");
        }
        try {
            Service servicio = Service.create(wsdl, NOMBRE_SERVICIO);
            ServicioObraSocialLegado puerto = servicio.getPort(ServicioObraSocialLegado.class);

            Map<String, Object> contexto = ((BindingProvider) puerto).getRequestContext();
            contexto.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, direccion());
            TiemposDeEspera.aplicarSoap(contexto, TiemposDeEspera.conexion(), TiemposDeEspera.respuesta());

            boolean vigente = puerto.consultarVigencia(obraSocial, numeroAfiliado);
            String plan = puerto.consultarPlan(obraSocial, numeroAfiliado);

            return new ResultadoCobertura(vigente, plan, 0, "LEGADO");
        } catch (WebServiceException error) {
            String mensaje = IntegracionExternaException.fueTiempoAgotado(error)
                    ? "El sistema legado de obras sociales no respondio a tiempo."
                    : "El sistema legado de obras sociales no respondio.";
            throw new IntegracionExternaException(mensaje, error);
        }
    }

    /** Direccion del servicio: la configurada o la del despliegue estandar. */
    static String direccion() {
        String url = System.getProperty("mediconecta.soap.url");
        if (url == null || url.isBlank()) {
            url = System.getProperty("mediconecta.soap.wsdl");
        }
        if (url == null || url.isBlank()) {
            return DIRECCION_POR_DEFECTO;
        }
        url = url.trim();
        return url.regionMatches(true, Math.max(0, url.length() - 5), "?wsdl", 0, 5)
                ? url.substring(0, url.length() - 5)
                : url;
    }
}
