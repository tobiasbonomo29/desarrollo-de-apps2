/**
 * Profesionales: listado en tarjetas, alta (ADMIN) y disponibilidad (MEDICO/ADMIN).
 *
 * El contador de "turnos de hoy" solo se calcula para los roles que pueden leer
 * la agenda completa; un PACIENTE ve el resto de la tarjeta igual.
 */

import { api } from './api.js';
import { iniciarLayout, tieneRol, reportar } from './layout.js';
import { esc, iniciales, nombreDeDia, DIAS_SEMANA, hoyIso,
         mostrarCargando, mostrarVacio, mostrarError, avisoExito, mensajeEnCaja } from './ui.js';

const $ = (id) => document.getElementById(id);

let profesionales = [];
let turnosDeHoy = new Map();
let puedeVerAgenda = false;
let puedeEditarCualquiera = false;
let miProfesionalId = null;
let profesionalEnEdicion = null;

function puedeEditar(profesional) {
    if (puedeEditarCualquiera) {
        return true;
    }
    return miProfesionalId !== null && profesional.id === miProfesionalId;
}

/* ----------------------------- Listado ----------------------------- */

function resumenDeAgenda(profesional) {
    if (!profesional.disponibilidades || profesional.disponibilidades.length === 0) {
        return 'Sin disponibilidad cargada';
    }
    const dias = profesional.disponibilidades
        .map(f => nombreDeDia(f.diaSemana).substring(0, 3))
        .join(', ');
    const primera = profesional.disponibilidades[0];
    return dias + ' · ' + primera.horaDesde.substring(0, 5) + ' a ' + primera.horaHasta.substring(0, 5);
}

function tarjeta(profesional) {
    const estado = profesional.activo
        ? '<span class="etiqueta etiqueta--activo">Activo</span>'
        : '<span class="etiqueta etiqueta--inactivo">Inactivo</span>';

    let turnosHoy = 'Agenda no disponible';
    if (puedeVerAgenda) {
        const cantidad = turnosDeHoy.get(profesional.id) || 0;
        turnosHoy = cantidad === 0
            ? 'Sin turnos hoy'
            : cantidad + (cantidad === 1 ? ' turno hoy' : ' turnos hoy');
    }

    const boton = puedeEditar(profesional)
        ? '<button type="button" class="boton boton--secundario boton--chico" data-id="'
          + esc(profesional.id) + '">Editar disponibilidad</button>'
        : '';

    return '<article class="profesional">'
        + '<div class="profesional__cabecera">'
        + '<div class="avatar" aria-hidden="true">' + esc(iniciales(profesional.nombre.replace(/^(Dra?\.)\s*/i, ''))) + '</div>'
        + '<div><div class="profesional__nombre">' + esc(profesional.nombre) + '</div>'
        + '<div class="profesional__especialidad">' + esc(profesional.especialidad) + '</div></div>'
        + '</div>'
        + '<div class="profesional__datos">'
        + '<div>Matrícula <strong>' + esc(profesional.matricula) + '</strong></div>'
        + '<div>' + esc(resumenDeAgenda(profesional)) + '</div>'
        + '<div>' + esc(turnosHoy) + '</div>'
        + '</div>'
        + '<div class="profesional__pie">' + estado + boton + '</div>'
        + '</article>';
}

function dibujar() {
    const listado = $('listado');
    const texto = $('busqueda').value.trim().toLowerCase();
    const especialidad = $('especialidad').value;

    const visibles = profesionales
        .filter(p => !texto || p.nombre.toLowerCase().includes(texto)
            || p.especialidad.toLowerCase().includes(texto))
        .filter(p => !especialidad || p.especialidad === especialidad);

    $('contador').textContent = visibles.length === profesionales.length
        ? profesionales.length + (profesionales.length === 1 ? ' profesional' : ' profesionales')
        : visibles.length + ' de ' + profesionales.length + ' profesionales';

    if (visibles.length === 0) {
        mostrarVacio(listado, 'No se encontraron profesionales',
            'Probá con otro nombre o especialidad.');
        return;
    }

    listado.innerHTML = '<div class="grilla-tarjetas">' + visibles.map(tarjeta).join('') + '</div>';

    listado.querySelectorAll('button[data-id]').forEach(boton => {
        boton.addEventListener('click', () => abrirDisponibilidad(Number(boton.dataset.id)));
    });
}

async function cargar() {
    const listado = $('listado');
    mostrarCargando(listado, 'Cargando profesionales...');

    try {
        profesionales = await api.profesionales();
    } catch (error) {
        reportar(error);
        mostrarError(listado, error.message);
        return;
    }

    if (puedeVerAgenda) {
        try {
            const turnos = await api.turnos();
            const hoy = hoyIso();
            turnosDeHoy = new Map();
            turnos.filter(t => t.estado !== 'CANCELADO' && t.fechaHora.startsWith(hoy))
                .forEach(t => turnosDeHoy.set(t.profesional.id, (turnosDeHoy.get(t.profesional.id) || 0) + 1));
        } catch (error) {
            turnosDeHoy = new Map();
        }
    }

    const select = $('especialidad');
    select.innerHTML = '<option value="">Todas las especialidades</option>';
    const datalist = $('lista-especialidades');
    [...new Set(profesionales.map(p => p.especialidad))].sort().forEach(nombre => {
        const opcion = document.createElement('option');
        opcion.value = nombre;
        opcion.textContent = nombre;
        select.appendChild(opcion);
        if (datalist) {
            const sugerencia = document.createElement('option');
            sugerencia.value = nombre;
            datalist.appendChild(sugerencia);
        }
    });

    dibujar();
}

