# MediConecta — Matriz de trazabilidad final (v2)

**Fecha:** 20/11/2026 · **Código:** `eda4b02` · **Reemplaza a:** `docs/Matriz_de_Trazabilidad.md` (v1).
✔ verificado con evidencia ejecutada · ◐ parcial · ✗ sin verificar. "Humo" = sección de `scripts/smoke-test.sh` (148/148 en CI run #4).

> **Pendiente:** el repositorio no contiene las HU originales (entrega del diseño 03/11 / Jira) con sus criterios Gherkin. La cadena exigida HU → criterio → caso → test se completa aquí desde **CU** y **RF**; falta mapear cada HU y criterio a estas filas.

## Requisitos funcionales

| Id | Requisito | CU | Componente | Tests automatizados | Resultado | Evidencia | Defecto | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-01 | Login y JWT | — | `RecursoAutenticacion`, `ServicioAutenticacion`, `ServicioJwt` | `ServicioAutenticacionTest`, `ServicioJwtTest`, humo SEGURIDAD | Pasa | CI calidad + humo | D-03 | ✔ |
| RF-02 | Ver agenda | CU-08 | `GestionTurnos` | `GestionTurnosTest`, humo TURNOS | Pasa | CI | — | ✔ |
| RF-03 | Reservar turno | CU-01 | `FachadaReservas`, `FabricaTurnos` | `FachadaReservasTest`, `FabricaTurnosTest`, humo TURNOS | Pasa | CI | — | ✔ |
| RF-04 | Sin superposición ni pasado | CU-01 | `GestionTurnos` | `GestionTurnosTest`, humo | Pasa | CI | — | ✔ |
| RF-05 | Cancelar/atender/archivar (State, 409) | CU-03, CU-04 | `modelo.estado`, `GestionTurnos` | `EstadoDelTurnoTest`, `GestionTurnosTest`, PIT, humo STATE | Pasa | CI | — | ✔ |
| RF-06 | Reserva guiada `@Stateful` | CU-02 | `AsistenteReservaTurno` | `AsistenteReservaTurnoTest`, humo ASISTENTE | Pasa | CI | D-05 | ✔ |
| RF-07 | Padrón, ficha, historial | CU-05 | `GestionPacientes` | `GestionPacientesTest`, humo PACIENTES | Pasa | CI | D-02, D-08 | ✔ |
| RF-08 | Profesionales y disponibilidad | CU-06 | `GestionProfesionales` | `GestionProfesionalesTest`, humo PROFESIONALES | Pasa | CI | — | ✔ |
| RF-09 | Cobertura SOAP/REST | CU-07 | `GestorCobertura`, adaptadores | `GestorCoberturaTest`, `FallbacksTest`, `TiemposDeEsperaTest`, humo COBERTURA | Pasa | CI | D-06 | ✔ |
| RF-10 | Orientación con IA | CU-02 | `GestorOrientacion`, `AdaptadorIaGroq` | `GestorOrientacionTest`, `AdaptadorIaGroqTest`, `FallbacksTest`, humo IA; `evaluar-ia.sh` | Unitarias pasan; real sin evidencia versionada | CI | — | ◐ |
| RF-11 | Avisos JMS | CU-09 | `NotificadorTurnos`, MDB, `GestionNotificaciones` | `ConsumoDeEventosTest`, `GestionNotificacionesTest`, `EventoTurnoTest`, humo MENSAJERIA | Pasa | CI | D-04 | ✔ |
| RF-12 | Tablero | CU-08 | `FachadaReservas.resumenDelDia` | `FachadaReservasTest`, k6 | Pasa | CI | — | ✔ |
| RF-13 | Mensajes fallidos | CU-09 | `RecursoMensajeria`, `MonitorMensajesFallidos` | `ConsumoDeEventosTest`, humo | Pasa | CI | — | ✔ |
| RF-14 | Interfaz por rol | todos | `src/main/webapp` | — (manual) | Manual | — | O-03 | ◐ |

## Requisitos no funcionales

| Id | Requisito | Evidencia | Estado |
|---|---|---|---|
| RNF-01 | SOAP, REST y JMS funcionando | Humo COBERTURA, MENSAJERIA | ✔ |
| RNF-02 | Timeout 5 s + fallback | `FallbacksTest`, `TiemposDeEsperaTest`, humo IA | ✔ (sin reintento/circuit breaker: O-01) |
| RNF-03 | Consumidor idempotente | `GestionNotificacionesTest` | ✔ |
| RNF-04 | Reintentos y DLQ (4 intentos) | `ConsumoDeEventosTest`, humo | ✔ |
| RNF-05 | Broker caído sin pérdida | Procedimiento manual 19/11 (no reejecutado) | ◐ |
| RNF-06 | Idempotency-Key | `FachadaReservasTest`, humo | ✔ |
| RNF-07 | ETag / 412 | `EtiquetasTest`, humo ETAG | ✔ |
| RNF-08 | Problem Details | `ProblemasTest`, humo | ✔ |
| RNF-09 | Gateway de IA | `GestorOrientacionTest`, `FallbacksTest` | ✔ |
| RNF-10 | CI con cobertura | Run #4 | ✔ |
| RNF-11 | Build reproducible (G1) | Docker Compose en CI | ✔ (en CI) |
| RNF-12 | CI en verde (G3) | Run #4, 6 jobs | ✔ en la rama (falta sobre el tag) |
| RNF-13 | Sin secretos (G6) | gitleaks 0 | ✔ (riesgo residual D-03) |
| RNF-14 | Cobertura negocio 80/70 | 95,0 % / 82,6 % | ✔ |
| RNF-15 | Mutación ≥ 60 % | 85 % | ✔ |
| RNF-16 | Quality Gate Sonar | No ejecutado | ✗ |
| RNF-17 | Carga p95 < 500 ms (50 usuarios × 5 min) | 22,18 ms con 20 usuarios × 45 s | ◐ |
| RNF-18 | BOLA/IDOR 403 | Humo seguridad en vivo | ✔ |
| RNF-19 | BFLA 403 | Humo | ✔ |
| RNF-20 | JWT inválidos 401 | `ServicioJwtTest`, humo | ✔ |
| RNF-21 | Inyección SQL/XSS | Humo | ✔ |
| RNF-22 | 429 fuerza bruta | Tests + humo | ✔ |
| RNF-23 | ZAP 0 High | Baseline 0 FAIL; sin activo autenticado | ◐ |
| RNF-24 | Testing de IA | Unitarias ✔; real ◐ | ◐ |
| RNF-25 | 0 Bloqueantes/Críticos abiertos | Registro de defectos | ✔ |
| RNF-26 | E2E de interfaz (≥ 5 escenarios) | No existe | ✗ |
| RNF-27 | Testcontainers/Arquillian | No existe | ✗ |
| RNF-28 | XXE en SOAP | Sin prueba | ✗ |
| RNF-29 | SCA de dependencias | Sin escaneo | ✗ |
