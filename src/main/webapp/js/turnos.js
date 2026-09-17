/**
 * Listado de turnos con buscador y filtros.
 *
 * Los turnos se piden una sola vez y el filtrado es en el navegador: para el
 * volumen de este TP es mas simple y evita un request por tecla.
 */

import { api } from './api.js';
import { iniciarLayout, tieneRol, reportar } from './layout.js';
import { esc, etiquetaEstado, fecha, hora, mostrarCargando, mostrarVacio, mostrarError } from './ui.js';

let turnos = [];

function coincide(turno, texto) {
    if (!texto) {
        return true;
    }
    const buscado = texto.toLowerCase();
    return turno.paciente.nombre.toLowerCase().includes(buscado)
        || turno.paciente.documento.includes(buscado)
        || turno.profesional.nombre.toLowerCase().includes(buscado)
        || turno.especialidad.toLowerCase().includes(buscado);
}

function filtrar() {
    const texto = document.getElementById('busqueda').value.trim();
    const estado = document.getElementById('estado').value;
    const especialidad = document.getElementById('especialidad').value;

    return turnos
        .filter(t => coincide(t, texto))
        .filter(t => !estado || t.estado === estado)
        .filter(t => !especialidad || t.especialidad === especialidad);
}

function fila(turno) {
    return '<tr>'
        + '<td data-titulo="Paciente"><span class="celda-principal">' + esc(turno.paciente.nombre)
        + '</span><div class="celda-secundaria">DNI ' + esc(turno.paciente.documento) + '</div></td>'
        + '<td data-titulo="Profesional">' + esc(turno.profesional.nombre)
        + '<div class="celda-secundaria">' + esc(turno.especialidad) + '</div></td>'
        + '<td data-titulo="Fecha">' + esc(fecha(turno.fechaHora)) + '</td>'
        + '<td data-titulo="Hora">' + esc(hora(turno.fechaHora)) + '</td>'
        + '<td data-titulo="Estado">' + etiquetaEstado(turno.estado) + '</td>'
        + '<td><a class="boton boton--secundario boton--chico" href="turno.html?id='
        + encodeURIComponent(turno.id) + '">Ver detalle</a></td>'
        + '</tr>';
}

function dibujar() {
    const listado = document.getElementById('listado');
    const contador = document.getElementById('contador');
    const visibles = filtrar();

    contador.textContent = visibles.length === turnos.length
        ? turnos.length + (turnos.length === 1 ? ' turno registrado' : ' turnos registrados')
        : visibles.length + ' de ' + turnos.length + ' turnos';

    if (visibles.length === 0) {
        mostrarVacio(listado,
            turnos.length === 0 ? 'La agenda está vacía' : 'Ningún turno coincide con el filtro',
            turnos.length === 0
                ? 'Cargá el primer turno para empezar.'
                : 'Probá con otro texto de búsqueda o quitá los filtros.');
        return;
    }

    listado.innerHTML = '<div class="tabla-scroll"><table class="tabla">'
        + '<thead><tr><th>Paciente</th><th>Profesional</th><th>Fecha</th>'
        + '<th>Hora</th><th>Estado</th><th></th></tr></thead>'
        + '<tbody>' + visibles.map(fila).join('') + '</tbody></table></div>';
}

function cargarEspecialidades() {
    const select = document.getElementById('especialidad');
    [...new Set(turnos.map(t => t.especialidad))].sort().forEach(especialidad => {
        const opcion = document.createElement('option');
        opcion.value = especialidad;
        opcion.textContent = especialidad;
        select.appendChild(opcion);
    });
}

async function iniciar() {
    const sesion = await iniciarLayout();

    if (tieneRol(sesion, 'RECEPCION', 'ADMIN')) {
        document.getElementById('boton-nuevo-turno').hidden = false;
    }

    const listado = document.getElementById('listado');
    mostrarCargando(listado, 'Cargando turnos...');

    try {
        turnos = await api.turnos();
        cargarEspecialidades();
        dibujar();
    } catch (error) {
        reportar(error);
        mostrarError(listado, error.message);
        document.getElementById('contador').textContent = '';
        return;
    }

    document.getElementById('filtros').addEventListener('submit', e => e.preventDefault());
    document.getElementById('busqueda').addEventListener('input', dibujar);
    document.getElementById('estado').addEventListener('change', dibujar);
    document.getElementById('especialidad').addEventListener('change', dibujar);
}

iniciar();
