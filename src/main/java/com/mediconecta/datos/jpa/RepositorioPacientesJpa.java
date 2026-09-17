package com.mediconecta.datos.jpa;

import com.mediconecta.datos.RepositorioPacientes;
import com.mediconecta.modelo.Paciente;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

/** Implementacion de {@link RepositorioPacientes} sobre JPA + PostgreSQL. */
@ApplicationScoped
public class RepositorioPacientesJpa implements RepositorioPacientes {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Override
    public List<Paciente> listarTodos() {
        return em.createQuery("select p from Paciente p order by p.nombre", Paciente.class)
                .getResultList();
    }

    @Override
    public Optional<Paciente> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(Paciente.class, id));
    }

    @Override
    public Optional<Paciente> buscarPorDocumento(String documento) {
        if (documento == null) {
            return Optional.empty();
        }
        return em.createQuery("select p from Paciente p where p.documento = :documento", Paciente.class)
                .setParameter("documento", documento)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Paciente> buscar(String texto) {
        String patron = "%" + texto.toLowerCase() + "%";
        return em.createQuery(
                        "select p from Paciente p "
                                + "where lower(p.nombre) like :patron or p.documento like :patron "
                                + "order by p.nombre", Paciente.class)
                .setParameter("patron", patron)
                .getResultList();
    }

    @Override
    public Paciente guardar(Paciente paciente) {
        em.persist(paciente);
        em.flush();
        return paciente;
    }

    @Override
    public Paciente actualizar(Paciente paciente) {
        return em.merge(paciente);
    }
}
