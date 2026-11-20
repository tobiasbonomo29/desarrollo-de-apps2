package com.mediconecta.presentacion;

import com.mediconecta.modelo.estado.TransicionInvalidaException;
import com.mediconecta.negocio.AccesoDenegadoException;
import com.mediconecta.negocio.ConflictoIdempotenciaException;
import com.mediconecta.negocio.CredencialesInvalidasException;
import com.mediconecta.negocio.DemasiadasSolicitudesException;
import com.mediconecta.negocio.IntegracionExternaException;
import com.mediconecta.negocio.NoEncontradoException;
import com.mediconecta.negocio.ReglaNegocioException;
import com.mediconecta.negocio.VersionDesactualizadaException;

import jakarta.ejb.EJBAccessException;
import jakarta.json.JsonException;
import jakarta.json.bind.JsonbException;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Traduce cualquier excepcion que llegue a la presentacion a un
 * {@link DetalleProblema}. Es el unico lugar que decide que codigo HTTP y que
 * {@code type} le corresponde a cada error; los {@code Manejador*} solo lo
 * invocan.
 *
 * <p>El negocio no sabe nada de HTTP: lanza sus excepciones de dominio y esta
 * clase hace la traduccion. Los errores no previstos se responden con un 500
 * generico que <b>no</b> expone la traza ni el mensaje interno: se registran en
 * el log con un identificador, y ese mismo identificador viaja en la respuesta
 * ({@code idError}) para poder encontrarlos.</p>
 */
public final class Problemas {

    private static final Logger LOG = Logger.getLogger(Problemas.class.getName());

    private Problemas() {
    }

    /** Respuesta HTTP lista para devolver desde un {@code ExceptionMapper}. */
    public static Response respuesta(Throwable error, UriInfo uriInfo) {
        return respuesta(de(error, instancia(uriInfo)));
    }

    public static Response respuesta(DetalleProblema problema) {
        return Response.status(problema.estado())
                .entity(problema.aJson())
                .type(DetalleProblema.TIPO_MEDIO + ";charset=UTF-8")
                .build();
    }

    /** El problema que corresponde a una excepcion. */
    public static DetalleProblema de(Throwable error, String instancia) {
        if (error instanceof AccesoDenegadoException) {
            return DetalleProblema.de(TipoProblema.ACCESO_DENEGADO, error.getMessage(), instancia);
        }
        if (error instanceof ReglaNegocioException) {
            return DetalleProblema.de(TipoProblema.REGLA_NEGOCIO, error.getMessage(), instancia);
        }
        if (error instanceof NoEncontradoException) {
            return DetalleProblema.de(TipoProblema.NO_ENCONTRADO, error.getMessage(), instancia);
        }
        if (error instanceof TransicionInvalidaException) {
            return DetalleProblema.de(TipoProblema.TRANSICION_INVALIDA, error.getMessage(), instancia);
        }
        if (error instanceof CredencialesInvalidasException) {
            return DetalleProblema.de(TipoProblema.CREDENCIALES_INVALIDAS, error.getMessage(), instancia);
        }
        if (error instanceof EJBAccessException) {
            return DetalleProblema.de(TipoProblema.ACCESO_DENEGADO,
                    "Su rol no tiene permiso para realizar esta operacion.", instancia);
        }
        if (error instanceof IntegracionExternaException externa) {
            TipoProblema tipo = externa.esPorTiempo() ? TipoProblema.TIEMPO_AGOTADO : TipoProblema.INTEGRACION_EXTERNA;
            return DetalleProblema.de(tipo, error.getMessage(), instancia);
        }
        if (error instanceof ConflictoIdempotenciaException conflicto) {
            TipoProblema tipo = conflicto.motivo() == ConflictoIdempotenciaException.Motivo.EN_CURSO
                    ? TipoProblema.IDEMPOTENCIA_EN_CURSO
                    : TipoProblema.IDEMPOTENCIA_REUTILIZADA;
            return DetalleProblema.de(tipo, error.getMessage(), instancia);
        }
        if (error instanceof VersionDesactualizadaException) {
            return DetalleProblema.de(TipoProblema.PRECONDICION_FALLIDA, error.getMessage(), instancia);
        }
        if (error instanceof DemasiadasSolicitudesException) {
            return DetalleProblema.de(TipoProblema.DEMASIADAS_SOLICITUDES, error.getMessage(), instancia);
        }
        if (causadoPor(error, OptimisticLockException.class)) {
            return DetalleProblema.de(TipoProblema.MODIFICACION_CONCURRENTE,
                    "Otro usuario modifico el recurso al mismo tiempo. Vuelva a cargarlo e intente de nuevo.", instancia);
        }
        if (error instanceof WebApplicationException web) {
            return deErrorHttp(web.getResponse().getStatus(), instancia);
        }
        if (esCuerpoMalFormado(error)) {
            return DetalleProblema.de(TipoProblema.PEDIDO_INVALIDO,
                    "El cuerpo de la peticion no es un JSON valido para esta operacion.", instancia);
        }
        return inesperado(error, instancia);
    }

    /** Errores que genera JAX-RS mismo (ruta inexistente, metodo no permitido...). */
    static DetalleProblema deErrorHttp(int estado, String instancia) {
        Response.Status conocido = Response.Status.fromStatusCode(estado);
        String titulo = conocido == null ? "Error HTTP " + estado : conocido.getReasonPhrase();
        String detalle = switch (estado) {
            case 400 -> "La peticion no es valida.";
            case 404 -> "No existe el recurso pedido.";
            case 405 -> "El recurso no admite ese metodo HTTP.";
            case 406 -> "El recurso no puede responder en el formato pedido en Accept.";
            case 415 -> "Tipo de contenido no soportado: envie application/json.";
            default -> titulo;
        };
        if (estado >= 500) {
            return inesperado(null, instancia);
        }
        return new DetalleProblema(DetalleProblema.SIN_TIPO, titulo, estado, detalle, instancia);
    }

    static boolean causadoPor(Throwable error, Class<? extends Throwable> tipo) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (tipo.isInstance(actual)) {
                return true;
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return false;
    }

    /**
     * JSON-B (o JAX-RS al leer el cuerpo) no pudo convertir el JSON recibido.
     * Los adaptadores atrapan sus propios {@code ProcessingException} de
     * cliente, asi que uno que llegue hasta aca viene de leer el pedido.
     */
    static boolean esCuerpoMalFormado(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof JsonbException
                    || actual instanceof JsonException
                    || actual instanceof ProcessingException) {
                return true;
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return false;
    }

    private static DetalleProblema inesperado(Throwable error, String instancia) {
        String id = UUID.randomUUID().toString();
        if (error != null) {
            LOG.log(Level.SEVERE, "[API] Error inesperado " + id + " en " + instancia, error);
        }
        return new DetalleProblema(TipoProblema.ERROR_INTERNO.uri(), TipoProblema.ERROR_INTERNO.titulo(),
                TipoProblema.ERROR_INTERNO.estado(),
                "Ocurrio un error inesperado. Si se repite, informe el codigo " + id + ".",
                instancia, Map.of("idError", id));
    }

    private static String instancia(UriInfo uriInfo) {
        try {
            return uriInfo == null ? null : uriInfo.getRequestUri().getRawPath();
        } catch (RuntimeException fueraDeUnaPeticion) {
            return null;
        }
    }
}
