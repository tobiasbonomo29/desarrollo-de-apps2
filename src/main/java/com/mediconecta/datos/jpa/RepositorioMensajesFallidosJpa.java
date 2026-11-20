package com.mediconecta.datos.jpa;

import com.mediconecta.datos.RepositorioMensajesFallidos;
import com.mediconecta.modelo.MensajeFallido;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

/** {@link RepositorioMensajesFallidos} sobre JPA + PostgreSQL. */
@ApplicationScoped
public class RepositorioMensajesFallidosJpa implements RepositorioMensajesFallidos {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Override
    public boolean existe(String mensajeId) {
        return em.createQuery("select count(m) from MensajeFallido m where m.mensajeId = :id", Long.class)
                .setParameter("id", mensajeId)
                .getSingleResult() > 0;
    }

    @Override
    public MensajeFallido guardar(MensajeFallido mensaje) {
        em.persist(mensaje);
        em.flush();
        return mensaje;
    }

    @Override
    public List<MensajeFallido> listarRecientes(int maximo) {
        return em.createQuery("select m from MensajeFallido m order by m.recibidoEn desc, m.id desc",
                        MensajeFallido.class)
                .setMaxResults(maximo)
                .getResultList();
    }
}
