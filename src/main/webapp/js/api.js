/**
 * Cliente HTTP de la API de MediConecta.
 *
 * Todas las llamadas pasan por aca para que el manejo de errores y de la sesion
 * vencida este en un solo lugar.
 */

/**
 * Context root de la aplicacion, deducido de la URL de este mismo archivo.
 * Asi el front sigue funcionando aunque el WAR se despliegue con otro nombre.
 */
export const contexto = new URL('..', import.meta.url).pathname.replace(/\/$/, '');

const BASE = contexto + '/api';

/** Error con el mensaje que devolvio el servidor y su codigo HTTP. */
export class ErrorApi extends Error {
    constructor(mensaje, codigo) {
        super(mensaje);
        this.codigo = codigo;
    }
}

async function pedir(metodo, ruta, cuerpo) {
    const opciones = {
        method: metodo,
        headers: {
            'Accept': 'application/json',
            // Le avisa al servlet de login que somos un cliente JSON y que ante
            // una sesion vencida queremos un 401 y no la pagina de login.
            'X-Requested-With': 'XMLHttpRequest'
        },
        credentials: 'same-origin'
    };

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

    if (!respuesta.ok) {
        const mensaje = (datos && datos.error)
            ? datos.error
            : 'El servidor respondio con un error (' + respuesta.status + ').';
        throw new ErrorApi(mensaje, respuesta.status);
    }

    return datos;
}

/**
 * Manda al usuario a iniciar sesion cuando la suya vencio.
 *
 * Apunta al tablero y no a login.html a proposito: el tablero esta protegido,
 * asi que el contenedor lo intercepta, guarda esa URL como destino, muestra el
 * login y, una vez validadas las credenciales, devuelve al usuario justo ahi.
 * Si en cambio fueramos directo a login.html, el contenedor recordaria como
 * destino la ultima URL que dio 401, que suele ser una llamada a la API, y el
 * usuario terminaria viendo un JSON despues de iniciar sesion.
 */
export function irALogin() {
    if (window.location.pathname.endsWith('/login.html')) {
        return;
    }
    window.location.href = contexto + '/app/index.html';
}

export const api = {
    // Sesion
    sesion: () => pedir('GET', '/sesion'),
    cerrarSesion: () => pedir('POST', '/sesion/logout'),

    // Tablero
    tablero: () => pedir('GET', '/tablero'),

    // Turnos
    turnos: () => pedir('GET', '/turnos'),
    turno: (id) => pedir('GET', '/turnos/' + encodeURIComponent(id)),
    crearTurno: (pedido) => pedir('POST', '/turnos', pedido),
    cancelarTurno: (id) => pedir('PUT', '/turnos/' + encodeURIComponent(id) + '/cancelar'),

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
    definirDisponibilidad: (id, franjas) =>
        pedir('PUT', '/profesionales/' + encodeURIComponent(id) + '/disponibilidad', franjas),
    horariosLibres: (id, fecha) =>
        pedir('GET', '/profesionales/' + encodeURIComponent(id) + '/horarios-libres?fecha=' + encodeURIComponent(fecha)),

    // Reserva guiada (componente @Stateful)
    reservaEstado: () => pedir('GET', '/reserva'),
    reservaIniciar: (documentoPaciente) => pedir('POST', '/reserva/iniciar', { documentoPaciente }),
    reservaEspecialidad: (especialidad) => pedir('PUT', '/reserva/especialidad', { especialidad }),
    reservaProfesional: (profesionalId) => pedir('PUT', '/reserva/profesional', { profesionalId }),
    reservaHorario: (fechaHora) => pedir('PUT', '/reserva/horario', { fechaHora }),
    reservaVolver: () => pedir('PUT', '/reserva/volver'),
    reservaHorarios: (fecha) => pedir('GET', '/reserva/horarios?fecha=' + encodeURIComponent(fecha)),
    reservaConfirmar: () => pedir('POST', '/reserva/confirmar'),
    reservaCancelar: () => pedir('DELETE', '/reserva')
};
