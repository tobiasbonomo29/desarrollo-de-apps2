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
import {
    esc, hora, hoyIso, fechaLarga, mostrarCargando, avisoExito, mensajeEnCaja,
    atiendeEseDia, proximosDiasDeAtencion, textoDisponibilidad, lugaresPorDia,
    htmlDiasDeAtencion, marcarDiaElegido
} from './ui.js';

const $ = (id) => document.getElementById(id);

// Los pasos numerados del stepper. La eleccion del paciente (para quien
// reserva RECEPCION o ADMIN) no cuenta como paso: es previa.
const NOMBRES = ['Especialidad', 'Profesional', 'Horario', 'Confirmar'];

// Los pasos del backend: SIN_INICIAR=0, ELEGIR_ESPECIALIDAD=1,
// ELEGIR_PROFESIONAL=2, ELEGIR_HORARIO=3, CONFIRMAR=4, FINALIZADA=5.

let estado = null;
let sesion = null;
let fechaElegida = hoyIso();

// Orientacion con IA del paso 1 ("Prefiero escribirlo"). La conversacion vive
// solo en esta pagina: el servidor no la guarda, la recibe completa en cada
// mensaje. Si la persona vuelve al paso 1 con "Atras", la encuentra igual.
const SALUDO = 'Hola, contame con tus palabras qué te pasa o qué tipo de consulta necesitás, '
    + 'y te sugiero con qué especialidad sacar el turno.';
const chat = { abierto: false, mensajes: [], esperando: false };

function reiniciarChat() {
    chat.abierto = false;
    chat.mensajes = [];
    chat.esperando = false;
}

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
        + '<p class="campo__ayuda u-mb-16">Elegí para quién es la reserva.</p>'
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
    botones([{
        texto: 'Cancelar reserva',
        clase: 'boton--secundario',
        alHacerClic: () => ejecutar(() => api.reservaCancelar())
    }]);

    if (chat.abierto) {
        pasoEspecialidadConChat();
        return;
    }

    const contenedor = $('paso');
    mostrarCargando(contenedor, 'Cargando especialidades...');

    const especialidades = await api.especialidades();
    contenedor.innerHTML = '<h2>Elegí una especialidad</h2>'
        + '<div class="opciones">'
        + especialidades.map(e => '<button type="button" class="opcion" data-valor="' + esc(e) + '">'
            + esc(e) + '<span aria-hidden="true">›</span></button>').join('')
        + '</div>'
        + '<p class="separador-ia"><span>¿No sabés cuál elegir?</span></p>'
        + '<button type="button" class="opcion opcion--ia" id="abrir-chat">'
        + '<span class="opcion-ia__icono" aria-hidden="true">✦</span>'
        + '<span class="opcion-ia__texto">Prefiero escribirlo'
        + '<small>' + (chat.mensajes.length
            ? 'Seguí la conversación con el asistente.'
            : 'Contale al asistente qué te pasa y te sugiere la especialidad.') + '</small></span>'
        + '<span aria-hidden="true">›</span></button>';

    contenedor.querySelectorAll('.opcion[data-valor]').forEach(boton => {
        boton.addEventListener('click', () =>
            ejecutar(() => api.reservaEspecialidad(boton.dataset.valor)));
    });

    $('abrir-chat').addEventListener('click', () => {
        chat.abierto = true;
        pasoEspecialidadConChat();
    });
}

/* --------------------- Paso 1 con el asistente de IA --------------------- */

