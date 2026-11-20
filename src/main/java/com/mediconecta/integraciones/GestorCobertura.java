package com.mediconecta.integraciones;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.negocio.GestionPacientes;
import com.mediconecta.negocio.IntegracionExternaException;
import com.mediconecta.negocio.Roles;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

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

    /**
     * Obras sociales conocidas. La ficha del paciente guarda la cobertura con
     * el plan ("OSDE 210", "Swiss Medical SMG02"), asi que se identifica la
     * obra social por el comienzo del texto. Las mas largas van primero.
     */
    private static final List<String> CONOCIDAS =
            List.of("SWISS MEDICAL", "MEDICUS", "GALENO", "PAMI", "OSDE", "IOMA");

    @Inject
    private GestionPacientes gestionPacientes;

    @Inject
    private AdaptadorObraSocialLegado adaptadorLegado;

    @Inject
    private AdaptadorObraSocialPartner adaptadorPartner;

    @Inject
    private CacheCobertura cache;

    private static final Logger LOG = Logger.getLogger(GestorCobertura.class.getName());

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public ResultadoCobertura validarCobertura(String documento) {
        Paciente paciente = gestionPacientes.buscarPorDocumento(documento);
        gestionPacientes.exigirPropioDocumentoSiEsPaciente(paciente.getDocumento());

        String obraSocial = paciente.getCobertura();
        if (obraSocial == null || obraSocial.isBlank()) {
            return new ResultadoCobertura(false, null, 0, "NINGUNA");
        }

        String nombre = obraSocialDe(obraSocial);
        ValidadorCobertura adaptador = esLegada(nombre) ? adaptadorLegado : adaptadorPartner;
        String clave = nombre + "|" + paciente.getDocumento();
        try {
            ResultadoCobertura resultado = adaptador.validar(nombre, paciente.getDocumento());
            cache.guardar(clave, resultado);
            return resultado;
        } catch (IntegracionExternaException falla) {
            return respaldo(clave, falla);
        }
    }

    /**
     * <b>Fallback.</b> Si el sistema externo falla o no responde dentro de los 5
     * segundos de timeout, la pantalla no se rompe: se devuelve el ultimo
     * resultado bueno de las ultimas 24 h, marcado como de contingencia, o, si
     * no hay, un resultado "a verificar" para que recepcion lo confirme en la
     * atencion. La reserva de turnos no depende de esta consulta.
     */
    ResultadoCobertura respaldo(String clave, IntegracionExternaException falla) {
        LOG.warning("[COBERTURA] " + falla.getMessage() + " Se responde con el respaldo.");
        String motivo = falla.esPorTiempo() ? "no respondio a tiempo" : "no esta disponible";
        return cache.buscar(clave)
                .map(guardado -> {
                    guardado.setContingencia(true);
                    guardado.setOrigen("CACHE");
                    guardado.setMensaje("El sistema de la obra social " + motivo
                            + ". Se muestra el ultimo resultado verificado (menos de 24 h).");
                    return guardado;
                })
                .orElseGet(() -> {
                    ResultadoCobertura aVerificar = new ResultadoCobertura(false, null, 0, "CONTINGENCIA");
                    aVerificar.setContingencia(true);
                    aVerificar.setMensaje("El sistema de la obra social " + motivo
                            + ". La cobertura se verificara en la atencion.");
                    return aVerificar;
                });
    }

    /** "OSDE 210" -> "OSDE"; "Swiss Medical SMG02" -> "SWISS MEDICAL"; otra -> tal cual, en mayusculas. */
    static String obraSocialDe(String cobertura) {
        String texto = cobertura.trim().toUpperCase(Locale.ROOT);
        for (String conocida : CONOCIDAS) {
            if (texto.equals(conocida) || texto.startsWith(conocida + " ")) {
                return conocida;
            }
        }
        return texto;
    }

    static boolean esLegada(String obraSocial) {
        return LEGADAS.contains(obraSocial);
    }
}
