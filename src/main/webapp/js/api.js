/**
 * Cliente HTTP de la API de MediConecta.
 *
 * Todas las llamadas pasan por aca para que el manejo de errores, del token JWT
 * y de la sesion vencida este en un solo lugar.
 */

/**
 * Context root de la aplicacion, deducido de la URL de este mismo archivo.
 * Asi el front sigue funcionando aunque el WAR se despliegue con otro nombre.
 */
export const contexto = new URL('..', import.meta.url).pathname.replace(/\/$/, '');

const BASE = contexto + '/api';

/**
 * Donde se guarda el JWT. sessionStorage y no localStorage: el token muere al
 * cerrar la pestana. Todo dato del servidor se inserta con esc(), que es la
 * defensa contra un XSS que intentara leerlo.
 */
const CLAVE_TOKEN = 'mediconecta.token';

export function guardarToken(token) {
    try { sessionStorage.setItem(CLAVE_TOKEN, token); } catch (e) { /* sin almacenamiento */ }
}

export function leerToken() {
    try { return sessionStorage.getItem(CLAVE_TOKEN); } catch (e) { return null; }
}

export function borrarToken() {
    try { sessionStorage.removeItem(CLAVE_TOKEN); } catch (e) { /* sin almacenamiento */ }
}

/** Error con el mensaje que devolvio el servidor y su codigo HTTP. */
export class ErrorApi extends Error {
    constructor(mensaje, codigo) {
        super(mensaje);
        this.codigo = codigo;
    }
}

/**
 * Clave para la cabecera Idempotency-Key: un UUID v4. crypto.randomUUID solo
 * existe en contextos seguros (https o localhost); si no esta, se arma el UUID
 * con crypto.getRandomValues, que existe en todos lados.
 */
export function nuevaClaveIdempotencia() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        return window.crypto.randomUUID();
    }
    const b = window.crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
    return h.slice(0, 8) + '-' + h.slice(8, 12) + '-' + h.slice(12, 16) + '-' + h.slice(16, 20) + '-' + h.slice(20);
}

async function pedir(metodo, ruta, cuerpo, cabecerasExtra) {
    const opciones = {
        method: metodo,
        headers: Object.assign({
            'Accept': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        }, cabecerasExtra || {}),
        // La cookie de sesion sigue viajando: la necesita la conversacion del
        // asistente @Stateful. La identidad, en cambio, va en el token.
        credentials: 'same-origin'
    };

    const token = leerToken();
    if (token) {
        opciones.headers['Authorization'] = 'Bearer ' + token;
    }

    // Los recursos declaran @Consumes(APPLICATION_JSON), asi que incluso las
    // operaciones sin cuerpo (confirmar, cancelar, volver) tienen que declarar
    // el tipo de contenido para que JAX-RS las haga coincidir.
    if (metodo !== 'GET') {
        opciones.headers['Content-Type'] = 'application/json';
        if (cuerpo !== undefined) {
            opciones.body = JSON.stringify(cuerpo);
        }
    }

    let respuesta;
    try {
        respuesta = await fetch(BASE + ruta, opciones);
    } catch (fallo) {
        throw new ErrorApi('No se pudo contactar al servidor.', 0);
    }

    if (respuesta.status === 401) {
        borrarToken();
        irALogin();
        throw new ErrorApi('Su sesion vencio. Vuelva a iniciar sesion.', 401);
    }

    if (respuesta.status === 204) {
        return null;
    }

    const texto = await respuesta.text();
    let datos = null;
    if (texto) {
        try {
            datos = JSON.parse(texto);
        } catch (fallo) {
            datos = null;
        }
    }

    // Los errores llegan en Problem Details (RFC 9457): "detail" es el texto
    // para la persona y "type" identifica el error.
    if (!respuesta.ok) {
        const mensaje = (datos && (datos.detail || datos.title))
            ? (datos.detail || datos.title)
            : 'El servidor respondio con un error (' + respuesta.status + ').';
        const error = new ErrorApi(mensaje, respuesta.status);
        error.tipo = datos && datos.type ? datos.type : null;
        throw error;
    }

    return datos;
}

