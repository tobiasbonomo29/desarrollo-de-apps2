/**
 * Utilidades de interfaz compartidas por todas las pantallas:
 * escapado de texto, formatos de fecha, estados de carga y avisos.
 */

/**
 * Escapa texto antes de insertarlo en HTML.
 *
 * Todo dato que venga del servidor pasa por aca: un paciente podria llamarse
 * literalmente algo con etiquetas HTML adentro y no queremos que el navegador lo
 * interprete.
 */
export function esc(valor) {
    if (valor === null || valor === undefined) {
        return '';
    }
    return String(valor)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

const DIAS = ['domingo', 'lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado'];
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

export const DIAS_SEMANA = [
    { valor: 'MONDAY', texto: 'Lunes' },
    { valor: 'TUESDAY', texto: 'Martes' },
    { valor: 'WEDNESDAY', texto: 'Miercoles' },
    { valor: 'THURSDAY', texto: 'Jueves' },
    { valor: 'FRIDAY', texto: 'Viernes' },
    { valor: 'SATURDAY', texto: 'Sabado' },
    { valor: 'SUNDAY', texto: 'Domingo' }
];

export function nombreDeDia(valor) {
    const dia = DIAS_SEMANA.find(d => d.valor === valor);
    return dia ? dia.texto : valor;
}

/** "2026-09-18T09:30" -> "09:30" */
export function hora(fechaHora) {
    return fechaHora ? fechaHora.substring(11, 16) : '';
}

/** "2026-09-18T09:30" -> "18/09/2026" */
export function fecha(fechaHora) {
    if (!fechaHora) {
        return '';
    }
    const [anio, mes, dia] = fechaHora.substring(0, 10).split('-');
    return dia + '/' + mes + '/' + anio;
}

/** "2026-09-18T09:30" -> "viernes 18 sep, 09:30" */
export function fechaLarga(fechaHora) {
    if (!fechaHora) {
        return '';
    }
    const momento = new Date(fechaHora);
    if (isNaN(momento)) {
        return fechaHora;
    }
    return DIAS[momento.getDay()] + ' ' + momento.getDate() + ' '
        + MESES[momento.getMonth()] + ', ' + hora(fechaHora);
}

/** Fecha de hoy en formato aaaa-MM-dd, lista para un <input type="date">. */
export function hoyIso() {
    const hoy = new Date();
    const mes = String(hoy.getMonth() + 1).padStart(2, '0');
    const dia = String(hoy.getDate()).padStart(2, '0');
    return hoy.getFullYear() + '-' + mes + '-' + dia;
}

export function etiquetaEstado(estado) {
    const clases = {
        PROGRAMADO: 'etiqueta--programado',
        ATENDIDO: 'etiqueta--atendido',
        CANCELADO: 'etiqueta--cancelado'
    };
    const textos = {
        PROGRAMADO: 'Programado',
        ATENDIDO: 'Atendido',
        CANCELADO: 'Cancelado'
    };
    const clase = clases[estado] || '';
    return '<span class="etiqueta ' + clase + '">' + esc(textos[estado] || estado) + '</span>';
}

export function iniciales(nombre) {
    if (!nombre) {
        return '?';
    }
    return nombre.trim().split(/\s+/).slice(0, 2).map(p => p[0].toUpperCase()).join('');
}

/* ------------------------- Estados de una seccion ------------------------- */

export function mostrarCargando(elemento, texto) {
    elemento.innerHTML = '<div class="cargando">' + esc(texto || 'Cargando...') + '</div>';
}

export function mostrarVacio(elemento, titulo, detalle, botonHtml) {
    elemento.innerHTML = '<div class="estado">'
        + '<h3>' + esc(titulo) + '</h3>'
        + '<p>' + esc(detalle || '') + '</p>'
        + (botonHtml || '')
        + '</div>';
}

export function mostrarError(elemento, mensaje) {
    elemento.innerHTML = '<div class="estado">'
        + '<h3>No se pudo cargar la informacion</h3>'
        + '<p>' + esc(mensaje) + '</p>'
        + '</div>';
}

/* ------------------------------- Avisos ---------------------------------- */

export function aviso(mensaje, tipo) {
    const contenedor = document.getElementById('avisos');
    if (!contenedor) {
        return;
    }
    const caja = document.createElement('div');
    caja.className = 'toast' + (tipo ? ' toast--' + tipo : '');
    caja.setAttribute('role', 'status');
    caja.textContent = mensaje;
    contenedor.appendChild(caja);
    setTimeout(() => caja.remove(), 5000);
}

export const avisoExito = (mensaje) => aviso(mensaje, 'exito');
export const avisoError = (mensaje) => aviso(mensaje, 'error');

/** Mensaje inline dentro de un formulario. */
export function mensajeEnCaja(elemento, texto, tipo) {
    if (!texto) {
        elemento.hidden = true;
        elemento.textContent = '';
        return;
    }
    elemento.hidden = false;
    elemento.className = 'aviso aviso--' + (tipo || 'info');
    elemento.textContent = texto;
}
