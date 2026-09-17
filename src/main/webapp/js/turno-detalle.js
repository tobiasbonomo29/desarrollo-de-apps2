/**
 * Detalle de un turno y cancelacion.
 *
 * El boton de cancelar se deshabilita cuando el rol no corresponde o cuando el
 * turno ya no se puede cancelar. La comprobacion real la vuelve a hacer el
 * servidor: si alguien llamara al endpoint igual recibiria 403 o 400.
 */

import { api } from './api.js';
import { iniciarLayout, tieneRol, reportar } from './layout.js';
import { esc, etiquetaEstado, fechaLarga, mostrarCargando, mostrarError, avisoExito, mensajeEnCaja } from './ui.js';

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

async function iniciar() {
    const sesion = await iniciarLayout();
    const detalle = document.getElementById('detalle');
    const boton = document.getElementById('boton-cancelar');
    const nota = document.getElementById('nota-permisos');

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

    dibujar(turno);

    const puedeCancelar = tieneRol(sesion, 'RECEPCION', 'MEDICO', 'ADMIN');
    if (!puedeCancelar) {
        nota.textContent = 'Tu rol no puede cancelar turnos. Comunicate con recepción.';
    } else if (turno.estado !== 'PROGRAMADO') {
        nota.textContent = 'Solo se pueden cancelar los turnos en estado Programado.';
    } else {
        boton.disabled = false;
        nota.textContent = 'Al cancelar, el horario vuelve a quedar disponible para otro paciente.';
    }

    boton.addEventListener('click', async () => {
        boton.disabled = true;
        boton.textContent = 'Cancelando...';
        try {
            const actualizado = await api.cancelarTurno(turno.id);
            dibujar(actualizado);
            avisoExito('El turno quedó cancelado.');
            mensajeEnCaja(document.getElementById('mensaje'),
                'El turno fue cancelado. Su horario volvió a quedar libre.', 'exito');
            nota.textContent = 'Solo se pueden cancelar los turnos en estado Programado.';
        } catch (error) {
            reportar(error);
            mensajeEnCaja(document.getElementById('mensaje'), error.message, 'error');
            boton.disabled = false;
        } finally {
            boton.textContent = 'Cancelar turno';
        }
    });
}

iniciar();
