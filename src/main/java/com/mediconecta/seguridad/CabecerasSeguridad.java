package com.mediconecta.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Agrega las cabeceras de seguridad HTTP a <b>todas</b> las respuestas del WAR:
 * pantallas, archivos estaticos, API REST y los sistemas simulados.
 *
 * <ul>
 *   <li>{@code Content-Security-Policy}: el navegador solo ejecuta scripts del
 *       propio sitio (ningun {@code <script>} en linea, ningun {@code eval}) y no
 *       deja que otra pagina nos meta en un {@code <iframe>}. Es la segunda
 *       linea de defensa contra un XSS, despues de {@code esc()} en el front.
 *       La API usa una politica mas cerrada todavia ({@code default-src 'none'}),
 *       porque solo devuelve JSON.</li>
 *   <li>{@code X-Content-Type-Options: nosniff}: el navegador respeta el
 *       {@code Content-Type} y no "adivina" que un JSON es HTML.</li>
 *   <li>{@code X-Frame-Options: DENY}: lo mismo que {@code frame-ancestors},
 *       para navegadores viejos (clickjacking).</li>
 *   <li>{@code Referrer-Policy: no-referrer}: las URLs internas (que llevan
 *       documentos de pacientes) no se filtran a otros sitios.</li>
 *   <li>{@code Permissions-Policy}: la aplicacion no usa camara, microfono ni
 *       ubicacion; se apagan para que ningun script los pueda pedir.</li>
 *   <li>{@code Cross-Origin-Opener-Policy}, {@code Cross-Origin-Resource-Policy}
 *       y {@code Cross-Origin-Embedder-Policy}: aislan la pagina y sus recursos
 *       de otros origenes.</li>
 *   <li>{@code Strict-Transport-Security}: solo cuando la peticion llego por
 *       HTTPS (directo o detras de un proxy que lo informa), porque mandarlo por
 *       HTTP no tiene efecto.</li>
 *   <li>{@code Cache-Control: no-store} en la API: los datos de pacientes no
 *       quedan guardados en la cache del navegador ni de un proxy.</li>
 * </ul>
 *
 * <p>Las respuestas 401 que escribe {@link MecanismoAutenticacionJwt} salen
 * antes de llegar a este filtro, por eso ese mecanismo llama tambien a
 * {@link #aplicar}.</p>
 */
@WebFilter(urlPatterns = "/*", asyncSupported = true)
public class CabecerasSeguridad extends HttpFilter {

    private static final long serialVersionUID = 1L;

    /** Politica para las pantallas: todo del propio sitio (la tipografia tambien se sirve desde aca). */
    static final String CSP_PANTALLAS = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "font-src 'self'",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /**
     * Politica para la documentacion interactiva de la API (Swagger UI), que se
     * sirve desde el CDN de jsDelivr y aplica estilos en linea.
     */
    static final String CSP_DOCUMENTACION = String.join("; ",
            "default-src 'self'",
            "script-src 'self' https://cdn.jsdelivr.net",
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net",
            "img-src 'self' data: https://cdn.jsdelivr.net",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /** Politica para respuestas que no son paginas (JSON, XML de SOAP). */
    static final String CSP_API = "default-src 'none'; frame-ancestors 'none'";

    static final String HSTS = "max-age=31536000; includeSubDomains";

    @Override
    protected void doFilter(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws IOException, ServletException {
        aplicar(peticion, respuesta);
        cadena.doFilter(peticion, respuesta);
    }

    /** Escribe las cabeceras en la respuesta segun el tipo de recurso pedido. */
    public static void aplicar(HttpServletRequest peticion, HttpServletResponse respuesta) {
        String ruta = rutaDentroDelWar(peticion);

        respuesta.setHeader("X-Content-Type-Options", "nosniff");
        respuesta.setHeader("X-Frame-Options", "DENY");
        respuesta.setHeader("Referrer-Policy", "no-referrer");
        respuesta.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        respuesta.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        respuesta.setHeader("Cross-Origin-Resource-Policy", "same-origin");

        if (esServicio(ruta)) {
            respuesta.setHeader("Content-Security-Policy", CSP_API);
            respuesta.setHeader("Cache-Control", "no-store");
        } else if (ruta.startsWith("/api-docs")) {
            respuesta.setHeader("Content-Security-Policy", CSP_DOCUMENTACION);
            respuesta.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
        } else {
            respuesta.setHeader("Content-Security-Policy", CSP_PANTALLAS);
            respuesta.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
        }

        if (llegoPorHttps(peticion)) {
            respuesta.setHeader("Strict-Transport-Security", HSTS);
        }
    }

    /** API REST, partner REST simulado y servicio SOAP simulado. */
    static boolean esServicio(String ruta) {
        return ruta.startsWith("/api/")
                || ruta.equals("/api")
                || ruta.startsWith("/partner/")
                || ruta.startsWith("/ServicioObraSocialLegadoImpl");
    }

    static boolean llegoPorHttps(HttpServletRequest peticion) {
        return peticion.isSecure() || "https".equalsIgnoreCase(peticion.getHeader("X-Forwarded-Proto"));
    }

    private static String rutaDentroDelWar(HttpServletRequest peticion) {
        String uri = peticion.getRequestURI();
        String contexto = peticion.getContextPath();
        if (uri == null) {
            return "/";
        }
        if (contexto != null && !contexto.isEmpty() && uri.startsWith(contexto)) {
            uri = uri.substring(contexto.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }
}
