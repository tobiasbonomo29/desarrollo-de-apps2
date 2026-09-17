package com.mediconecta.datos;

import com.mediconecta.modelo.Profesional;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de acceso a datos de profesionales (patron DAO / Repository).
 *
 * <p>Incluye la consulta de especialidades porque es una consulta sobre el
 * almacenamiento (un {@code SELECT DISTINCT}), no una regla de negocio.</p>
 */
public interface RepositorioProfesionales {

    List<Profesional> listarTodos();

    Optional<Profesional> buscarPorId(Long id);

    Optional<Profesional> buscarPorMatricula(String matricula);

    List<Profesional> listarPorEspecialidad(String especialidad);

    /** Especialidades distintas que hoy tienen al menos un profesional activo. */
    List<String> listarEspecialidades();

    Profesional guardar(Profesional profesional);

    Profesional actualizar(Profesional profesional);
}
