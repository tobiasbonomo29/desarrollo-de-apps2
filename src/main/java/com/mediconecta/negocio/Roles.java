package com.mediconecta.negocio;

/**
 * Nombres de los roles de seguridad de MediConecta.
 *
 * <p>Son constantes de compilacion porque {@code @RolesAllowed} solo admite
 * literales. Tenerlas en un unico lugar evita que un rol mal escrito en una
 * anotacion pase desapercibido: los mismos nombres estan declarados en
 * {@code web.xml} y se asignan a los usuarios en WildFly.</p>
 */
public final class Roles {

    public static final String PACIENTE = "PACIENTE";

    public static final String RECEPCION = "RECEPCION";

    public static final String MEDICO = "MEDICO";

    public static final String ADMIN = "ADMIN";

    private Roles() {
    }
}
