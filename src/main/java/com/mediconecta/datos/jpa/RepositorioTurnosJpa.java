package com.mediconecta.datos.jpa;

import com.mediconecta.datos.RepositorioTurnos;
import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Turno;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion de {@link RepositorioTurnos} sobre JPA + PostgreSQL.
 *
 * <p>Es la unica clase del proyecto, junto con sus hermanas de este paquete, que
 * conoce el {@link EntityManager}. Ni la capa de negocio ni la de presentacion
 * lo importan: esa es exactamente la ventaja que da el patron DAO.</p>
 *
 * <p>No abre ni cierra transacciones: el {@code EntityManager} es de tipo
 * transaction-scoped y se engancha a la transaccion JTA que el contenedor abrio
 * al entrar al metodo del EJB {@code @Stateless} que llamo a este repositorio.</p>
 */
@ApplicationScoped
public class RepositorioTurnosJpa implements RepositorioTurnos {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Override
    public List<Turno> listarTodos() {
        return em.createQuery("select t from Turno t order by t.fechaHora", Turno.class)
                .getResultList();
    }

    @Override
    public Optional<Turno> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(Turno.class, id));
    }

    @Override
    public Turno guardar(Turno turno) {
        em.persist(turno);
        em.flush();
        return turno;
    }

    @Override
    public Turno actualizar(Turno turno) {
        return em.merge(turno);
    }

    @Override
    public boolean existeTurnoVigente(Long profesionalId, LocalDateTime fechaHora) {
        Long cantidad = em.createQuery(
                        "select count(t) from Turno t "
                                + "where t.profesional.id = :profesionalId "
                                + "and t.fechaHora = :fechaHora "
                                + "and t.estado <> :cancelado", Long.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("fechaHora", fechaHora)
                .setParameter("cancelado", EstadoTurno.CANCELADO)
                .getSingleResult();
        return cantidad > 0;
    }

    @Override
    public List<Turno> listarPorPaciente(String documento) {
        return em.createQuery(
                        "select t from Turno t where t.paciente.documento = :documento "
                                + "order by t.fechaHora desc", Turno.class)
                .setParameter("documento", documento)
                .getResultList();
    }

    @Override
    public List<Turno> listarPorProfesionalYFecha(Long profesionalId, LocalDate fecha) {
        return em.createQuery(
                        "select t from Turno t where t.profesional.id = :profesionalId "
                                + "and t.fechaHora between :desde and :hasta "
                                + "order by t.fechaHora", Turno.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("desde", fecha.atStartOfDay())
                .setParameter("hasta", fecha.atTime(LocalTime.MAX))
                .getResultList();
    }

    @Override
    public List<Turno> listarPorFecha(LocalDate fecha) {
        return em.createQuery(
                        "select t from Turno t where t.fechaHora between :desde and :hasta "
                                + "order by t.fechaHora", Turno.class)
                .setParameter("desde", fecha.atStartOfDay())
                .setParameter("hasta", fecha.atTime(LocalTime.MAX))
                .getResultList();
    }
}
