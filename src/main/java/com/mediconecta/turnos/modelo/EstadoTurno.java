package com.mediconecta.turnos.modelo;

/**
 * Estados posibles de un turno medico dentro de MediConecta.
 *
 * <p>Se modela como enum y no como String libre para que el estado sea un
 * valor cerrado y validado por el compilador: no se puede crear un turno con
 * un estado inventado.</p>
 */
public enum EstadoTurno {

    /** Turno reservado y vigente. Es el estado con el que nace todo turno. */
    PROGRAMADO,

    /** El paciente se presento y fue atendido. */
    ATENDIDO,

    /** El turno fue dado de baja por el paciente o por el centro medico. */
    CANCELADO
}
