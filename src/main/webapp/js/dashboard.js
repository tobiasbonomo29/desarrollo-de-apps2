/**
 * Pantalla de inicio.
 *
 * Tiene dos caras segun el rol, y eso no es un capricho visual: /api/tablero
 * esta reservado a RECEPCION, MEDICO y ADMIN, asi que un PACIENTE recibiria un
 * 403. En vez de mostrarle un error se le muestra lo suyo: su ficha y sus turnos.
 */

import { api } from './api.js';
import { iniciarLayout, tieneRol, reportar } from './layout.js';
import { esc, etiquetaEstado, fechaLarga, hora, mostrarVacio, mostrarError } from './ui.js';

function metrica(titulo, valor, detalle) {
    return '<article class="metrica">'
        + '<div class="metrica__titulo">' + esc(titulo) + '</div>'
        + '<div class="metrica__valor">' + esc(valor) + '</div>'
        + '<div class="metrica__detalle">' + esc(detalle) + '</div>'
        + '</article>';
}

function filaTurno(turno) {
    return '<tr>'
        + '<td data-titulo="Paciente"><span class="celda-principal">'
        + esc(turno.paciente.nombre) + '</span></td>'
        + '<td data-titulo="Profesional">' + esc(turno.profesional.nombre)
        + '<div class="celda-secundaria">' + esc(turno.especialidad) + '</div></td>'
        + '<td data-titulo="Cuándo">' + esc(fechaLarga(turno.fechaHora)) + '</td>'
        + '<td data-titulo="Estado">' + etiquetaEstado(turno.estado) + '</td>'
        + '<td><a class="boton boton--secundario boton--chico" href="turno.html?id='
        + encodeURIComponent(turno.id) + '">Ver detalle</a></td>'
        + '</tr>';
}

function tablaTurnos(turnos) {
    return '<div class="tabla-scroll"><table class="tabla">'
        + '<thead><tr><th>Paciente</th><th>Profesional</th><th>Cuándo</th>'
        + '<th>Estado</th><th></th></tr></thead>'
        + '<tbody>' + turnos.map(filaTurno).join('') + '</tbody></table></div>';
}

function dibujarAgenda(contenedor, resumen) {
    const porProfesional = new Map();
    resumen.proximosTurnos.forEach(turno => {
        const clave = turno.profesional.nombre;
        porProfesional.set(clave, (porProfesional.get(clave) || 0) + 1);
    });

    if (porProfesional.size === 0) {
        mostrarVacio(contenedor, 'Sin turnos por delante',
            'Cuando se agende el primer turno vas a verlo acá.');
        return;
    }

    contenedor.innerHTML = '<div class="profesional__datos">'
        + [...porProfesional.entries()].map(([nombre, cantidad]) =>
            '<div style="display:flex;justify-content:space-between;gap:12px">'
            + '<strong>' + esc(nombre) + '</strong>'
            + '<span>' + esc(cantidad) + (cantidad === 1 ? ' turno' : ' turnos') + '</span>'
            + '</div>').join('')
        + '</div>';
}

async function panelEquipo() {
    document.getElementById('panel-equipo').hidden = false;

    const resumen = await api.tablero();

    document.getElementById('metricas').innerHTML =
        metrica('Turnos de hoy', resumen.turnosDeHoy, 'Agendados para la fecha de hoy')
        + metrica('Pacientes activos', resumen.pacientesActivos, 'Registrados en el padrón')
        + metrica('Profesionales', resumen.profesionalesActivos, 'Con agenda habilitada')
        + metrica('Pendientes', resumen.pendientes, 'Turnos programados por atender');

    const proximos = document.getElementById('proximos');
    if (resumen.proximosTurnos.length === 0) {
        mostrarVacio(proximos, 'No hay turnos próximos',
            'Cargá el primero desde el botón Nuevo turno.');
    } else {
        proximos.innerHTML = tablaTurnos(resumen.proximosTurnos);
    }

    dibujarAgenda(document.getElementById('agenda'), resumen);
}

async function panelPaciente() {
    document.getElementById('panel-paciente').hidden = false;

    const ficha = await api.miFicha();

    document.getElementById('mi-ficha').innerHTML = '<div class="detalle-datos">'
        + '<div><div class="dato__titulo">Nombre</div><div class="dato__valor">'
        + esc(ficha.nombre) + '</div></div>'
        + '<div><div class="dato__titulo">Documento</div><div class="dato__valor">'
        + esc(ficha.documento) + '</div></div>'
        + '<div><div class="dato__titulo">Cobertura</div><div class="dato__valor">'
        + esc(ficha.cobertura || 'Sin cobertura registrada') + '</div></div>'
        + '<div><div class="dato__titulo">Teléfono</div><div class="dato__valor">'
        + esc(ficha.telefono || '—') + '</div></div>'
        + '</div>';

    const turnos = await api.historial(ficha.documento);
    const contenedor = document.getElementById('mis-turnos');

    if (turnos.length === 0) {
        mostrarVacio(contenedor, 'Todavía no tenés turnos',
            'Reservá el primero con el asistente guiado.',
            '<a class="boton" href="reserva.html">Reservar turno</a>');
        return;
    }

    contenedor.innerHTML = '<div class="tabla-scroll"><table class="tabla">'
        + '<thead><tr><th>Profesional</th><th>Especialidad</th><th>Cuándo</th><th>Estado</th></tr></thead>'
        + '<tbody>' + turnos.map(turno =>
            '<tr>'
            + '<td data-titulo="Profesional"><span class="celda-principal">'
            + esc(turno.profesional.nombre) + '</span></td>'
            + '<td data-titulo="Especialidad">' + esc(turno.especialidad) + '</td>'
            + '<td data-titulo="Cuándo">' + esc(fechaLarga(turno.fechaHora))
            + '<div class="celda-secundaria">' + esc(hora(turno.fechaHora)) + ' hs</div></td>'
            + '<td data-titulo="Estado">' + etiquetaEstado(turno.estado) + '</td>'
            + '</tr>').join('')
        + '</tbody></table></div>';
}

async function iniciar() {
    const sesion = await iniciarLayout();

    const carga = document.getElementById('carga');

    try {
        if (tieneRol(sesion, 'RECEPCION', 'MEDICO', 'ADMIN')) {
            await panelEquipo();
        } else {
            await panelPaciente();
        }
        carga.hidden = true;
    } catch (error) {
        reportar(error);
        mostrarError(carga, error.message);
    }
}

iniciar();
