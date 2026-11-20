package com.mediconecta.negocio;

import jakarta.ejb.SessionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.Set;

/**
 * Ayudas para probar los EJB como objetos Java comunes, sin contenedor.
 *
 * <p>Las dependencias se completan con las implementaciones en memoria de los
 * repositorios: es el mismo patron DAO que habilita el "plan B" sin base de
 * datos, usado aca para que los tests no necesiten PostgreSQL ni WildFly.</p>
 */
final class Inyeccion {

    private Inyeccion() {
    }

    /** Asigna un campo privado, como haria {@code @Inject} o {@code @Resource}. */
    static void campo(Object destino, String nombre, Object valor) {
        try {
            Field campo = destino.getClass().getDeclaredField(nombre);
            campo.setAccessible(true);
            campo.set(destino, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo inyectar " + nombre, e);
        }
    }

    /**
     * Un {@link SessionContext} que solo responde quien es el llamador y que
     * roles tiene: lo unico que usan los componentes para el ownership.
     */
    static SessionContext llamador(String usuario, String... roles) {
        Set<String> conjunto = Set.of(roles);
        Principal principal = () -> usuario;
        return (SessionContext) Proxy.newProxyInstance(
                Inyeccion.class.getClassLoader(),
                new Class<?>[]{SessionContext.class},
                (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "getCallerPrincipal" -> principal;
                    case "isCallerInRole" -> conjunto.contains((String) args[0]);
                    default -> throw new UnsupportedOperationException(metodo.getName());
                });
    }
}
