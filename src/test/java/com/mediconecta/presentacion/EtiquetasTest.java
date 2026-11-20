package com.mediconecta.presentacion;

import com.mediconecta.negocio.ReglaNegocioException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** ETag de turnos y pacientes y lectura de If-Match. */
class EtiquetasTest {

    @Test
    void elEtagIncluyeRecursoIdYVersion() {
        assertEquals("turno-5-v3", Etiquetas.valor("turno", 5L, 3));
    }

    @Test
    void ifMatchDevuelveLaVersionEsperada() {
        assertEquals(3L, Etiquetas.versionEsperada("\"turno-5-v3\"", "turno", 5L));
        assertEquals(7L, Etiquetas.versionEsperada("paciente-30111222-v7", "paciente", "30111222"));
    }

    @Test
    void sinIfMatchOConComodinNoHayPrecondicion() {
        assertNull(Etiquetas.versionEsperada(null, "turno", 5L));
        assertNull(Etiquetas.versionEsperada(" ", "turno", 5L));
        assertNull(Etiquetas.versionEsperada("*", "turno", 5L));
    }

    @Test
    void unEtagDeOtroRecursoOMalFormadoNuncaCoincide() {
        assertEquals(-1L, Etiquetas.versionEsperada("\"turno-6-v3\"", "turno", 5L));
        assertEquals(-1L, Etiquetas.versionEsperada("\"turno-5-vX\"", "turno", 5L));
        assertThrows(ReglaNegocioException.class, () -> Etiquetas.versionEsperada("W/\"turno-5-v3\"", "turno", 5L));
    }
}
