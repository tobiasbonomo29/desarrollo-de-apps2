/**
 * Detalle de un turno: cancelación y, para un turno cancelado, sacarlo de la
 * agenda sin perder el registro.
 *
 * Los botones se muestran solo cuando la persona puede usarlos. Igual, la
 * comprobación real la vuelve a hacer el servidor: si alguien llamara al
 * endpoint igual recibiría 403 o 400.
 */

import { api } from './api.js';
import { iniciarLayout, tieneRol, reportar } from './layout.js';
import { esc, etiquetaEstado, fechaLarga, mostrarCargando, mostrarError, avisoExito, mensajeEnCaja, confirmar } from './ui.js';

const id = new URLSearchParams(location.search).get('id');

function dato(titulo, valor) {
    return '<div><div class="dato__titulo">' + esc(titulo) + '</div>'
        + '<div class="dato__valor">' + esc(valor) + '</div></div>';
}

function dibujar(turno) {
    document.getElementById('titulo').textContent = 'Turno #' + turno.id;
    document.getElementById('subtitulo').textContent = fechaLarga(turno.fechaHora) + ' hs';
    document.getElementById('estado').innerHTML = etiquetaEstado(turno.estado);

    document.getElementById('detalle').innerHTML = '<div class="detalle-datos">'
        + dato('Paciente', turno.paciente.nombre)
        + dato('Documento', turno.paciente.documento)
        + dato('Cobertura', turno.paciente.cobertura || 'Sin cobertura registrada')
        + dato('Profesional', turno.profesional.nombre)
        + dato('Especialidad', turno.especialidad)
        + dato('Matrícula', turno.profesional.matricula)
        + dato('Fecha y hora', fechaLarga(turno.fechaHora))
        + dato('Motivo', turno.motivo || 'No se indicó motivo')
        + '</div>';
}

/** A qué pantalla vuelve "Volver" según lo que ese rol puede ver. */
function ajustarEnlaceVolver(sesion) {
    const enlace = document.getElementById('enlace-volver');
    if (tieneRol(sesion, 'RECEPCION', 'MEDICO', 'ADMIN')) {
        enlace.href = 'turnos.html';
        enlace.textContent = 'Volver a turnos';
    } else {
        enlace.href = 'mis-turnos.html';
        enlace.textContent = 'Volver a mis turnos';
    }
}

/** Si la persona puede cancelar este turno puntual (ownership incluido). */
async function puedeCancelarEste(sesion, turno) {
    if (turno.estado !== 'PROGRAMADO') {
        return false;
    }
    if (tieneRol(sesion, 'RECEPCION', 'ADMIN')) {
        return true;
    }
    if (tieneRol(sesion, 'MEDICO')) {
        try {
            const perfil = await api.miPerfilProfesional();
            return perfil.id === turno.profesional.id;
        } catch (error) {
            return false;
        }
    }
    if (tieneRol(sesion, 'PACIENTE')) {
        try {
            const ficha = await api.miFicha();
            return ficha.documento === turno.paciente.documento;
        } catch (error) {
            return false;
        }
    }
    return false;
}

/**
 * Si la persona puede marcar este turno como atendido: un MEDICO sobre su
 * propia agenda, o un ADMIN. Igual quien decide es el servidor (y el estado del
 * turno: desde otro estado que no sea PROGRAMADO responde 409).
 */
async function puedeAtenderEste(sesion, turno) {
    if (turno.estado !== 'PROGRAMADO') {
        return false;
    }
    if (tieneRol(sesion, 'ADMIN')) {
        return true;
    }
    if (tieneRol(sesion, 'MEDICO')) {
        try {
            const perfil = await api.miPerfilProfesional();
            return perfil.id === turno.profesional.id;
        } catch (error) {
            return false;
        }
    }
    return false;
}

function notaSegunEstado(turno) {
    if (turno.estado === 'ATENDIDO') {
        return 'Este turno ya fue atendido.';
    }
    if (turno.estado === 'CANCELADO') {
        return 'Este turno está cancelado.';
    }
    return 'Al cancelar, el horario vuelve a quedar disponible.';
}

const NOMBRE_AVISO = {
    CONFIRMACION: 'Confirmación',
    RECORDATORIO: 'Recordatorio',
    AVISO_PROFESIONAL: 'Aviso al profesional'
};

const NOMBRE_CANAL = { EMAIL: 'Email', SMS: 'SMS', AGENDA: 'Agenda' };

function dibujarAvisos(caja, avisos) {
    if (!avisos.length) {
        caja.innerHTML = '<p class="campo__ayuda">Todavía no hay avisos para este turno.</p>';
        return;
    }
    caja.innerHTML = '<ul class="avisos-turno">' + avisos.map((aviso) =>
        '<li class="avisos-turno__item">'
        + '<div class="fila-espaciada"><strong>' + esc(NOMBRE_AVISO[aviso.tipo] || aviso.tipo) + '</strong>'
        + '<span class="avisos-turno__canal">' + esc(NOMBRE_CANAL[aviso.canal] || aviso.canal)
        + ' · ' + esc(aviso.destinatario) + '</span></div>'
        + '<p class="avisos-turno__texto">' + esc(aviso.mensaje) + '</p>'
        + (aviso.programadaPara
            ? '<p class="campo__ayuda">Programado para ' + esc(fechaLarga(aviso.programadaPara)) + '</p>'
            : '')
        + '</li>').join('') + '</ul>';
}

