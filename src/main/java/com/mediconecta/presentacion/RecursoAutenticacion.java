package com.mediconecta.presentacion;

import com.mediconecta.modelo.Usuario;
import com.mediconecta.negocio.ServicioAutenticacion;
import com.mediconecta.seguridad.ServicioJwt;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

/**
 * Inicio de sesion: cambia usuario y contrasena por un JWT.
 *
 * <p>Es publico en {@code web.xml} (hace falta justamente para obtener el
 * token). La verificacion de la contrasena la hace el negocio
 * ({@link ServicioAutenticacion}); este recurso solo emite el token con los
 * roles que devolvio el negocio.</p>
 *
 * <pre>
 * POST /api/auth/login  {"usuario":"recepcion","clave":"Demo.2026"}
 * 200 {"token":"eyJ...","tipo":"Bearer","expiraEnSegundos":7200,
 *      "usuario":"recepcion","roles":["RECEPCION"]}
 * 401 {"error":"Usuario o contrasena incorrectos.","codigo":401}
 * </pre>
 */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoAutenticacion {

    @Inject
    private ServicioAutenticacion servicioAutenticacion;

    @Inject
    private ServicioJwt servicioJwt;

    /** Para la IP de origen: el limite de intentos fallidos tambien cuenta por IP. */
    @Context
    private HttpServletRequest peticion;

    @POST
    @Path("/login")
    public RespuestaLogin login(PedidoLogin pedido) {
        Usuario usuario = servicioAutenticacion.autenticar(
                pedido == null ? null : pedido.getUsuario(),
                pedido == null ? null : pedido.getClave(),
                peticion == null ? null : peticion.getRemoteAddr());

        List<String> roles = new ArrayList<>(usuario.getRoles());

        RespuestaLogin respuesta = new RespuestaLogin();
        respuesta.setToken(servicioJwt.emitir(usuario.getUsuario(), roles));
        respuesta.setTipo("Bearer");
        respuesta.setExpiraEnSegundos(servicioJwt.validez().toSeconds());
        respuesta.setUsuario(usuario.getUsuario());
        respuesta.setRoles(roles);
        return respuesta;
    }

    /** Cuerpo de entrada del login. */
    public static class PedidoLogin {

        private String usuario;

        private String clave;

        public String getUsuario() {
            return usuario;
        }

        public void setUsuario(String usuario) {
            this.usuario = usuario;
        }

        public String getClave() {
            return clave;
        }

        public void setClave(String clave) {
            this.clave = clave;
        }
    }

    /** Cuerpo de salida del login. */
    public static class RespuestaLogin {

        private String token;

        private String tipo;

        private long expiraEnSegundos;

        private String usuario;

        private List<String> roles;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getTipo() {
            return tipo;
        }

        public void setTipo(String tipo) {
            this.tipo = tipo;
        }

        public long getExpiraEnSegundos() {
            return expiraEnSegundos;
        }

        public void setExpiraEnSegundos(long expiraEnSegundos) {
            this.expiraEnSegundos = expiraEnSegundos;
        }

        public String getUsuario() {
            return usuario;
        }

        public void setUsuario(String usuario) {
            this.usuario = usuario;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}
