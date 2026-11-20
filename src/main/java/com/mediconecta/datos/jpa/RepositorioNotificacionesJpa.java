package com.mediconecta.datos.jpa;

import com.mediconecta.datos.RepositorioNotificaciones;
import com.mediconecta.modelo.Notificacion;
import com.mediconecta.modelo.TipoNotificacion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

/** {@link RepositorioNotificaciones} sobre JPA + PostgreSQL. */
@ApplicationScoped
public class RepositorioNotificacionesJpa implements RepositorioNotificaciones {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Override
    public boolean existe(String eventoId, TipoNotificacion tipo) {
        return em.createQuery("select count(n) from Notificacion n where n.eventoId = :evento and n.tipo = :tipo",
                        Long.class)
                .setParameter("evento", eventoId)
                .setParameter("tipo", tipo)
                .getSingleResult() > 0;
    }

    @Override
    public Notificacion guardar(Notificacion notificacion) {
        em.persist(notificacion);
        em.flush();
        return notificacion;
    }

    @Override
    public List<Notificacion> listarPorTurno(Long turnoId) {
        return em.createQuery("select n from Notificacion n where n.turnoId = :turno order by n.creadaEn, n.id",
                        Notificacion.class)
                .setParameter("turno", turnoId)
                .getResultList();
    }
}