/**
 * Los avisos los generan los MDB de forma asincronica: si el turno se acaba de
 * crear pueden tardar un instante. Se reintenta unas pocas veces.
 */
async function cargarAvisos(turnoId, intentos = 5) {
    const caja = document.getElementById('avisos-turno');
    if (!caja) {
        return;
    }
    try {
        const avisos = await api.notificacionesDeTurno(turnoId);
        if (avisos.length < 3 && intentos > 1) {
            dibujarAvisos(caja, avisos);
            setTimeout(() => cargarAvisos(turnoId, intentos - 1), 1500);
            return;
        }
        dibujarAvisos(caja, avisos);
    } catch (error) {
        mostrarError(caja, error.message);
    }
}

async function iniciar() {
    const sesion = await iniciarLayout();
    const detalle = document.getElementById('detalle');
    const botonCancelar = document.getElementById('boton-cancelar');
    const botonArchivar = document.getElementById('boton-archivar');
    const botonAtender = document.getElementById('boton-atender');
    const nota = document.getElementById('nota-permisos');

    ajustarEnlaceVolver(sesion);

    if (!id) {
        mostrarError(detalle, 'No se indicó qué turno mostrar.');
        return;
    }

    mostrarCargando(detalle, 'Cargando el turno...');

    let turno;
    try {
        turno = await api.turno(id);
    } catch (error) {
        reportar(error);
        mostrarError(detalle, error.message);
        return;
    }

    cargarAvisos(turno.id);

    dibujar(turno);
    nota.textContent = notaSegunEstado(turno);

    if (await puedeAtenderEste(sesion, turno)) {
        botonAtender.hidden = false;
    }

    if (await puedeCancelarEste(sesion, turno)) {
        botonCancelar.hidden = false;
    } else if (turno.estado === 'CANCELADO' && tieneRol(sesion, 'RECEPCION', 'ADMIN')) {
        botonArchivar.hidden = false;
        nota.textContent = 'Podés sacarlo de la agenda: el registro se conserva en el historial.';
    }

    botonAtender.addEventListener('click', async () => {
        botonAtender.disabled = true;
        botonAtender.textContent = 'Guardando...';
        try {
            turno = await api.atenderTurno(turno.id);
            dibujar(turno);
            avisoExito('El turno quedó registrado como atendido.');
            botonAtender.hidden = true;
            botonCancelar.hidden = true;
            nota.textContent = notaSegunEstado(turno);
        } catch (error) {
            reportar(error);
            mensajeEnCaja(document.getElementById('mensaje'), error.message, 'error');
            botonAtender.disabled = false;
            botonAtender.textContent = 'Marcar atendido';
        }
    });

    botonCancelar.addEventListener('click', async () => {
        const ok = await confirmar(
            '¿Cancelar este turno?',
            'El horario va a quedar disponible para otro paciente.',
            'Cancelar turno',
            'peligro');
        if (!ok) {
            return;
        }

        botonCancelar.disabled = true;
        botonCancelar.textContent = 'Cancelando...';
        try {
            const actualizado = await api.cancelarTurno(turno.id);
            turno = actualizado;
            dibujar(turno);
            avisoExito('El turno quedó cancelado.');
            mensajeEnCaja(document.getElementById('mensaje'),
                'El turno fue cancelado. Su horario volvió a quedar libre.', 'exito');
            botonCancelar.hidden = true;
            botonAtender.hidden = true;
            if (tieneRol(sesion, 'RECEPCION', 'ADMIN')) {
                botonArchivar.hidden = false;
                nota.textContent = 'Podés sacarlo de la agenda: el registro se conserva en el historial.';
            } else {
                nota.textContent = notaSegunEstado(turno);
            }
        } catch (error) {
            reportar(error);
            mensajeEnCaja(document.getElementById('mensaje'), error.message, 'error');
            botonCancelar.disabled = false;
            botonCancelar.textContent = 'Cancelar turno';
        }
    });

    botonArchivar.addEventListener('click', async () => {
        const ok = await confirmar(
            '¿Eliminar este turno de la agenda?',
            'El registro se va a conservar en el historial del paciente.',
            'Eliminar de agenda',
            'peligro');
        if (!ok) {
            return;
        }

        botonArchivar.disabled = true;
        botonArchivar.textContent = 'Eliminando...';
        try {
            await api.archivarTurno(turno.id);
            avisoExito('El turno fue eliminado de la agenda.');
            mensajeEnCaja(document.getElementById('mensaje'),
                'El turno fue eliminado de la agenda.', 'exito');
            botonArchivar.hidden = true;
            nota.textContent = 'Este turno ya no aparece en la agenda, pero sigue en el historial del paciente.';
        } catch (error) {
            reportar(error);
            mensajeEnCaja(document.getElementById('mensaje'), error.message, 'error');
            botonArchivar.disabled = false;
            botonArchivar.textContent = 'Eliminar de agenda';
        }
    });
}

iniciar();
