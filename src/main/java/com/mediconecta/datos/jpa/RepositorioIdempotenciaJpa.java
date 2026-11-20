package com.mediconecta.datos.jpa;

import com.mediconecta.datos.RepositorioIdempotencia;
import com.mediconecta.modelo.ClaveIdempotencia;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;

import java.time.LocalDateTime;
import java.util.Optional;

/** {@link RepositorioIdempotencia} sobre JPA + PostgreSQL. */
@ApplicationScoped
public class RepositorioIdempotenciaJpa implements RepositorioIdempotencia {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Override
    public Optional<ClaveIdempotencia> buscar(String usuario, String clave) {
        return em.createQuery("select c from ClaveIdempotencia c where c.usuario = :usuario and c.clave = :clave",
                        ClaveIdempotencia.class)
                .setParameter("usuario", usuario)
                .setParameter("clave", clave)
                .getResultStream()
                .findFirst();
    }

    /**
     * El {@code flush} fuerza el INSERT en este momento: si otra transaccion ya
     * registro la misma clave, PostgreSQL espera a que termine y, si confirmo,
     * rechaza este INSERT por la restriccion unica. Esa violacion es la senal de
     * "pedido repetido en curso"; la transaccion queda marcada para deshacerse.
     */
    @Override
    public boolean registrar(ClaveIdempotencia clave) {
        try {
            em.persist(clave);
            em.flush();
            return true;
        } catch (PersistenceException duplicada) {
            return false;
        }
    }

    @Override
    public void eliminar(ClaveIdempotencia clave) {
        ClaveIdempotencia administrada = em.contains(clave) ? clave : em.find(ClaveIdempotencia.class, clave.getId());
        if (administrada != null) {
            em.remove(administrada);
            em.flush();
        }
    }

    @Override
    public int eliminarAnterioresA(LocalDateTime limite) {
        return em.createQuery("delete from ClaveIdempotencia c where c.creadaEn < :limite")
                .setParameter("limite", limite)
                .executeUpdate();
    }
}
