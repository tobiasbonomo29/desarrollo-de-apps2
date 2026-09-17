/**
 * Reserva de turno, paso a paso.
 *
 * En cada paso este archivo envia UN solo dato (la especialidad, o el
 * profesional, o el horario): nunca reenvia lo elegido antes. El resumen de la
 * derecha y el estado del formulario se arman con lo que devuelve el servidor
 * en cada respuesta.
 */

import { api, contexto } from './api.js';
import { iniciarLayout, tieneRol, reportar } from './layout.js';
import { esc, hora, hoyIso, fechaLarga, mostrarCargando, avisoExito, mensajeEnCaja } from './ui.js';

const $ = (id) => document.getElementById(id);

// Los pasos numerados del stepper. La eleccion del paciente (para quien
// reserva RECEPCION o ADMIN) no cuenta como paso: es previa.
const NOMBRES = ['Especialidad', 'Profesional', 'Horario', 'Confirmar'];

// Los pasos del backend: SIN_INICIAR=0, ELEGIR_ESPECIALIDAD=1,
// ELEGIR_PROFESIONAL=2, ELEGIR_HORARIO=3, CONFIRMAR=4, FINALIZADA=5.

let estado = null;
let sesion = null;
let fechaElegida = hoyIso();

/* ----------------------------- Pintado comun ----------------------------- */

function dibujarPasos() {
    const pasos = $('pasos');

    // Paso 0: todavia no se identifico al paciente. No hay stepper numerado.
    if (estado.paso === 0) {
        pasos.innerHTML = '';
        return;
    }

    pasos.innerHTML = NOMBRES.map((nombre, indice) => {
        const numeroPaso = indice + 1;
        let clase = 'paso';
        if (estado.paso > numeroPaso || estado.paso === 5) {
            clase += ' paso--hecho';
        } else if (estado.paso === numeroPaso) {
            clase += ' paso--activo';
        }
        return '<span class="' + clase + '"><span class="paso__numero">' + numeroPaso
            + '</span>' + esc(nombre) + '</span>';
    }).join('');
}

function dibujarResumen() {
    const filas = [
        ['r-paciente', estado.nombrePaciente, 'Sin iniciar'],
        ['r-especialidad', estado.especialidad, 'Sin elegir'],
        ['r-profesional', estado.nombreProfesional, 'Sin elegir'],
        ['r-fecha', estado.fechaHora ? fechaLarga(estado.fechaHora) + ' hs' : null, 'Sin elegir']
    ];

    filas.forEach(([id, valor, vacio]) => {
        const celda = $(id);
        celda.textContent = valor || vacio;
        celda.classList.toggle('vacio', !valor);
    });
}

function botones(lista) {
    $('acciones').innerHTML = '';
    lista.forEach(({ texto, clase, alHacerClic, deshabilitado }) => {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'boton ' + (clase || '');
        boton.textContent = texto;
        boton.disabled = !!deshabilitado;
        boton.addEventListener('click', alHacerClic);
        $('acciones').appendChild(boton);
    });
}

async function ejecutar(accion) {
    mensajeEnCaja($('mensaje'), '');
    try {
        estado = await accion();
        await pintar();
    } catch (error) {
        mensajeEnCaja($('mensaje'), error.message, 'error');
        if (error.codigo === 403) {
            reportar(error);
        }
    }
}

/* -------------------------------- Pasos --------------------------------- */

