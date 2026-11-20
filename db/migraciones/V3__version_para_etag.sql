-- =============================================================================
--  MediConecta - V3: version para concurrencia optimista (ETag / If-Match)
-- =============================================================================
--
--  JPA @Version en turnos y pacientes. Cada modificacion incrementa la version
--  y la API la publica en el ETag; un PUT con If-Match de una version vieja
--  responde 412 en lugar de pisar el cambio de otro usuario.
-- =============================================================================

ALTER TABLE turnos    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pacientes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
