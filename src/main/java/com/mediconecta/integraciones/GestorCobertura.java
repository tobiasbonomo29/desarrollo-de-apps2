package com.mediconecta.integraciones;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.negocio.GestionPacientes;
import com.mediconecta.negocio.Roles;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * Patron Adapter: valida la cobertura del paciente contra el sistema que
 * corresponda, con una interfaz unica para quien lo consume.
 *
 * <p>Las obras sociales de {@link #LEGADAS} se resuelven contra el sistema
 * legado por SOAP ({@link AdaptadorObraSocialLegado}); cualquier otra, contra
 * el partner moderno por REST ({@link AdaptadorObraSocialPartner}). Quien
 * llama a {@link #validarCobertura(String)} no sabe cual de los dos se uso:
 * esa es la idea del patron.</p>
 */
@Stateless
public class GestorCobertura {

    private static final Set<String> LEGADAS = Set.of("PAMI", "OSDE", "IOMA");

    @Inject
    private GestionPacientes gestionPacientes;

    @Inject
    private AdaptadorObraSocialLegado adaptadorLegado;

    @Inject
    private AdaptadorObraSocialPartner adaptadorPartner;

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public ResultadoCobertura validarCobertura(String documento) {
        Paciente paciente = gestionPacientes.buscarPorDocumento(documento);
        gestionPacientes.exigirPropioDocumentoSiEsPaciente(paciente.getDocumento());

        String obraSocial = paciente.getCobertura();
        if (obraSocial == null || obraSocial.isBlank()) {
            return new ResultadoCobertura(false, null, 0, "NINGUNA");
        }

        ValidadorCobertura adaptador = esLegada(obraSocial) ? adaptadorLegado : adaptadorPartner;
        return adaptador.validar(obraSocial, paciente.getDocumento());
    }

    private boolean esLegada(String obraSocial) {
        return LEGADAS.contains(obraSocial.trim().toUpperCase());
    }
}
