/**
 * Turnos del paciente autenticado: programados, atendidos y cancelados.
 *
 * Nunca lee un documento de la URL: siempre busca la ficha propia con
 * miFicha() y pide el historial de esa ficha, asi que un paciente no puede
 * ver turnos de otro cambiando algo en el navegador.
 */

import { api } from './api.js';
import { iniciarLayout, reportar } from './layout.js';
import { esc, etiquetaEstado, fecha, hora, mostrarCargando, mostrarVacio, mostrarError, avisoExito, confirmar } from './ui.js';

const $ = (id) => document.getElementById(id);

function fila(turno) {
    const puedeCancelar = turno.estado === 'PROGRAMADO';
    const accionCancelar = puedeCancelar
        ? '<button type="button" class="boton boton--peligro boton--chico" data-cancelar="'
          + esc(turno.id) + '">Cancelar</button>'
        : '';

    return '<tr>'
        + '<td data-titulo="Profesional"><span class="celda-principal">' + esc(turno.profesional.nombre)
        + '</span><div class="celda-secundaria">' + esc(turno.especialidad) + '</div></td>'
        + '<td data-titulo="Fecha">' + esc(fecha(turno.fechaHora)) + '</td>'
        + '<td data-titulo="Hora">' + esc(hora(turno.fechaHora)) + '</td>'
        + '<td data-titulo="Estado">' + etiquetaEstado(turno.estado) + '</td>'
        + '<td style="display:flex;gap:8px;justify-content:flex-end;flex-wrap:wrap">'
        + '<a class="boton boton--secundario boton--chico" href="turno.html?id='
        + encodeURIComponent(turno.id) + '">Ver detalle</a>'
        + accionCancelar
        + '</td>'
        + '</tr>';
}

async function cargar() {
    const listado = $('listado');
    mostrarCargando(listado, 'Cargando tus turnos...');

    let turnos;
    try {
        const ficha = await api.miFicha();
        turnos = await api.historial(ficha.documento);
    } catch (error) {
        reportar(error);
        mostrarError(listado, error.message);
        $('contador').textContent = '';
        return;
    }

    $('contador').textContent = turnos.length === 0
        ? 'Todavía no tenés turnos'
        : turnos.length + (turnos.length === 1 ? ' turno' : ' turnos');

    if (turnos.length === 0) {
        mostrarVacio(listado, 'Todavía no tenés turnos',
            'Reservá el primero cuando quieras.',
            '<a class="boton" href="reserva.html">Reservar turno</a>');
        return;
    }

    listado.innerHTML = '<div class="tabla-scroll"><table class="tabla">'
        + '<thead><tr><th>Profesional</th><th>Fecha</th><th>Hora</th><th>Estado</th><th></th></tr></thead>'
        + '<tbody>' + turnos.map(fila).join('') + '</tbody></table></div>';

    listado.querySelectorAll('[data-cancelar]').forEach(boton => {
        boton.addEventListener('click', async () => {
            const ok = await confirmar(
                '¿Cancelar este turno?',
                'El horario va a quedar disponible para otro paciente.',
                'Cancelar turno',
                'peligro');
            if (!ok) {
                return;
            }
            try {
                await api.cancelarTurno(boton.dataset.cancelar);
                avisoExito('Turno cancelado.');
                await cargar();
            } catch (error) {
                reportar(error);
            }
        });
    });
}

async function iniciar() {
    await iniciarLayout();
    await cargar();
}

iniciar();
