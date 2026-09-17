/**
 * Ficha de un paciente: datos, edicion e historial de turnos.
 *
 * La edicion solo se muestra a RECEPCION y ADMIN, que son los roles que
 * GestionPacientes.actualizarDatos permite.
 */

import { api } from './api.js';
import { iniciarLayout, tieneRol, reportar } from './layout.js';
import { esc, etiquetaEstado, fechaLarga, mostrarCargando, mostrarVacio, mostrarError, avisoExito, mensajeEnCaja } from './ui.js';

const $ = (id) => document.getElementById(id);
const documento = new URLSearchParams(location.search).get('documento');

function dato(titulo, valor) {
    return '<div><div class="dato__titulo">' + esc(titulo) + '</div>'
        + '<div class="dato__valor">' + esc(valor) + '</div></div>';
}

function dibujarFicha(paciente) {
    $('nombre-paciente').textContent = paciente.nombre;
    $('documento-paciente').textContent = 'Documento ' + paciente.documento;
    $('estado-paciente').innerHTML = paciente.activo
        ? '<span class="etiqueta etiqueta--activo">Activo</span>'
        : '<span class="etiqueta etiqueta--inactivo">Inactivo</span>';

    $('ficha').innerHTML = '<div class="detalle-datos">'
        + dato('Email', paciente.email || '—')
        + dato('Teléfono', paciente.telefono || '—')
        + dato('Cobertura', paciente.cobertura || 'Sin cobertura registrada')
        + dato('Usuario del sistema', paciente.usuario || 'Sin usuario asociado')
        + '</div>';
}

async function cargarHistorial() {
    const contenedor = $('historial');
    mostrarCargando(contenedor, 'Cargando historial...');

    try {
        const turnos = await api.historial(documento);
        if (turnos.length === 0) {
            mostrarVacio(contenedor, 'Sin turnos registrados',
                'Este paciente todavía no tuvo ninguna atención agendada.');
            return;
        }

        contenedor.innerHTML = '<div class="tabla-scroll"><table class="tabla">'
            + '<thead><tr><th>Profesional</th><th>Especialidad</th><th>Cuándo</th>'
            + '<th>Estado</th><th></th></tr></thead><tbody>'
            + turnos.map(turno => '<tr>'
                + '<td data-titulo="Profesional"><span class="celda-principal">'
                + esc(turno.profesional.nombre) + '</span></td>'
                + '<td data-titulo="Especialidad">' + esc(turno.especialidad) + '</td>'
                + '<td data-titulo="Cuándo">' + esc(fechaLarga(turno.fechaHora)) + '</td>'
                + '<td data-titulo="Estado">' + etiquetaEstado(turno.estado) + '</td>'
                + '<td><a class="boton boton--secundario boton--chico" href="turno.html?id='
                + encodeURIComponent(turno.id) + '">Ver detalle</a></td>'
                + '</tr>').join('')
            + '</tbody></table></div>';
    } catch (error) {
        reportar(error);
        mostrarError(contenedor, error.message);
    }
}

function prepararEdicion(paciente) {
    $('card-editar').hidden = false;
    $('nombre').value = paciente.nombre;
    $('email').value = paciente.email || '';
    $('telefono').value = paciente.telefono || '';
    $('cobertura').value = paciente.cobertura || '';
    $('activo').value = String(paciente.activo);

    $('form-editar').addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const boton = $('guardar');
        boton.disabled = true;
        boton.textContent = 'Guardando...';
        mensajeEnCaja($('mensaje'), '');

        try {
            const actualizado = await api.actualizarPaciente(documento, {
                nombre: $('nombre').value,
                email: $('email').value,
                telefono: $('telefono').value,
                cobertura: $('cobertura').value,
                activo: $('activo').value === 'true'
            });
            dibujarFicha(actualizado);
            avisoExito('Datos actualizados.');
            mensajeEnCaja($('mensaje'), 'Los datos del paciente se guardaron correctamente.', 'exito');
        } catch (error) {
            mensajeEnCaja($('mensaje'), error.message, 'error');
        } finally {
            boton.disabled = false;
            boton.textContent = 'Guardar cambios';
        }
    });
}

async function iniciar() {
    const sesion = await iniciarLayout();

    if (!documento) {
        mostrarError($('ficha'), 'No se indicó qué paciente mostrar.');
        return;
    }

    mostrarCargando($('ficha'), 'Cargando ficha...');

    let paciente;
    try {
        paciente = await api.paciente(documento);
    } catch (error) {
        reportar(error);
        mostrarError($('ficha'), error.message);
        return;
    }

    dibujarFicha(paciente);

    if (tieneRol(sesion, 'RECEPCION', 'ADMIN')) {
        prepararEdicion(paciente);
    }

    await cargarHistorial();
}

iniciar();
