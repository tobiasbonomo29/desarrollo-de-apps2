package com.mediconecta.integraciones.simulacion;

import jakarta.ejb.Stateless;
import jakarta.jws.WebService;

import java.util.Map;

/**
 * Implementacion de demostracion del sistema legado.
 *
 * <p>No hay una base de afiliados real detras: resuelve contra una tabla fija
 * en memoria. Lo que importa para la materia es el protocolo de integracion
 * (SOAP con su WSDL publicado por el contenedor), no el origen de los datos.
 * Al ser un EJB {@code @Stateless} anotado {@code @WebService}, WildFly lo
 * publica automaticamente como servicio SOAP al desplegar el WAR, sin
 * configuracion adicional en {@code web.xml}.</p>
 */
@Stateless(name = "ServicioObraSocialLegado")
@WebService(endpointInterface = "com.mediconecta.integraciones.simulacion.ServicioObraSocialLegado")
public class ServicioObraSocialLegadoImpl implements ServicioObraSocialLegado {

    private static final Map<String, String> PLANES = Map.of(
            "PAMI", "Plan basico PAMI",
            "OSDE", "OSDE 210",
            "IOMA", "IOMA plan unico");

    @Override
    public boolean consultarVigencia(String obraSocial, String numeroAfiliado) {
        return obraSocial != null && PLANES.containsKey(obraSocial.trim().toUpperCase())
                && numeroAfiliado != null && !numeroAfiliado.isBlank();
    }

    @Override
    public String consultarPlan(String obraSocial, String numeroAfiliado) {
        String clave = obraSocial == null ? "" : obraSocial.trim().toUpperCase();
        return PLANES.getOrDefault(clave, "Plan no identificado");
    }
}