/* ------------------------------- Alta ------------------------------ */

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
        const boton = $('guardar-alta');
        boton.disabled = true;
        boton.textContent = 'Guardando...';
        mensajeEnCaja($('mensaje-alta'), '');

        try {
            const creado = await api.registrarProfesional({
                nombre: $('nombre').value,
                especialidad: $('especialidad-alta').value,
                matricula: $('matricula').value,
                email: $('email').value,
                activo: true,
                disponibilidades: []
            });
            avisoExito(creado.nombre + ' quedó registrado. Cargale la disponibilidad.');
            $('form-alta').reset();
            $('card-alta').hidden = true;
            await cargar();
        } catch (error) {
            mensajeEnCaja($('mensaje-alta'), error.message, 'error');
        } finally {
            boton.disabled = false;
            boton.textContent = 'Registrar profesional';
        }
    });
}

/* --------------------------- Disponibilidad ------------------------- */

function filaFranja(franja) {
    const opciones = DIAS_SEMANA.map(dia =>
        '<option value="' + dia.valor + '"' + (franja && franja.diaSemana === dia.valor ? ' selected' : '')
        + '>' + dia.texto + '</option>').join('');

    return '<div class="franja" style="display:grid;grid-template-columns:1.4fr 1fr 1fr 1fr auto;'
        + 'gap:10px;align-items:end;margin-bottom:12px">'
        + '<div class="campo" style="margin:0"><label>Día</label><select class="f-dia">' + opciones + '</select></div>'
        + '<div class="campo" style="margin:0"><label>Desde</label>'
        + '<input type="time" class="f-desde" value="' + esc(franja ? franja.horaDesde.substring(0, 5) : '09:00') + '"></div>'
        + '<div class="campo" style="margin:0"><label>Hasta</label>'
        + '<input type="time" class="f-hasta" value="' + esc(franja ? franja.horaHasta.substring(0, 5) : '13:00') + '"></div>'
        + '<div class="campo" style="margin:0"><label>Minutos</label>'
        + '<input type="number" class="f-duracion" min="5" max="120" step="5" value="'
        + esc(franja ? franja.duracionMinutos : 30) + '"></div>'
        + '<button type="button" class="boton boton--peligro boton--chico f-quitar">Quitar</button>'
        + '</div>';
}

function engancharQuitar() {
    $('franjas').querySelectorAll('.f-quitar').forEach(boton => {
        boton.onclick = () => boton.closest('.franja').remove();
    });
}

function abrirDisponibilidad(id) {
    profesionalEnEdicion = profesionales.find(p => p.id === id);
    if (!profesionalEnEdicion) {
        return;
    }

    $('titulo-disponibilidad').textContent = 'Disponibilidad de ' + profesionalEnEdicion.nombre;
    $('franjas').innerHTML = (profesionalEnEdicion.disponibilidades || []).map(filaFranja).join('')
        || filaFranja(null);
    engancharQuitar();
    mensajeEnCaja($('mensaje-disponibilidad'), '');
    $('card-disponibilidad').hidden = false;
    $('card-disponibilidad').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function prepararDisponibilidad() {
    $('agregar-franja').addEventListener('click', () => {
        $('franjas').insertAdjacentHTML('beforeend', filaFranja(null));
        engancharQuitar();
    });

    $('cerrar-disponibilidad').addEventListener('click', () => {
        $('card-disponibilidad').hidden = true;
        profesionalEnEdicion = null;
    });

    $('guardar-disponibilidad').addEventListener('click', async () => {
        const boton = $('guardar-disponibilidad');
        const franjas = [...$('franjas').querySelectorAll('.franja')].map(fila => ({
            diaSemana: fila.querySelector('.f-dia').value,
            horaDesde: fila.querySelector('.f-desde').value,
            horaHasta: fila.querySelector('.f-hasta').value,
            duracionMinutos: Number(fila.querySelector('.f-duracion').value)
        }));

        boton.disabled = true;
        boton.textContent = 'Guardando...';
        mensajeEnCaja($('mensaje-disponibilidad'), '');

        try {
            await api.definirDisponibilidad(profesionalEnEdicion.id, franjas);
            avisoExito('Disponibilidad actualizada.');
            $('card-disponibilidad').hidden = true;
            await cargar();
        } catch (error) {
            mensajeEnCaja($('mensaje-disponibilidad'), error.message, 'error');
        } finally {
            boton.disabled = false;
            boton.textContent = 'Guardar disponibilidad';
        }
    });
}

/* ------------------------------ Arranque ---------------------------- */

async function iniciar() {
    const sesion = await iniciarLayout();

    puedeVerAgenda = tieneRol(sesion, 'RECEPCION', 'MEDICO', 'ADMIN');
    puedeEditarCualquiera = tieneRol(sesion, 'RECEPCION', 'ADMIN');
    const esSoloMedico = tieneRol(sesion, 'MEDICO') && !tieneRol(sesion, 'RECEPCION', 'ADMIN');

    if (tieneRol(sesion, 'ADMIN')) {
        prepararAlta();
    }
    if (puedeEditarCualquiera || esSoloMedico) {
        prepararDisponibilidad();
    }

    if (esSoloMedico) {
        try {
            const perfil = await api.miPerfilProfesional();
            miProfesionalId = perfil.id;
        } catch (error) {
            miProfesionalId = null;
        }
    }

    $('filtros').addEventListener('submit', e => e.preventDefault());
    $('busqueda').addEventListener('input', dibujar);
    $('especialidad').addEventListener('change', dibujar);

    await cargar();
}

iniciar();
