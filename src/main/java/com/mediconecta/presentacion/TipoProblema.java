package com.mediconecta.presentacion;

/**
 * Catalogo de los tipos de error de la API, con su codigo HTTP.
 *
 * <p>Cada uno se publica en el campo {@code type} de Problem Details (RFC 9457)
 * como {@code urn:mediconecta:problema:<identificador>}. El cliente puede
 * decidir que hacer leyendo {@code type}, sin depender del texto de
 * {@code detail}, que es para personas y puede cambiar. La lista completa esta
 * documentada en {@code openapi.json} y en el README.</p>
 */
public enum TipoProblema {

    REGLA_NEGOCIO(400, "regla-negocio", "Regla de negocio incumplida"),
    PEDIDO_INVALIDO(400, "pedido-invalido", "Pedido mal formado"),
    NO_AUTENTICADO(401, "no-autenticado", "No autenticado"),
    CREDENCIALES_INVALIDAS(401, "credenciales-invalidas", "Credenciales invalidas"),
    ACCESO_DENEGADO(403, "acceso-denegado", "Acceso denegado"),
    NO_ENCONTRADO(404, "no-encontrado", "Recurso inexistente"),
    TRANSICION_INVALIDA(409, "transicion-invalida", "Transicion de estado invalida"),
    IDEMPOTENCIA_EN_CURSO(409, "idempotencia-en-curso", "Pedido repetido en curso"),
    IDEMPOTENCIA_REUTILIZADA(422, "idempotencia-reutilizada", "Idempotency-Key reutilizada con otro pedido"),
    MODIFICACION_CONCURRENTE(409, "modificacion-concurrente", "El recurso fue modificado al mismo tiempo"),
    PRECONDICION_FALLIDA(412, "precondicion-fallida", "La version del recurso cambio"),
    DEMASIADAS_SOLICITUDES(429, "demasiadas-solicitudes", "Demasiadas solicitudes"),
    ERROR_INTERNO(500, "error-interno", "Error interno"),
    INTEGRACION_EXTERNA(502, "integracion-externa", "Fallo de un sistema externo"),
    TIEMPO_AGOTADO(504, "tiempo-agotado", "Un sistema externo no respondio a tiempo");

    /** Prefijo comun de los identificadores de tipo. */
    public static final String PREFIJO = "urn:mediconecta:problema:";

    private final int estado;

    private final String identificador;

    private final String titulo;

    TipoProblema(int estado, String identificador, String titulo) {
        this.estado = estado;
        this.identificador = identificador;
        this.titulo = titulo;
    }

    public int estado() {
        return estado;
    }

    public String uri() {
        return PREFIJO + identificador;
    }

    public String titulo() {
        return titulo;
    }
}