/** Manda al usuario a la pantalla de login (sin token o con el token vencido). */
export function irALogin() {
    if (window.location.pathname.endsWith('/login.html')) {
        return;
    }
    window.location.href = contexto + '/login.html';
}

export const api = {
    // Sesion
    login: (usuario, clave) => pedir('POST', '/auth/login', { usuario, clave }),
    sesion: () => pedir('GET', '/sesion'),
    cerrarSesion: () => pedir('POST', '/sesion/logout'),

    // Tablero
    tablero: () => pedir('GET', '/tablero'),

    // Turnos
    turnos: () => pedir('GET', '/turnos'),
    turno: (id) => pedir('GET', '/turnos/' + encodeURIComponent(id)),
    crearTurno: (pedido, claveIdempotencia) => pedir('POST', '/turnos', pedido,
        claveIdempotencia ? { 'Idempotency-Key': claveIdempotencia } : undefined),
    notificacionesDeTurno: (id) => pedir('GET', '/turnos/' + encodeURIComponent(id) + '/notificaciones'),
    cancelarTurno: (id) => pedir('PUT', '/turnos/' + encodeURIComponent(id) + '/cancelar'),
    atenderTurno: (id) => pedir('PUT', '/turnos/' + encodeURIComponent(id) + '/atender'),
    archivarTurno: (id) => pedir('PUT', '/turnos/' + encodeURIComponent(id) + '/archivar'),

    // Pacientes
    pacientes: (texto) => pedir('GET', '/pacientes' + (texto ? '?q=' + encodeURIComponent(texto) : '')),
    paciente: (documento) => pedir('GET', '/pacientes/' + encodeURIComponent(documento)),
    miFicha: () => pedir('GET', '/pacientes/mi-ficha'),
    registrarPaciente: (paciente) => pedir('POST', '/pacientes', paciente),
    actualizarPaciente: (documento, datos) =>
        pedir('PUT', '/pacientes/' + encodeURIComponent(documento), datos),
    historial: (documento) =>
        pedir('GET', '/pacientes/' + encodeURIComponent(documento) + '/turnos'),

    // Profesionales
    profesionales: (especialidad) =>
        pedir('GET', '/profesionales' + (especialidad ? '?especialidad=' + encodeURIComponent(especialidad) : '')),
    profesional: (id) => pedir('GET', '/profesionales/' + encodeURIComponent(id)),
    especialidades: () => pedir('GET', '/profesionales/especialidades'),
    registrarProfesional: (profesional) => pedir('POST', '/profesionales', profesional),
    miPerfilProfesional: () => pedir('GET', '/profesionales/mi-perfil'),
    definirDisponibilidad: (id, franjas) =>
        pedir('PUT', '/profesionales/' + encodeURIComponent(id) + '/disponibilidad', franjas),
    horariosLibres: (id, fecha) =>
        pedir('GET', '/profesionales/' + encodeURIComponent(id) + '/horarios-libres?fecha=' + encodeURIComponent(fecha)),

    // Reserva guiada (componente @Stateful)
    reservaEstado: () => pedir('GET', '/reserva'),
    reservaIniciar: (documentoPaciente) => pedir('POST', '/reserva/iniciar', { documentoPaciente }),
    reservaEspecialidad: (especialidad) => pedir('PUT', '/reserva/especialidad', { especialidad }),
    reservaOrientacion: (mensajes) => pedir('POST', '/reserva/orientacion', { mensajes }),
    reservaProfesional: (profesionalId) => pedir('PUT', '/reserva/profesional', { profesionalId }),
    reservaHorario: (fechaHora) => pedir('PUT', '/reserva/horario', { fechaHora }),
    reservaVolver: () => pedir('PUT', '/reserva/volver'),
    reservaHorarios: (fecha) => pedir('GET', '/reserva/horarios?fecha=' + encodeURIComponent(fecha)),
    reservaConfirmar: () => pedir('POST', '/reserva/confirmar'),
    reservaCancelar: () => pedir('DELETE', '/reserva')
};
