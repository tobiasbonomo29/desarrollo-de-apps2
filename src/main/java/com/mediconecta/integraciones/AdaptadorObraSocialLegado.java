package com.mediconecta.integraciones;

import com.mediconecta.integraciones.simulacion.ServicioObraSocialLegado;
import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.ejb.Stateless;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;

import javax.xml.namespace.QName;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Adapter que habla SOAP con el sistema legado de obras sociales.
 *
 * <p>La URL del WSDL es configurable con la propiedad de sistema
 * {@code mediconecta.soap.wsdl} porque depende de donde quedo publicado
 * {@code ServicioObraSocialLegadoImpl} en el WildFly real (contexto y, segun
 * version del servidor, el nombre exacto del EJB). El valor por omision
 * asume el despliegue estandar del proyecto
 * ({@code http://localhost:8080/mediconecta/...}); conviene confirmarlo una
 * vez desplegado mirando el log de arranque de WildFly, que imprime la URL
 * publicada de cada servicio SOAP.</p>
 */
@Stateless
public class AdaptadorObraSocialLegado implements ValidadorCobertura {

    private static final String WSDL_POR_DEFECTO =
            "http://localhost:8080/mediconecta/ServicioObraSocialLegado?wsdl";

    private static final QName NOMBRE_SERVICIO =
            new QName("http://simulacion.integraciones.mediconecta.com/", "ServicioObraSocialLegadoImplService");

    @Override
    public ResultadoCobertura validar(String obraSocial, String numeroAfiliado) {
        try {
            URL wsdl = new URL(System.getProperty("mediconecta.soap.wsdl", WSDL_POR_DEFECTO));
            Service servicio = Service.create(wsdl, NOMBRE_SERVICIO);
            ServicioObraSocialLegado puerto = servicio.getPort(ServicioObraSocialLegado.class);

            boolean vigente = puerto.consultarVigencia(obraSocial, numeroAfiliado);
            String plan = puerto.consultarPlan(obraSocial, numeroAfiliado);

            return new ResultadoCobertura(vigente, plan, 0, "LEGADO");
        } catch (MalformedURLException error) {
            throw new IntegracionExternaException(
                    "La URL del sistema legado de obras sociales esta mal configurada.", error);
        } catch (WebServiceException error) {
            throw new IntegracionExternaException(
                    "El sistema legado de obras sociales no respondio.", error);
        }
    }
}
