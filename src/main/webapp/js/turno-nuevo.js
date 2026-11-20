/**
 * Alta directa de un turno.
 *
 * Todos los desplegables se llenan con datos reales de la API: los pacientes
 * salen de Gestion de Pacientes, las especialidades y profesionales de Gestion
 * de Profesionales, y los horarios de la disponibilidad real cruzada con los
 * turnos ya tomados. No hay ni un dato escrito a mano.
 */

import { api, contexto, nuevaClaveIdempotencia } from './api.js';
import { iniciarLayout, tieneRol, reportar } from './layout.js';
import {
    esc, hora, hoyIso, fechaLarga, avisoExito, mensajeEnCaja,
    atiendeEseDia, proximosDiasDeAtencion, textoDisponibilidad, lugaresPorDia,
    htmlDiasDeAtencion, marcarDiaElegido
} from './ui.js';

const elegido = { documento: null, nombre: null, especialidad: null, profesionalId: null, profesional: null, fechaHora: null };

// Disponibilidad semanal de cada profesional de la especialidad elegida, por id.
const agendas = new Map();

const $ = (id) => document.getElementById(id);

function resumen() {
    const filas = [
        ['r-paciente', elegido.nombre],
        ['r-especialidad', elegido.especialidad],
        ['r-profesional', elegido.profesional],
        ['r-fecha', elegido.fechaHora ? fechaLarga(elegido.fechaHora) + ' hs' : null]
    ];

    filas.forEach(([id, valor]) => {
        const celda = $(id);
        celda.textContent = valor || 'Sin seleccionar';
        celda.classList.toggle('vacio', !valor);
    });

    $('confirmar').disabled = !(elegido.documento && elegido.profesionalId && elegido.fechaHora);
}

async function cargarPacientes(sesion) {
    const select = $('paciente');

    // Un PACIENTE no puede leer el padron completo: solo reserva para si mismo.
    if (!tieneRol(sesion, 'RECEPCION', 'MEDICO', 'ADMIN')) {
        const ficha = await api.miFicha();
        select.innerHTML = '<option value="' + esc(ficha.documento) + '">'
            + esc(ficha.nombre) + '</option>';
        select.value = ficha.documento;
        select.disabled = true;
        elegido.documento = ficha.documento;
        elegido.nombre = ficha.nombre;
        $('ayuda-paciente').textContent = 'Como paciente, solo podés reservar turnos para vos.';
        return;
    }

    const pacientes = await api.pacientes();
    pacientes.forEach(p => {
        const opcion = document.createElement('option');
        opcion.value = p.documento;
        opcion.textContent = p.nombre + ' — DNI ' + p.documento;
        select.appendChild(opcion);
    });
}

async function cargarEspecialidades() {
    const select = $('especialidad');
    const especialidades = await api.especialidades();
    especialidades.forEach(nombre => {
        const opcion = document.createElement('option');
        opcion.value = nombre;
        opcion.textContent = nombre;
        select.appendChild(opcion);
    });
}

async function cargarProfesionales(especialidad) {
    const select = $('profesional');
    select.innerHTML = '<option value="">Seleccioná un profesional</option>';
    select.disabled = true;

    if (!especialidad) {
        select.innerHTML = '<option value="">Elegí primero la especialidad</option>';
        return;
    }

    const profesionales = await api.profesionales(especialidad);
    agendas.clear();
    profesionales.filter(p => p.activo).forEach(p => {
        agendas.set(String(p.id), p.disponibilidades || []);
        const opcion = document.createElement('option');
        opcion.value = p.id;
        opcion.textContent = p.nombre + ' — Mat. ' + p.matricula;
        select.appendChild(opcion);
    });
    select.disabled = false;
}

function limpiarHorarios(mensaje) {
    $('horarios').innerHTML = '<p class="campo__ayuda">' + esc(mensaje) + '</p>';
    $('disponible').hidden = true;
    elegido.fechaHora = null;
    resumen();
}

/**
 * Muestra que dias atiende el profesional elegido y botones con los proximos,
 * y arranca en el primero que todavia tiene lugar.
 */
async function mostrarDiasDeAtencion() {
    const caja = $('dias-atencion');
    const id = elegido.profesionalId;
    const disponibilidad = agendas.get(String(id)) || [];

    if (!id) {
        caja.innerHTML = '<p class="campo__ayuda">Elegí un profesional para ver qué días atiende.</p>';
        return;
    }
    if (disponibilidad.length === 0) {
        caja.innerHTML = '<p class="campo__ayuda">' + esc(elegido.profesional)
            + ' todavía no tiene días de atención cargados.</p>';
        return;
    }

    caja.innerHTML = '<div class="cargando">Buscando los días de atención...</div>';
    const lugares = await lugaresPorDia(proximosDiasDeAtencion(disponibilidad),
        (dia) => api.horariosLibres(id, dia));
    if (elegido.profesionalId !== id) {
        return; // mientras tanto se eligio otro profesional
    }

    const primero = lugares.find(l => l.libres > 0);
    caja.innerHTML = '<p class="campo__ayuda u-mb-8">' + esc(elegido.profesional)
        + ' atiende ' + esc(textoDisponibilidad(disponibilidad)) + '.</p>'
        + htmlDiasDeAtencion(lugares, primero ? primero.dia : null);

    caja.querySelectorAll('.dia-atencion').forEach(boton => {
        boton.addEventListener('click', () => {
            $('fecha').value = boton.dataset.dia;
            marcarDiaElegido(caja, boton.dataset.dia);
            cargarHorarios();
        });
    });

    if (primero) {
        $('fecha').value = primero.dia;
        cargarHorarios();
    }
}

