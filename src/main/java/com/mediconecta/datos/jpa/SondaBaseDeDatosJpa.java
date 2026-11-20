package com.mediconecta.datos.jpa;

import com.mediconecta.datos.SondaBaseDeDatos;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Ejecuta {@code SELECT 1} contra PostgreSQL con un limite de 2 segundos.
 *
 * <p>Si la base no responde, el health check informa {@code DOWN} en lugar de
 * quedarse colgado esperando: un orquestador (Docker, Kubernetes) necesita
 * una respuesta rapida para decidir si saca la instancia de servicio.</p>
 */
@ApplicationScoped
public class SondaBaseDeDatosJpa implements SondaBaseDeDatos {

    static final int LIMITE_MS = 2000;

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Override
    public boolean responde() {
        try {
            Object resultado = em.createNativeQuery("SELECT 1")
                    .setHint("jakarta.persistence.query.timeout", LIMITE_MS)
                    .getSingleResult();
            return resultado != null;
        } catch (RuntimeException caida) {
            // PersistenceException, la excepcion del pool de conexiones, etc.:
            // cualquiera significa que la base no esta disponible.
            return false;
        }
    }
}