async function pasoInicio() {
    const contenedor = $('paso');

    // Un paciente reserva siempre para si mismo: no hace falta que elija nada
    // ni que confirme quien es, arranca directo en el primer paso.
    if (!tieneRol(sesion, 'RECEPCION', 'ADMIN')) {
        mostrarCargando(contenedor, 'Preparando tu reserva...');
        botones([]);
        try {
            const ficha = await api.miFicha();
            estado = await api.reservaIniciar(ficha.documento);
            await pintar();
        } catch (error) {
            contenedor.innerHTML = '';
            botones([]);
            mensajeEnCaja($('mensaje'), error.message, 'error');
        }
        return;
    }

    const pacientes = await api.pacientes();
    contenedor.innerHTML = '<h2>Seleccioná al paciente</h2>'
        + '<p class="campo__ayuda" style="margin-bottom:16px">Elegí para quién es la reserva.</p>'
        + '<div class="campo"><label for="doc">Paciente</label>'
        + '<select id="doc"><option value="">Seleccioná un paciente</option>'
        + pacientes.map(p => '<option value="' + esc(p.documento) + '">'
            + esc(p.nombre) + ' — DNI ' + esc(p.documento) + '</option>').join('')
        + '</select></div>';

    const actualizar = () => botones([{
        texto: 'Continuar',
        deshabilitado: !$('doc').value,
        alHacerClic: () => ejecutar(() => api.reservaIniciar($('doc').value))
    }]);

    $('doc').addEventListener('change', actualizar);
    actualizar();
}

async function pasoEspecialidad() {
    const contenedor = $('paso');
    mostrarCargando(contenedor, 'Cargando especialidades...');

    const especialidades = await api.especialidades();
    contenedor.innerHTML = '<h2>Elegí una especialidad</h2>'
        + '<div class="opciones">'
        + especialidades.map(e => '<button type="button" class="opcion" data-valor="' + esc(e) + '">'
            + esc(e) + '<span aria-hidden="true">›</span></button>').join('')
        + '</div>';

    contenedor.querySelectorAll('.opcion').forEach(boton => {
        boton.addEventListener('click', () =>
            ejecutar(() => api.reservaEspecialidad(boton.dataset.valor)));
    });

    botones([{
        texto: 'Cancelar reserva',
        clase: 'boton--secundario',
        alHacerClic: () => ejecutar(() => api.reservaCancelar())
    }]);
}

async function pasoProfesional() {
    const contenedor = $('paso');
    mostrarCargando(contenedor, 'Cargando profesionales...');

    const profesionales = await api.profesionales(estado.especialidad);
    contenedor.innerHTML = '<h2>Elegí un profesional</h2>'
        + '<p class="campo__ayuda" style="margin-bottom:16px">Profesionales de '
        + esc(estado.especialidad) + '.</p>'
        + '<div class="opciones">'
        + profesionales.filter(p => p.activo).map(p =>
            '<button type="button" class="opcion" data-valor="' + esc(p.id) + '">'
            + '<span>' + esc(p.nombre) + '<small>Matrícula ' + esc(p.matricula) + '</small></span>'
            + '<span aria-hidden="true">›</span></button>').join('')
        + '</div>';

    contenedor.querySelectorAll('.opcion').forEach(boton => {
        boton.addEventListener('click', () =>
            ejecutar(() => api.reservaProfesional(Number(boton.dataset.valor))));
    });

    botones([
        { texto: 'Atrás', clase: 'boton--secundario', alHacerClic: () => ejecutar(() => api.reservaVolver()) },
        { texto: 'Cancelar reserva', clase: 'boton--secundario', alHacerClic: () => ejecutar(() => api.reservaCancelar()) }
    ]);
}

