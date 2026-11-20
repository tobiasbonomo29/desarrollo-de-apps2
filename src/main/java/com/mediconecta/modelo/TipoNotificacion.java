package com.mediconecta.modelo;

/** Que aviso genero cada consumidor JMS a partir de un turno confirmado. */
public enum TipoNotificacion {

    /** Lo genera el consumidor de la cola: el mail de confirmacion al paciente. */
    CONFIRMACION,

    /** Lo genera un suscriptor del topico: el recordatorio al paciente, 24 h antes. */
    RECORDATORIO,

    /** Lo genera el otro suscriptor del topico: el aviso en la agenda del profesional. */
    AVISO_PROFESIONAL
}
