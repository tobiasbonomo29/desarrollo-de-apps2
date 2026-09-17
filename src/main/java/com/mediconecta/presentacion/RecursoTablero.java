package com.mediconecta.presentacion;

import com.mediconecta.negocio.FachadaReservas;
import com.mediconecta.negocio.ResumenAgenda;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Datos de la pantalla de inicio.
 *
 * <p>Una sola llamada que la fachada resuelve consultando a los tres componentes
 * stateless. Sin la fachada, esta pantalla necesitaria cuatro requests y la
 * presentacion tendria que saber a quien preguntarle cada cosa.</p>
 */
@Path("/tablero")
@Produces(MediaType.APPLICATION_JSON)
public class RecursoTablero {

    @Inject
    private FachadaReservas fachadaReservas;

    @GET
    public ResumenAgenda resumen() {
        return fachadaReservas.resumenDelDia();
    }
}
