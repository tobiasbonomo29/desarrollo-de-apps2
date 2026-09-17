/**
 * Pantalla de login.
 *
 * No valida credenciales ni guarda roles: de eso se ocupa el contenedor cuando
 * el formulario hace POST a j_security_check. Aca solo se ajusta la URL del
 * formulario, se muestra el error del intento anterior y se evita que alguien
 * que ya inicio sesion vuelva a ver esta pantalla.
 */

import { contexto } from './api.js';

const COOKIE_ERROR = 'mediconecta_login_error';

function leerCookie(nombre) {
    return document.cookie.split(';')
        .map(c => c.trim())
        .find(c => c.startsWith(nombre + '='));
}

function borrarCookie(nombre) {
    document.cookie = nombre + '=; Max-Age=0; path=' + (contexto || '/');
}

function mostrarError() {
    if (!leerCookie(COOKIE_ERROR)) {
        return;
    }
    borrarCookie(COOKIE_ERROR);
    const mensaje = document.getElementById('mensaje');
    mensaje.hidden = false;
    mensaje.textContent = 'Usuario o contraseña incorrectos. Volvé a intentar.';
}

// Esta pantalla no consulta /api/sesion a proposito. Esa llamada daria 401
// cuando todavia no hay sesion, y el contenedor guardaria esa URL como destino
// posterior al login: el usuario terminaria viendo un JSON en vez del tablero.
// De quien ya inicio sesion se encarga ServletLogin, que lo desvia al tablero
// antes de llegar aca.

// El formulario se arma desde el context root real para que funcione tambien si
// el WAR se despliega con otro nombre.
document.getElementById('form-login').action = contexto + '/j_security_check';

mostrarError();
