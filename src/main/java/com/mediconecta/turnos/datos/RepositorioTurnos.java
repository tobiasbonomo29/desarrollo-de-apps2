package com.mediconecta.turnos.datos;

import com.mediconecta.turnos.modelo.Turno;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de acceso a datos de turnos (patron DAO / Repository).
 *
 * <p>Esta interfaz es la frontera entre la capa de negocio y la capa de datos.
 * La capa de negocio depende <b>solo de esta interfaz</b> y nunca de una
 * implementacion concreta, por lo que reemplazar el almacenamiento en memoria
 * por JPA + PostgreSQL en la Entrega 2 se reduce a escribir una nueva clase que
 * implemente estos metodos: ni la capa de negocio ni la de presentacion cambian
 * una sola linea.</p>
 *
 * <p>Notar que el contrato no menciona SQL, listas, ni ningun detalle de
 * almacenamiento: habla en terminos del dominio.</p>
 */
public interface RepositorioTurnos {

    /**
     * Devuelve todos los turnos registrados.
     *
     * @return lista de turnos, vacia si todavia no hay ninguno (nunca {@code null})
     */
    List<Turno> listarTodos();

    /**
     * Busca un turno por su identificador.
     *
     * <p>Devuelve {@link Optional} en lugar de {@code null} para obligar a quien
     * llama a contemplar explicitamente el caso "no existe".</p>
     *
     * @param id identificador del turno buscado
     * @return el turno si existe, u {@link Optional#empty()} si no
     */
    Optional<Turno> buscarPorId(Long id);

    /**
     * Guarda un turno nuevo y le asigna un identificador.
     *
     * @param turno turno a persistir, sin id
     * @return el mismo turno ya con su id asignado
     */
    Turno guardar(Turno turno);

    /**
     * Indica si un profesional ya tiene un turno reservado en ese horario.
     *
     * <p>Vive en la capa de datos porque es una consulta (mañana sera un
     * {@code SELECT ... WHERE}); la decision de que hacer con la respuesta
     * es de la capa de negocio.</p>
     *
     * @param profesional nombre del profesional
     * @param fechaHora   fecha y hora en formato ISO-8601
     * @return {@code true} si ya existe un turno no cancelado para ese profesional y horario
     */
    boolean existeTurnoDe(String profesional, String fechaHora);
}