function pasoEspecialidadConChat() {
    const contenedor = $('paso');
    contenedor.innerHTML = '<div class="chat-ia__cabecera">'
        + '<h2>Contanos qué te pasa</h2>'
        + '<button type="button" class="boton boton--secundario boton--chico" id="volver-lista">'
        + 'Ver la lista de especialidades</button></div>'
        + '<div class="chat-ia">'
        + '<div class="chat-ia__mensajes" id="chat-mensajes" role="log" aria-live="polite"></div>'
        + '<form class="chat-ia__formulario" id="chat-formulario">'
        + '<label for="chat-texto" class="solo-lectores">Tu mensaje</label>'
        + '<textarea id="chat-texto" rows="1" maxlength="1000" '
        + 'placeholder="Ej.: me duele la rodilla cuando subo escaleras"></textarea>'
        + '<button type="submit" class="boton" id="chat-enviar">Enviar</button>'
        + '</form></div>'
        + '<p class="campo__ayuda chat-ia__aviso">Orientación automática con IA: no reemplaza una consulta '
        + 'médica y lo que escribas se envía a un servicio externo. Ante una emergencia llamá al 107 o al 911.</p>';

    $('volver-lista').addEventListener('click', () => {
        chat.abierto = false;
        pasoEspecialidad();
    });

    const texto = $('chat-texto');
    texto.addEventListener('input', ajustarAlto);
    texto.addEventListener('keydown', (e) => {
        // Enter envia; Shift+Enter agrega un renglon.
        if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
            e.preventDefault();
            $('chat-formulario').requestSubmit();
        }
    });

    $('chat-formulario').addEventListener('submit', (e) => {
        e.preventDefault();
        const mensaje = texto.value.trim();
        if (!mensaje || chat.esperando) {
            return;
        }
        texto.value = '';
        ajustarAlto();
        enviarAlAsistente(mensaje);
    });

    dibujarChat();
    texto.focus();
}

function ajustarAlto() {
    const texto = $('chat-texto');
    if (!texto) {
        return;
    }
    texto.style.height = 'auto';
    texto.style.height = Math.min(texto.scrollHeight, 140) + 'px';
}

async function enviarAlAsistente(mensaje) {
    chat.mensajes.push({ rol: 'usuario', texto: mensaje });
    chat.esperando = true;
    dibujarChat();

    try {
        // Los avisos de error se muestran en el chat pero no se mandan al modelo.
        const conversacion = chat.mensajes
            .filter(m => !m.error)
            .map(m => ({ rol: m.rol, texto: m.texto }));
        const respuesta = await api.reservaOrientacion(conversacion);
        chat.mensajes.push({
            rol: 'asistente',
            texto: respuesta.mensaje,
            especialidad: respuesta.especialidad || null,
            urgente: !!respuesta.urgente
        });
    } catch (error) {
        chat.mensajes.push({ rol: 'asistente', texto: error.message, error: true });
    } finally {
        chat.esperando = false;
        dibujarChat();
        const texto = $('chat-texto');
        if (texto) {
            texto.focus();
        }
    }
}

function dibujarChat() {
    const caja = $('chat-mensajes');
    if (!caja) {
        return; // la persona ya paso a otro paso
    }

    caja.innerHTML = '';
    caja.appendChild(globo({ rol: 'asistente', texto: SALUDO }));
    chat.mensajes.forEach(mensaje => caja.appendChild(globo(mensaje)));

    if (chat.esperando) {
        const fila = document.createElement('div');
        fila.className = 'chat-ia__fila chat-ia__fila--asistente';
        fila.innerHTML = '<div class="chat-ia__globo chat-ia__escribiendo" aria-label="El asistente está escribiendo">'
            + '<span></span><span></span><span></span></div>';
        caja.appendChild(fila);
    }

    $('chat-enviar').disabled = chat.esperando;
    caja.scrollTop = caja.scrollHeight;
}

/** Un mensaje del chat. Todo el texto entra con textContent: nunca como HTML. */
function globo(mensaje) {
    const fila = document.createElement('div');
    fila.className = 'chat-ia__fila chat-ia__fila--' + mensaje.rol;

    const caja = document.createElement('div');
    caja.className = 'chat-ia__globo'
        + (mensaje.urgente ? ' chat-ia__globo--urgente' : '')
        + (mensaje.error ? ' chat-ia__globo--error' : '');

    const texto = document.createElement('p');
    texto.textContent = mensaje.texto;
    caja.appendChild(texto);

    if (mensaje.especialidad) {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'boton boton--chico chat-ia__elegir';
        boton.textContent = 'Elegir ' + mensaje.especialidad;
        boton.addEventListener('click', () =>
            ejecutar(() => api.reservaEspecialidad(mensaje.especialidad)));
        caja.appendChild(boton);
    }

    fila.appendChild(caja);
    return fila;
}

