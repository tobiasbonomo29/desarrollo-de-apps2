package com.mediconecta.datos;

import com.mediconecta.modelo.Paciente;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de acceso a datos de pacientes (patron DAO / Repository).
 *
 * <p>Mismo criterio que {@link RepositorioTurnos}: el negocio
 * ({@code GestionPacientes}) depende de esta interfaz y no sabe si detras hay
 * PostgreSQL o un mapa en memoria.</p>
 */
public interface RepositorioPacientes {

    List<Paciente> listarTodos();

    Optional<Paciente> buscarPorId(Long id);

    /** Busca por documento, que es la clave de negocio del paciente. */
    Optional<Paciente> buscarPorDocumento(String documento);

    /** Busca por coincidencia parcial de nombre o documento (buscador de la UI). */
    List<Paciente> buscar(String texto);

    Paciente guardar(Paciente paciente);

    Paciente actualizar(Paciente paciente);
}
