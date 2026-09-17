/**
 * Arma la estructura comun de todas las pantallas: barra lateral, barra
 * superior y navegacion inferior para mobile.
 *
 * El menu se dibuja segun los roles que devuelve /api/sesion. Importante para la
 * defensa: esto es comodidad visual, NO seguridad. Aunque alguien forzara la URL
 * de una pantalla que no le corresponde, el contenedor le negaria igual cada
 * operacion porque la autorizacion real esta en @RolesAllowed, en el servidor.
 */

import { api, contexto, irALogin } from './api.js';
import { esc, iniciales, avisoError } from './ui.js';

const ICONOS = {
    inicio: 'M3 10.5 12 3l9 7.5M5.5 9.5V20h13V9.5',
    turnos: 'M7 3v3M17 3v3M4 8.5h16M5 5.5h14a1 1 0 0 1 1 1V20a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1Z',
    pacientes: 'M16 20v-1.5a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4V20M9.5 10.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM21 20v-1.5a4 4 0 0 0-3-3.87',
    profesionales: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM5 21v-1a5 5 0 0 1 5-5h4a5 5 0 0 1 5 5v1',
    reserva: 'M9 12.5l2 2 4.5-4.5M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z'
};

const MENU = [
    { id: 'inicio', texto: 'Inicio', archivo: 'index.html', roles: null },
    { id: 'turnos', texto: 'Turnos', archivo: 'turnos.html', roles: ['RECEPCION', 'MEDICO', 'ADMIN'] },
    { id: 'pacientes', texto: 'Pacientes', archivo: 'pacientes.html', roles: ['RECEPCION', 'MEDICO', 'ADMIN'] },
    { id: 'profesionales', texto: 'Profesionales', archivo: 'profesionales.html', roles: null },
    { id: 'reserva', texto: 'Reserva', archivo: 'reserva.html', roles: ['PACIENTE', 'RECEPCION', 'ADMIN'] }
];

/** Devuelve true si la sesion tiene alguno de los roles indicados. */
export function tieneRol(sesion, ...roles) {
    return roles.some(rol => sesion.roles.includes(rol));
}

function icono(id) {
    return '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"'
        + ' stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
        + '<path d="' + ICONOS[id] + '"/></svg>';
}

function logo(color) {
    return '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="' + color + '"'
        + ' stroke-width="1.8" stroke-linecap="round" aria-hidden="true">'
        + '<path d="M12 4v16M4 12h16"/></svg>';
}

function visibles(sesion) {
    return MENU.filter(item => item.roles === null || tieneRol(sesion, ...item.roles));
}

function dibujarSidebar(sesion, pagina) {
    const sidebar = document.getElementById('sidebar');
    if (!sidebar) {
        return;
    }

    const enlaces = visibles(sesion).map(item => {
        const activo = item.id === pagina ? ' sidebar__link--activo' : '';
        return '<a class="sidebar__link' + activo + '" href="' + contexto + '/app/' + item.archivo + '">'
            + icono(item.id) + '<span>' + esc(item.texto) + '</span></a>';
    }).join('');

    sidebar.innerHTML =
        '<div class="sidebar__logo">' + logo('#FFFFFF') + '<span>MediConecta</span></div>'
        + '<nav class="sidebar__nav" aria-label="Secciones">' + enlaces + '</nav>'
        + '<div class="sidebar__pie">Entrega 1 &middot; Desarrollo de Aplicaciones II</div>';
}

function dibujarNavInferior(sesion, pagina) {
    const nav = document.getElementById('nav-inferior');
    if (!nav) {
        return;
    }
    nav.setAttribute('aria-label', 'Secciones');
    nav.innerHTML = visibles(sesion).map(item => {
        const activo = item.id === pagina ? ' class="activo"' : '';
        return '<a' + activo + ' href="' + contexto + '/app/' + item.archivo + '">'
            + '<span class="nav-inferior__icono">' + icono(item.id) + '</span>'
            + esc(item.texto) + '</a>';
    }).join('');
}

function dibujarTopbar(sesion) {
    const topbar = document.getElementById('topbar');
    if (!topbar) {
        return;
    }

    const titulo = document.body.dataset.titulo || 'MediConecta';
    const subtitulo = document.body.dataset.subtitulo || '';

    topbar.innerHTML =
        '<div class="topbar__titulo">'
        + '<h1>' + esc(titulo) + '</h1>'
        + (subtitulo ? '<p>' + esc(subtitulo) + '</p>' : '')
        + '</div>'
        + '<div class="topbar__usuario">'
        + '<div class="usuario__datos">'
        + '<div class="usuario__nombre">' + esc(sesion.usuario) + '</div>'
        + '<div class="usuario__rol">' + esc(sesion.roles.join(' / ')) + '</div>'
        + '</div>'
        + '<div class="avatar" aria-hidden="true">' + esc(iniciales(sesion.usuario)) + '</div>'
        + '<button type="button" class="boton boton--secundario boton--chico" id="boton-salir">Salir</button>'
        + '</div>';

    document.getElementById('boton-salir').addEventListener('click', async () => {
        try {
            await api.cerrarSesion();
        } catch (error) {
            // Aunque falle el logout del servidor, conviene sacar al usuario.
        }
        irALogin();
    });
}

/**
 * Punto de entrada de toda pantalla protegida.
 * Devuelve la sesion para que cada pantalla adapte su contenido al rol.
 */
export async function iniciarLayout() {
    const pagina = document.body.dataset.pagina;
    let sesion;

    try {
        sesion = await api.sesion();
    } catch (error) {
        irALogin();
        throw error;
    }

    if (!sesion || !sesion.autenticado) {
        irALogin();
        throw new Error('sin sesion');
    }

    dibujarSidebar(sesion, pagina);
    dibujarNavInferior(sesion, pagina);
    dibujarTopbar(sesion);
    return sesion;
}

/** Atajo para reportar un error de API con un aviso flotante. */
export function reportar(error) {
    avisoError(error.message || 'Ocurrio un error inesperado.');
}
