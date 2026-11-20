package com.mediconecta.presentacion;

import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.estado.TransicionInvalidaException;
import com.mediconecta.negocio.AccesoDenegadoException;
import com.mediconecta.negocio.ConflictoIdempotenciaException;
import com.mediconecta.negocio.CredencialesInvalidasException;
import com.mediconecta.negocio.IntegracionExternaException;
import com.mediconecta.negocio.NoEncontradoException;
import com.mediconecta.negocio.ReglaNegocioException;

import jakarta.ejb.EJBAccessException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.ProcessingException;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cada excepcion se traduce al codigo HTTP y al "type" de Problem Details que le corresponde. */
class ProblemasTest {

    private static final String RUTA = "/mediconecta/api/turnos";

    private static DetalleProblema de(Throwable error) {
        return Problemas.de(error, RUTA);
    }

    private static JsonObject json(DetalleProblema problema) {
        return Json.createReader(new StringReader(problema.aJson())).readObject();
    }

    @Test
    void reglaDeNegocioEs400ConElMensajeDelNegocio() {
        DetalleProblema problema = de(new ReglaNegocioException("El profesional ya tiene un turno en ese horario."));

        assertEquals(400, problema.estado());
        assertEquals("urn:mediconecta:problema:regla-negocio", problema.tipo());
        assertEquals("El profesional ya tiene un turno en ese horario.", problema.detalle());
        assertEquals(RUTA, problema.instancia());
    }

    @Test
    void cadaExcepcionDeDominioTieneSuCodigo() {
        assertEquals(404, de(NoEncontradoException.turno(9L)).estado());
        assertEquals(409, de(new TransicionInvalidaException("El turno ya estaba cancelado.", EstadoTurno.CANCELADO, "cancelar")).estado());
        assertEquals(401, de(new CredencialesInvalidasException()).estado());
        assertEquals(403, de(new EJBAccessException("rol")).estado());
        assertEquals(403, de(new AccesoDenegadoException("Solo puede consultar sus propios turnos.")).estado());
        assertEquals("urn:mediconecta:problema:acceso-denegado", de(new AccesoDenegadoException("x")).tipo());
        assertEquals(502, de(new IntegracionExternaException("El partner no respondio.")).estado());
    }

    @Test
    void elAccesoDenegadoNoExponeElMensajeDelContenedor() {
        DetalleProblema problema = de(new EJBAccessException("WFLYEJB0364: Invocation on method ... is not allowed"));

        assertEquals(TipoProblema.ACCESO_DENEGADO.uri(), problema.tipo());
        assertFalse(problema.detalle().contains("WFLYEJB"));
    }

    @Test
    void unTimeoutDeUnSistemaExternoEs504() {
        DetalleProblema problema = de(new IntegracionExternaException("no respondio a tiempo",
                new ProcessingException(new SocketTimeoutException("Read timed out"))));

        assertEquals(504, problema.estado());
        assertEquals(TipoProblema.TIEMPO_AGOTADO.uri(), problema.tipo());
    }

    @Test
    void losConflictosDeIdempotenciaSon409y422() {
        assertEquals(409, de(new ConflictoIdempotenciaException(ConflictoIdempotenciaException.Motivo.EN_CURSO, "x")).estado());
        assertEquals(422, de(new ConflictoIdempotenciaException(
                ConflictoIdempotenciaException.Motivo.PEDIDO_DISTINTO, "x")).estado());
    }

    @Test
    void unJsonMalFormadoEs400YNo500() {
        assertEquals(TipoProblema.PEDIDO_INVALIDO.uri(), de(new ProcessingException("bad json")).tipo());
        assertEquals(400, de(new RuntimeException(new JsonbException("Unexpected char"))).estado());
    }

    @Test
    void unErrorInesperadoNoMuestraDetallesInternosPeroDaUnId() {
        DetalleProblema problema = de(new NullPointerException("campo secreto de la implementacion"));

        assertEquals(500, problema.estado());
        assertFalse(problema.detalle().contains("secreto"));
        String id = problema.extensiones().get("idError");
        assertTrue(problema.detalle().contains(id));
        assertEquals(id, json(problema).getString("idError"));
    }

    @Test
    void losErroresHttpGenericosUsanAboutBlank() {
        DetalleProblema noExiste = Problemas.deErrorHttp(404, RUTA);
        assertEquals(DetalleProblema.SIN_TIPO, noExiste.tipo());
        assertEquals("Not Found", noExiste.titulo());

        assertEquals(405, Problemas.deErrorHttp(405, RUTA).estado());
        assertTrue(Problemas.deErrorHttp(415, RUTA).detalle().contains("application/json"));
        assertTrue(Problemas.deErrorHttp(406, RUTA).detalle().contains("Accept"));
        assertEquals("La peticion no es valida.", Problemas.deErrorHttp(400, RUTA).detalle());
        assertEquals("Error HTTP 499", Problemas.deErrorHttp(499, RUTA).titulo());
        assertEquals(500, Problemas.deErrorHttp(503, RUTA).estado(), "un 5xx propio no se describe hacia afuera");
    }

    @Test
    void elJsonTieneLosMiembrosDeLaRfc() {
        JsonObject cuerpo = json(de(new ReglaNegocioException("Dato \"invalido\".")));

        assertEquals("urn:mediconecta:problema:regla-negocio", cuerpo.getString("type"));
        assertEquals("Regla de negocio incumplida", cuerpo.getString("title"));
        assertEquals(400, cuerpo.getInt("status"));
        assertEquals("Dato \"invalido\".", cuerpo.getString("detail"));
        assertEquals(RUTA, cuerpo.getString("instance"));
    }

    @Test
    void sinDetalleNiInstanciaSeOmiten() {
        JsonObject cuerpo = json(new DetalleProblema("about:blank", "Not Found", 404, null, null, Map.of()));

        assertFalse(cuerpo.containsKey("detail"));
        assertFalse(cuerpo.containsKey("instance"));
    }

    @Test
    void todosLosTiposSonUrnDelProyecto() {
        for (TipoProblema tipo : TipoProblema.values()) {
            assertTrue(tipo.uri().startsWith(TipoProblema.PREFIJO), tipo.name());
            assertTrue(tipo.estado() >= 400 && tipo.estado() < 600, tipo.name());
        }
    }

    @Test
    void losNuevosTiposDeResilienciaTienenSuCodigo() {
        assertEquals(412, de(new com.mediconecta.negocio.VersionDesactualizadaException("cambio")).estado());
        DetalleProblema muchos = de(new com.mediconecta.negocio.DemasiadasSolicitudesException("espere",
                java.time.Duration.ofSeconds(30)));
        assertEquals(429, muchos.estado());
        assertEquals(TipoProblema.DEMASIADAS_SOLICITUDES.uri(), muchos.tipo());
        DetalleProblema concurrente = de(new RuntimeException(new jakarta.persistence.OptimisticLockException("x")));
        assertEquals(409, concurrente.estado());
        assertEquals(TipoProblema.MODIFICACION_CONCURRENTE.uri(), concurrente.tipo());
    }
}
