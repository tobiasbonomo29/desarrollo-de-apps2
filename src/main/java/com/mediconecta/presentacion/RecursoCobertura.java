package com.mediconecta.presentacion;

import com.mediconecta.integraciones.GestorCobertura;
import com.mediconecta.integraciones.ResultadoCobertura;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Valida la cobertura de un paciente contra el sistema que corresponda
 * (legado por SOAP o partner por REST). Quien consume esta API no sabe cual
 * de los dos se uso: lo decide {@link GestorCobertura} con el patron Adapter.
 */
@Path("/pacientes/{documento}/cobertura")
@Produces(MediaType.APPLICATION_JSON)
public class RecursoCobertura {

    @Inject
    private GestorCobertura gestorCobertura;

    @GET
    public ResultadoCobertura validar(@PathParam("documento") String documento) {
        return gestorCobertura.validarCobertura(documento);
    }
}
