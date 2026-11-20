package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioUsuarios;
import com.mediconecta.modelo.Usuario;
import com.mediconecta.seguridad.HashDeClaves;
import com.mediconecta.seguridad.LimitadorDeFrecuencia;

import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Verifica credenciales. EJB <b>stateless</b>.
 *
 * <p>Es el unico metodo de negocio sin rol exigido ({@code @PermitAll}): lo
 * invoca alguien que todavia no inicio sesion. No emite el token: eso es un
 * detalle del transporte HTTP y lo hace la presentacion con
 * {@code seguridad.ServicioJwt}. Asi el negocio sigue sin saber nada de HTTP ni
 * de JWT.</p>
 *
 * <p><b>Fuerza bruta.</b> Despues de {@value #MAXIMO_FALLOS_POR_USUARIO}
 * intentos fallidos para un mismo usuario, o {@value #MAXIMO_FALLOS_POR_ORIGEN}
 * desde una misma IP, en {@link #VENTANA}, se rechaza con
 * {@link DemasiadasSolicitudesException} ({@code 429}) sin mirar la contrasena,
 * hasta que pase la ventana. Un login correcto borra los fallos del usuario.</p>
 */
@Stateless
public class ServicioAutenticacion {

    static final int MAXIMO_FALLOS_POR_USUARIO = 5;

    static final int MAXIMO_FALLOS_POR_ORIGEN = 30;

    static final Duration VENTANA = Duration.ofMinutes(5);

    private static final String HASH_FICTICIO = HashDeClaves.calcular("clave-ficticia-de-tiempo");

    @Inject
    private RepositorioUsuarios repositorio;

    @Inject
    private LimitadorDeFrecuencia limitador;

    @PermitAll
    public Usuario autenticar(String usuario, String clave) {
        return autenticar(usuario, clave, null);
    }

    /**
     * @param origen direccion IP del cliente (puede ser {@code null})
     * @return el usuario autenticado, con sus roles
     * @throws CredencialesInvalidasException si el usuario no existe, esta
     *                                        inactivo o la contrasena no coincide
     * @throws DemasiadasSolicitudesException si se superaron los intentos permitidos
     */
    @PermitAll
    public Usuario autenticar(String usuario, String clave, String origen) {
        String claveUsuario = "login:usuario:" + (usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT));
        String claveOrigen = origen == null ? null : "login:origen:" + origen;
        exigirNoBloqueado(claveUsuario, MAXIMO_FALLOS_POR_USUARIO);
        if (claveOrigen != null) {
            exigirNoBloqueado(claveOrigen, MAXIMO_FALLOS_POR_ORIGEN);
        }
        try {
            Usuario cuenta = verificar(usuario, clave);
            limitador.limpiar(claveUsuario);
            return cuenta;
        } catch (CredencialesInvalidasException fallo) {
            limitador.registrar(claveUsuario);
            if (claveOrigen != null) {
                limitador.registrar(claveOrigen);
            }
            throw fallo;
        }
    }

    private void exigirNoBloqueado(String clave, int maximo) {
        limitador.bloqueo(clave, maximo, VENTANA).ifPresent(espera -> {
            throw new DemasiadasSolicitudesException(
                    "Demasiados intentos fallidos de inicio de sesion. Espere unos minutos y vuelva a intentar.", espera);
        });
    }

    private Usuario verificar(String usuario, String clave) {
        if (usuario == null || usuario.isBlank() || clave == null || clave.isEmpty()) {
            throw new CredencialesInvalidasException();
        }
        Optional<Usuario> encontrado = repositorio.buscarPorUsuario(usuario.trim());
        if (encontrado.isEmpty()) {
            // Se calcula igual un hash para que el tiempo de respuesta no
            // delate si el usuario existe o no.
            HashDeClaves.coincide(clave, HASH_FICTICIO);
            throw new CredencialesInvalidasException();
        }
        Usuario cuenta = encontrado.get();
        if (!cuenta.isActivo() || !HashDeClaves.coincide(clave, cuenta.getHashClave())) {
            throw new CredencialesInvalidasException();
        }
        return cuenta;
    }
}
