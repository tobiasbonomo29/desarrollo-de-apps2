package com.mediconecta.integraciones;

/**
 * Patron Adapter: contrato unico para validar cobertura, sin importar que
 * sistema externo responda detras.
 *
 * <p>Dos implementaciones: {@link AdaptadorObraSocialLegado} (habla SOAP con
 * el sistema legado) y {@link AdaptadorObraSocialPartner} (habla REST con el
 * partner moderno). {@link GestorCobertura} elige cual usar segun la obra
 * social del paciente; quien consume {@link GestorCobertura} no sabe cual de
 * las dos se invoco.</p>
 */
public interface ValidadorCobertura {

    ResultadoCobertura validar(String obraSocial, String numeroAfiliado);
}