async function pasoHorario() {
    const contenedor = $('paso');

    contenedor.innerHTML = '<h2>Elegí un horario disponible</h2>'
        + '<div class="campo" style="max-width:240px"><label for="fecha">Fecha</label>'
        + '<input type="date" id="fecha" min="' + esc(hoyIso()) + '" value="' + esc(fechaElegida) + '"></div>'
        + '<div id="horarios"></div>';

    async function cargarHorarios() {
        const caja = $('horarios');
        mostrarCargando(caja, 'Buscando horarios...');
        try {
            const horarios = await api.reservaHorarios(fechaElegida);
            if (horarios.length === 0) {
                caja.innerHTML = '<p class="campo__ayuda">No hay horarios libres ese día. Probá con otra fecha.</p>';
                return;
            }
            caja.innerHTML = '<p class="campo__ayuda" style="margin-bottom:10px">'
                + 'Estos son los horarios disponibles para esta fecha.</p>'
                + '<div class="grilla-horarios">'
                + horarios.map(h => '<button type="button" class="horario" data-valor="' + esc(h) + '">'
                    + esc(hora(h)) + '</button>').join('') + '</div>';
            caja.querySelectorAll('.horario').forEach(boton => {
                boton.addEventListener('click', () =>
                    ejecutar(() => api.reservaHorario(boton.dataset.valor)));
            });
        } catch (error) {
            caja.innerHTML = '<p class="campo__ayuda">' + esc(error.message) + '</p>';
        }
    }

    $('fecha').addEventListener('change', (e) => {
        fechaElegida = e.target.value;
        cargarHorarios();
    });

    botones([
        { texto: 'Atrás', clase: 'boton--secundario', alHacerClic: () => ejecutar(() => api.reservaVolver()) },
        { texto: 'Cancelar reserva', clase: 'boton--secundario', alHacerClic: () => ejecutar(() => api.reservaCancelar()) }
    ]);

    await cargarHorarios();
}

function pasoConfirmar() {
    $('paso').innerHTML = '<h2>Revisá los datos antes de confirmar</h2>'
        + '<div class="detalle-datos">'
        + '<div><div class="dato__titulo">Paciente</div><div class="dato__valor">'
        + esc(estado.nombrePaciente) + '</div></div>'
        + '<div><div class="dato__titulo">Especialidad</div><div class="dato__valor">'
        + esc(estado.especialidad) + '</div></div>'
        + '<div><div class="dato__titulo">Profesional</div><div class="dato__valor">'
        + esc(estado.nombreProfesional) + '</div></div>'
        + '<div><div class="dato__titulo">Fecha y hora</div><div class="dato__valor">'
        + esc(fechaLarga(estado.fechaHora)) + ' hs</div></div>'
        + '</div>';

    botones([
        { texto: 'Atrás', clase: 'boton--secundario', alHacerClic: () => ejecutar(() => api.reservaVolver()) },
        { texto: 'Confirmar reserva', alHacerClic: () => ejecutar(async () => {
            const resultado = await api.reservaConfirmar();
            avisoExito('Turno #' + resultado.turnoId + ' confirmado.');
            return resultado;
        }) }
    ]);
}

function pasoFinalizado() {
    $('paso').innerHTML = '<div class="aviso aviso--exito" style="margin-bottom:20px">'
        + 'Turno #' + esc(estado.turnoId) + ' confirmado para ' + esc(estado.nombrePaciente) + '.</div>'
        + '<h2>Reserva confirmada</h2>'
        + '<p class="campo__ayuda">Guardamos tu turno. Podés verlo en el detalle o empezar una reserva nueva.</p>';

    botones([
        { texto: 'Ver el turno', alHacerClic: () => {
            window.location.href = contexto + '/app/turno.html?id=' + encodeURIComponent(estado.turnoId);
        } },
        { texto: 'Nueva reserva', clase: 'boton--secundario', alHacerClic: async () => {
            estado = await api.reservaEstado();
            await pintar();
        } }
    ]);
}

/* ------------------------------ Orquestacion ----------------------------- */

async function pintar() {
    dibujarPasos();
    dibujarResumen();

    switch (estado.paso) {
        case 1: await pasoEspecialidad(); break;
        case 2: await pasoProfesional(); break;
        case 3: await pasoHorario(); break;
        case 4: pasoConfirmar(); break;
        case 5: pasoFinalizado(); break;
        default: await pasoInicio();
    }
}

async function iniciar() {
    sesion = await iniciarLayout();

    mostrarCargando($('paso'), 'Preparando la reserva...');

    try {
        estado = await api.reservaEstado();
        await pintar();
    } catch (error) {
        reportar(error);
        mensajeEnCaja($('mensaje'), error.message, 'error');
        $('paso').innerHTML = '';
    }
}

iniciar();
