package com.mediconecta.datos.jpa;

import com.mediconecta.datos.RepositorioProfesionales;
import com.mediconecta.modelo.Profesional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

/** Implementacion de {@link RepositorioProfesionales} sobre JPA + PostgreSQL. */
@ApplicationScoped
public class RepositorioProfesionalesJpa implements RepositorioProfesionales {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Override
    public List<Profesional> listarTodos() {
        return em.createQuery("select p from Profesional p order by p.nombre", Profesional.class)
                .getResultList();
    }

    @Override
    public Optional<Profesional> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(Profesional.class, id));
    }

    @Override
    public Optional<Profesional> buscarPorMatricula(String matricula) {
        if (matricula == null) {
            return Optional.empty();
        }
        return em.createQuery("select p from Profesional p where p.matricula = :matricula", Profesional.class)
                .setParameter("matricula", matricula)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Profesional> listarPorEspecialidad(String especialidad) {
        return em.createQuery(
                        "select p from Profesional p where lower(p.especialidad) = :especialidad "
                                + "order by p.nombre", Profesional.class)
                .setParameter("especialidad", especialidad.toLowerCase())
                .getResultList();
    }

    @Override
    public List<String> listarEspecialidades() {
        return em.createQuery(
                        "select distinct p.especialidad from Profesional p "
                                + "where p.activo = true order by p.especialidad", String.class)
                .getResultList();
    }

    @Override
    public Profesional guardar(Profesional profesional) {
        em.persist(profesional);
        em.flush();
        return profesional;
    }

    @Override
    public Profesional actualizar(Profesional profesional) {
        return em.merge(profesional);
    }
}