async function cargarHorarios() {
    const fecha = $('fecha').value;
    if (!elegido.profesionalId || !fecha) {
        limpiarHorarios('Elegí profesional y fecha para ver los horarios libres.');
        return;
    }

    const disponibilidad = agendas.get(String(elegido.profesionalId)) || [];
    if (disponibilidad.length > 0 && !atiendeEseDia(disponibilidad, fecha)) {
        limpiarHorarios(elegido.profesional + ' no atiende ese día. Atiende '
            + textoDisponibilidad(disponibilidad) + '.');
        return;
    }

    $('horarios').innerHTML = '<div class="cargando">Buscando horarios...</div>';

    let horarios;
    try {
        horarios = await api.horariosLibres(elegido.profesionalId, fecha);
    } catch (error) {
        reportar(error);
        limpiarHorarios(error.message);
        return;
    }

    if (horarios.length === 0) {
        limpiarHorarios('No quedan horarios libres ese día. Probá con otro de los días de atención.');
        return;
    }

    $('horarios').innerHTML = '<div class="grilla-horarios">'
        + horarios.map(h => '<button type="button" class="horario" data-valor="' + esc(h) + '">'
            + esc(hora(h)) + '</button>').join('')
        + '</div>';

    $('horarios').querySelectorAll('.horario').forEach(boton => {
        boton.addEventListener('click', () => {
            $('horarios').querySelectorAll('.horario')
                .forEach(b => b.classList.remove('horario--elegido'));
            boton.classList.add('horario--elegido');
            elegido.fechaHora = boton.dataset.valor;

            const aviso = $('disponible');
            aviso.hidden = false;
            aviso.textContent = 'Horario disponible: ' + fechaLarga(elegido.fechaHora)
                + ' hs con ' + elegido.profesional + '.';
            resumen();
        });
    });

    $('disponible').hidden = true;
    elegido.fechaHora = null;
    resumen();
}

async function iniciar() {
    const sesion = await iniciarLayout();

    // MEDICO no crea turnos directos: solo recepcion y administracion.
    if (tieneRol(sesion, 'MEDICO') && !tieneRol(sesion, 'RECEPCION', 'ADMIN')) {
        $('sin-permiso').hidden = false;
        $('form-turno').hidden = true;
        return;
    }

    try {
        await cargarPacientes(sesion);
        await cargarEspecialidades();
    } catch (error) {
        reportar(error);
        mensajeEnCaja($('mensaje'), error.message, 'error');
        return;
    }

    $('fecha').min = hoyIso();

    $('paciente').addEventListener('change', (e) => {
        const opcion = e.target.selectedOptions[0];
        elegido.documento = e.target.value || null;
        elegido.nombre = (opcion && opcion.value) ? opcion.textContent.split(' — ')[0] : null;
        resumen();
    });

    $('especialidad').addEventListener('change', async (e) => {
        elegido.especialidad = e.target.value || null;
        elegido.profesionalId = null;
        elegido.profesional = null;
        $('fecha').disabled = true;
        $('fecha').value = '';
        mostrarDiasDeAtencion();
        limpiarHorarios('Elegí profesional y fecha para ver los horarios libres.');
        try {
            await cargarProfesionales(elegido.especialidad);
        } catch (error) {
            reportar(error);
        }
        resumen();
    });

    $('profesional').addEventListener('change', (e) => {
        const opcion = e.target.selectedOptions[0];
        elegido.profesionalId = e.target.value || null;
        elegido.profesional = (opcion && opcion.value) ? opcion.textContent.split(' — ')[0] : null;
        $('fecha').disabled = !elegido.profesionalId;
        $('fecha').value = '';
        limpiarHorarios('Elegí una fecha para ver los horarios libres.');
        resumen();
        mostrarDiasDeAtencion();
    });

    $('fecha').addEventListener('change', () => {
        marcarDiaElegido($('dias-atencion'), $('fecha').value);
        cargarHorarios();
    });

    // Idempotency-Key: el MISMO pedido reintentado (doble clic, se corto la red
    // y se vuelve a apretar) viaja con la misma clave y el servidor no crea un
    // segundo turno. Si el usuario cambia algun dato, es otro pedido: otra clave.
    let ultimoIntento = null;
    function claveParaElPedido(pedido) {
        const huella = JSON.stringify(pedido);
        if (!ultimoIntento || ultimoIntento.huella !== huella) {
            ultimoIntento = { huella, clave: nuevaClaveIdempotencia() };
        }
        return ultimoIntento.clave;
    }

    $('form-turno').addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const boton = $('confirmar');
        boton.disabled = true;
        boton.textContent = 'Confirmando...';
        mensajeEnCaja($('mensaje'), '');

        const pedido = {
            documentoPaciente: elegido.documento,
            profesionalId: Number(elegido.profesionalId),
            especialidad: elegido.especialidad,
            fechaHora: elegido.fechaHora,
            motivo: $('motivo').value
        };

        try {
            const turno = await api.crearTurno(pedido, claveParaElPedido(pedido));
            avisoExito('Turno #' + turno.id + ' agendado.');
            window.location.href = contexto + '/app/turno.html?id=' + encodeURIComponent(turno.id);
        } catch (error) {
            mensajeEnCaja($('mensaje'), error.message, 'error');
            boton.disabled = false;
            boton.textContent = 'Confirmar turno';
        }
    });

    resumen();
}

iniciar();
