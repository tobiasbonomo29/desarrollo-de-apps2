package com.mediconecta.presentacion;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Pagina de login del mecanismo FORM del contenedor.
 *
 * <p>Cuando alguien pide un recurso protegido sin estar autenticado, Undertow
 * <b>no</b> responde el recurso: deriva la peticion a esta ruta, que es la que
 * {@code web.xml} declara como {@code form-login-page}. Existe este servlet en
 * lugar de apuntar directo a {@code login.html} por un motivo: la misma
 * aplicacion la usan un navegador y un cliente JSON, y cada uno necesita una
 * respuesta distinta.</p>
 *
 * <ul>
 *   <li>Si el cliente pidio JSON (nuestro {@code fetch} manda
 *       {@code X-Requested-With}, y curl manda {@code Accept: application/json}),
 *       responde <b>401</b> con el mismo cuerpo de error que el resto de la API.
 *       Asi la interfaz sabe que la sesion vencio y puede llevar al login sin
 *       intentar interpretar HTML como si fuera JSON.</li>
 *   <li>Si es un navegador navegando, muestra la pantalla de login del
 *       prototipo.</li>
 * </ul>
 *
 * <p>El servlet no valida credenciales: de eso se ocupa el contenedor cuando el
 * formulario hace POST a {@code j_security_check}.</p>
 */
@WebServlet(name = "ServletLogin", urlPatterns = {"/login", "/login-error"})
public class ServletLogin extends HttpServlet {

    private static final long serialVersionUID = 1L;

    static final String COOKIE_ERROR = "mediconecta_login_error";

    @Override
    protected void doGet(HttpServletRequest peticion, HttpServletResponse respuesta)
            throws ServletException, IOException {
        atender(peticion, respuesta);
    }

    @Override
    protected void doPost(HttpServletRequest peticion, HttpServletResponse respuesta)
            throws ServletException, IOException {
        atender(peticion, respuesta);
    }

    private void atender(HttpServletRequest peticion, HttpServletResponse respuesta)
            throws ServletException, IOException {

        boolean falloElLogin = peticion.getServletPath().endsWith("login-error");

        // Si ya hay una sesion abierta no tiene sentido mostrar el formulario.
        if (!falloElLogin && peticion.getUserPrincipal() != null && !esClienteJson(peticion)) {
            respuesta.sendRedirect(peticion.getContextPath() + "/app/index.html");
            return;
        }

        if (esClienteJson(peticion)) {
            respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            respuesta.setContentType("application/json;charset=UTF-8");
            respuesta.getWriter().write(falloElLogin
                    ? "{\"error\":\"Usuario o contrasena incorrectos.\",\"codigo\":401}"
                    : "{\"error\":\"Debe iniciar sesion para usar esta operacion.\",\"codigo\":401}");
            return;
        }

        if (falloElLogin) {
            // La pagina de login es estatica y el navegador no cambia de URL
            // durante el desvio, asi que la unica forma de avisarle del error
            // es dejar una marca que su JavaScript pueda leer y borrar.
            Cookie aviso = new Cookie(COOKIE_ERROR, "1");
            aviso.setPath(peticion.getContextPath().isEmpty() ? "/" : peticion.getContextPath());
            aviso.setMaxAge(30);
            respuesta.addCookie(aviso);
        }

        // Se redirige en lugar de hacer forward. Con un forward la barra del
        // navegador seguiria mostrando la URL protegida y las rutas relativas de
        // login.html (css/ y js/) se resolverian mal. Redirigiendo, la pagina se
        // carga desde su propia URL y todo resuelve bien. El contenedor ya
        // guardo en la sesion la URL que el usuario queria, asi que despues del
        // login lo lleva igual a destino.
        respuesta.sendRedirect(peticion.getContextPath() + "/login.html");
    }

    private boolean esClienteJson(HttpServletRequest peticion) {
        if ("XMLHttpRequest".equalsIgnoreCase(peticion.getHeader("X-Requested-With"))) {
            return true;
        }
        String acepta = peticion.getHeader("Accept");
        return acepta != null && acepta.contains("application/json") && !acepta.contains("text/html");
    }
}
