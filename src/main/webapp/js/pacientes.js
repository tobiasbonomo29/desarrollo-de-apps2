/**
 * Padron de pacientes: buscador, listado y alta.
 *
 * El buscador consulta al servidor (/api/pacientes?q=) porque la busqueda por
 * nombre o documento es una consulta de la capa de datos, no un filtro de
 * pantalla.
 */

import { api } from './api.js';
import { iniciarLayout, tieneRol, reportar } from './layout.js';
import { esc, mostrarCargando, mostrarVacio, mostrarError, avisoExito, mensajeEnCaja } from './ui.js';

const $ = (id) => document.getElementById(id);

let temporizador = null;

function fila(paciente) {
    const estado = paciente.activo
        ? '<span class="etiqueta etiqueta--activo">Activo</span>'
        : '<span class="etiqueta etiqueta--inactivo">Inactivo</span>';

    return '<tr>'
        + '<td data-titulo="Paciente"><span class="celda-principal">' + esc(paciente.nombre) + '</span>'
        + '<div class="celda-secundaria">' + esc(paciente.email || 'Sin email') + '</div></td>'
        + '<td data-titulo="Documento">' + esc(paciente.documento) + '</td>'
        + '<td data-titulo="Cobertura">' + esc(paciente.cobertura || '—') + '</td>'
        + '<td data-titulo="Teléfono">' + esc(paciente.telefono || '—') + '</td>'
        + '<td data-titulo="Estado">' + estado + '</td>'
        + '<td><a class="boton boton--secundario boton--chico" href="paciente.html?documento='
        + encodeURIComponent(paciente.documento) + '">Ver ficha</a></td>'
        + '</tr>';
}

async function cargar(texto) {
    const listado = $('listado');
    mostrarCargando(listado, 'Buscando pacientes...');

    try {
        const pacientes = await api.pacientes(texto);
        $('contador').textContent = pacientes.length
            + (pacientes.length === 1 ? ' paciente' : ' pacientes');

        if (pacientes.length === 0) {
            mostrarVacio(listado, 'No se encontraron pacientes',
                texto ? 'Probá con otro nombre o documento.' : 'Registrá el primero para empezar.');
            return;
        }

        listado.innerHTML = '<div class="tabla-scroll"><table class="tabla">'
            + '<thead><tr><th>Paciente</th><th>Documento</th><th>Cobertura</th>'
            + '<th>Teléfono</th><th>Estado</th><th></th></tr></thead>'
            + '<tbody>' + pacientes.map(fila).join('') + '</tbody></table></div>';
    } catch (error) {
        reportar(error);
        mostrarError(listado, error.message);
        $('contador').textContent = '';
    }
}

function prepararAlta() {
    $('abrir-alta').hidden = false;

    $('abrir-alta').addEventListener('click', () => {
        $('card-alta').hidden = false;
        $('nombre').focus();
    });

    $('cerrar-alta').addEventListener('click', () => {
        $('card-alta').hidden = true;
        $('form-alta').reset();
        mensajeEnCaja($('mensaje-alta'), '');
    });

    $('form-alta').addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const boton = $('guardar');
        boton.disabled = true;
        boton.textContent = 'Guardando...';
        mensajeEnCaja($('mensaje-alta'), '');

        try {
            const paciente = await api.registrarPaciente({
                nombre: $('nombre').value,
                documento: $('documento').value,
                email: $('email').value,
                telefono: $('telefono').value,
                cobertura: $('cobertura').value,
                activo: true
            });
            avisoExito('Paciente ' + paciente.nombre + ' registrado.');
            $('form-alta').reset();
            $('card-alta').hidden = true;
            await cargar($('busqueda').value.trim());
        } catch (error) {
            mensajeEnCaja($('mensaje-alta'), error.message, 'error');
        } finally {
            boton.disabled = false;
            boton.textContent = 'Registrar paciente';
        }
    });
}

async function iniciar() {
    const sesion = await iniciarLayout();

    if (tieneRol(sesion, 'RECEPCION', 'ADMIN')) {
        prepararAlta();
    }

    $('filtros').addEventListener('submit', e => e.preventDefault());
    $('busqueda').addEventListener('input', (e) => {
        clearTimeout(temporizador);
        const texto = e.target.value.trim();
        temporizador = setTimeout(() => cargar(texto), 250);
    });

    await cargar('');
}

iniciar();
