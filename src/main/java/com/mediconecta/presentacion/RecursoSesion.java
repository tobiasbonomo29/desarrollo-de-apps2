package com.mediconecta.presentacion;

import com.mediconecta.negocio.Roles;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Quien soy y cerrar sesion.
 *
 * <p>La interfaz consulta este recurso al cargar para saber que opciones mostrar.
 * Es importante entender que esto <b>no</b> es la seguridad: el usuario y los
 * roles salen del {@link SecurityContext} que armo el contenedor al autenticar,
 * y ocultar un boton en el navegador no autoriza nada. Quien autoriza de verdad
 * es {@code @RolesAllowed} sobre los metodos de negocio; si alguien llamara al
 * endpoint a mano igual recibiria un 403.</p>
 */
@Path("/sesion")
@Produces(MediaType.APPLICATION_JSON)
public class RecursoSesion {

    private static final String[] TODOS_LOS_ROLES = {
            Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN};

    @Context
    private SecurityContext seguridad;

    @Context
    private HttpServletRequest peticion;

    @GET
    public Sesion actual() {
        Sesion sesion = new Sesion();
        sesion.setAutenticado(seguridad.getUserPrincipal() != null);
        sesion.setUsuario(sesion.isAutenticado() ? seguridad.getUserPrincipal().getName() : null);

        List<String> roles = new ArrayList<>();
        for (String rol : TODOS_LOS_ROLES) {
            if (seguridad.isUserInRole(rol)) {
                roles.add(rol);
            }
        }
        sesion.setRoles(roles);
        return sesion;
    }

    @POST
    @Path("/logout")
    public Response salir() throws ServletException {
        peticion.logout();
        HttpSession sesion = peticion.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        return Response.noContent().build();
    }

    /** Datos de la sesion que consume la interfaz. */
    public static class Sesion {

        private boolean autenticado;

        private String usuario;

        private List<String> roles;

        public boolean isAutenticado() {
            return autenticado;
        }

        public void setAutenticado(boolean autenticado) {
            this.autenticado = autenticado;
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
