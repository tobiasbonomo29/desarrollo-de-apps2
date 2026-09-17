-- =============================================================================
--  MediConecta - creacion de la base y del usuario
-- =============================================================================
--
--  Solo hace falta si se usa un PostgreSQL instalado en la maquina en lugar del
--  contenedor de scripts/db/levantar-postgres.ps1 (el contenedor ya crea las
--  tres cosas al arrancar).
--
--  Ejecutar como superusuario:
--
--      psql -U postgres -f crear-base.sql
--
--  La contrasena de abajo es local y de desarrollo. En una instalacion real se
--  cambia aca y se pasa a configurar-wildfly.ps1 con el parametro -ClaveBase;
--  nunca queda escrita en el codigo Java.
-- =============================================================================

CREATE USER mediconecta WITH PASSWORD 'mediconecta';

CREATE DATABASE mediconecta WITH OWNER = mediconecta ENCODING = 'UTF8';

GRANT ALL PRIVILEGES ON DATABASE mediconecta TO mediconecta;

-- Las tablas las crea Hibernate al desplegar el WAR
-- (hibernate.hbm2ddl.auto=update en persistence.xml).
-- El esquema equivalente, por si se prefiere crearlo a mano, esta en esquema.sql