async function pasoProfesional() {
    const contenedor = $('paso');
    mostrarCargando(contenedor, 'Cargando profesionales...');

    const profesionales = await api.profesionales(estado.especialidad);
    contenedor.innerHTML = '<h2>Elegí un profesional</h2>'
        + '<p class="campo__ayuda u-mb-16">Profesionales de '
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
    mostrarCargando(contenedor, 'Buscando los días de atención...');

    // Que dias atiende el profesional y cuanto lugar queda en los proximos.
    let disponibilidad = [];
    try {
        disponibilidad = (await api.profesional(estado.profesionalId)).disponibilidades || [];
    } catch (error) {
        // Sin la agenda semanal queda el calendario solo, como antes.
    }
    const atiende = textoDisponibilidad(disponibilidad);
    const lugares = await lugaresPorDia(proximosDiasDeAtencion(disponibilidad),
        (dia) => api.horariosLibres(estado.profesionalId, dia));

    // Se conserva la fecha que ya estaba elegida si sirve; si no (hoy no
    // atiende, o ya no queda lugar), se arranca en el primer dia con lugar.
    const enLista = lugares.find(l => l.dia === fechaElegida);
    const sirve = fechaElegida >= hoyIso()
        && (disponibilidad.length === 0 || atiendeEseDia(disponibilidad, fechaElegida))
        && (!enLista || enLista.libres !== 0);
    if (!sirve) {
        const primero = lugares.find(l => l.libres > 0);
        if (primero) {
            fechaElegida = primero.dia;
        }
    }

    contenedor.innerHTML = '<h2>Elegí un horario disponible</h2>'
        + (atiende
            ? '<p class="campo__ayuda u-my-ayuda">' + esc(estado.nombreProfesional)
                + ' atiende ' + esc(atiende) + '.</p>'
                + '<div class="campo"><label>Próximos días de atención</label>'
                + htmlDiasDeAtencion(lugares, fechaElegida) + '</div>'
            : '<p class="campo__ayuda u-my-ayuda">' + esc(estado.nombreProfesional)
                + ' todavía no tiene días de atención cargados.</p>')
        + '<div class="campo u-ancho-240"><label for="fecha">'
        + (atiende ? 'Otra fecha' : 'Fecha') + '</label>'
        + '<input type="date" id="fecha" min="' + esc(hoyIso()) + '" value="' + esc(fechaElegida) + '"></div>'
        + '<div id="horarios"></div>';

    contenedor.querySelectorAll('.dia-atencion').forEach(boton => {
        boton.addEventListener('click', () => {
            fechaElegida = boton.dataset.dia;
            $('fecha').value = fechaElegida;
            marcarDiaElegido(contenedor, fechaElegida);
            cargarHorarios();
        });
    });

    async function cargarHorarios() {
        const caja = $('horarios');

        if (disponibilidad.length > 0 && !atiendeEseDia(disponibilidad, fechaElegida)) {
            caja.innerHTML = '<p class="campo__ayuda">' + esc(estado.nombreProfesional)
                + ' no atiende ese día. Atiende ' + esc(atiende) + '.</p>';
            return;
        }

        mostrarCargando(caja, 'Buscando horarios...');
        try {
            const horarios = await api.reservaHorarios(fechaElegida);
            if (horarios.length === 0) {
                caja.innerHTML = '<p class="campo__ayuda">No quedan horarios libres ese día. '
                    + 'Probá con otro de los días de atención.</p>';
                return;
            }
            caja.innerHTML = '<p class="campo__ayuda u-mb-10">'
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
        if (!e.target.value) {
            return;
        }
        fechaElegida = e.target.value;
        marcarDiaElegido(contenedor, fechaElegida);
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
    $('paso').innerHTML = '<div class="aviso aviso--exito u-mb-20">'
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

    // Reserva cancelada, sin iniciar o terminada: la proxima arranca con el chat limpio.
    if (estado.paso === 0 || estado.paso === 5) {
        reiniciarChat();
    }

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
