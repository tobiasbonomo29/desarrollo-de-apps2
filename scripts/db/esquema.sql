-- =============================================================================
--  MediConecta - esquema de la base de datos
-- =============================================================================
--
--  Este archivo es DOCUMENTACION del modelo, no un paso obligatorio: al
--  desplegar el WAR, Hibernate crea o actualiza estas mismas tablas por su
--  cuenta (hibernate.hbm2ddl.auto=update en persistence.xml).
--
--  Sirve para dos cosas:
--    * ver el modelo relacional de un vistazo durante la defensa;
--    * crear el esquema a mano si se prefiere no dejar que Hibernate toque el
--      DDL (en ese caso, poner hbm2ddl.auto en "validate").
--
--      psql -U mediconecta -d mediconecta -f esquema.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS pacientes (
    id         BIGSERIAL PRIMARY KEY,
    documento  VARCHAR(20)  NOT NULL UNIQUE,
    nombre     VARCHAR(120) NOT NULL,
    email      VARCHAR(120),
    telefono   VARCHAR(40),
    cobertura  VARCHAR(60),
    usuario    VARCHAR(60),
    activo     BOOLEAN      NOT NULL DEFAULT TRUE
);

COMMENT ON COLUMN pacientes.documento IS 'Clave de negocio: es lo que identifica al paciente en las URLs de la API.';
COMMENT ON COLUMN pacientes.usuario   IS 'Usuario de WildFly asociado; permite que un rol PACIENTE vea solo su propia ficha.';

CREATE TABLE IF NOT EXISTS profesionales (
    id           BIGSERIAL PRIMARY KEY,
    nombre       VARCHAR(120) NOT NULL,
    especialidad VARCHAR(80)  NOT NULL,
    matricula    VARCHAR(30)  NOT NULL UNIQUE,
    email        VARCHAR(120),
    activo       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS disponibilidades (
    id               BIGSERIAL PRIMARY KEY,
    profesional_id   BIGINT      NOT NULL REFERENCES profesionales (id) ON DELETE CASCADE,
    dia_semana       VARCHAR(12) NOT NULL,
    hora_desde       TIME        NOT NULL,
    hora_hasta       TIME        NOT NULL,
    duracion_minutos INTEGER     NOT NULL DEFAULT 30
);

COMMENT ON TABLE disponibilidades IS 'Franjas semanales de atencion. De aca sale la grilla de horarios que se ofrece al paciente.';

CREATE TABLE IF NOT EXISTS turnos (
    id             BIGSERIAL PRIMARY KEY,
    paciente_id    BIGINT      NOT NULL REFERENCES pacientes (id),
    profesional_id BIGINT      NOT NULL REFERENCES profesionales (id),
    especialidad   VARCHAR(80) NOT NULL,
    fecha_hora     TIMESTAMP   NOT NULL,
    estado         VARCHAR(20) NOT NULL,
    motivo         VARCHAR(200)
);

COMMENT ON COLUMN turnos.estado IS 'PROGRAMADO, ATENDIDO o CANCELADO. Lo fija siempre la capa de negocio.';

-- Indices que acompanan las consultas que hace la aplicacion.
CREATE INDEX IF NOT EXISTS ix_turnos_fecha        ON turnos (fecha_hora);
CREATE INDEX IF NOT EXISTS ix_turnos_profesional  ON turnos (profesional_id, fecha_hora);
CREATE INDEX IF NOT EXISTS ix_turnos_paciente     ON turnos (paciente_id);
CREATE INDEX IF NOT EXISTS ix_disp_profesional    ON disponibilidades (profesional_id);
