package com.mediconecta.integraciones.simulacion;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

/**
 * Interfaz de servicio (SEI) del sistema legado externo de obras sociales que
 * MediConecta integra por SOAP.
 *
 * <p>En un escenario real este servicio lo publicaria la obra social en su
 * propio servidor. Para poder demostrar la integracion de punta a punta sin
 * depender de un sistema externo real, MediConecta lo publica y lo consume a
 * la vez: {@link ServicioObraSocialLegadoImpl} lo expone como EJB + JAX-WS, y
 * {@code com.mediconecta.integraciones.AdaptadorObraSocialLegado} lo consume
 * como cliente SOAP contra su propio WSDL.</p>
 */
@WebService
public interface ServicioObraSocialLegado {

    @WebMethod
    boolean consultarVigencia(String obraSocial, String numeroAfiliado);

    @WebMethod
    String consultarPlan(String obraSocial, String numeroAfiliado);
}
