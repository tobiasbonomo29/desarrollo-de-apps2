# MediConecta — Matriz de trazabilidad (v1)
> **Versión superada (v1, 19/11/2026).** La versión final, basada en la rama `integracion-final`, está en [`documentacion-final/`](../documentacion-final/README.md).

**Versión:** 1.0 · **Fecha:** 19/11/2026. Relaciona cada requisito con el componente que lo
implementa y con las pruebas que lo verifican. Los tipos de prueba (P1…P13) son los del
[plan de pruebas](Plan_de_Pruebas.md). "Humo" = sección de `scripts/smoke-test.sh`.

Estado: ✔ verificado (la prueba existe y pasa) · ◐ verificado parcialmente · ✗ sin verificar.

## Requisitos funcionales

| Id | Requisito | Componente | Pruebas | Estado |
|---|---|---|---|---|
| RF-01 | Iniciar sesión y obtener un JWT | `RecursoAutenticacion`, `ServicioAutenticacion`, `ServicioJwt` | P1 `ServicioAutenticacionTest`, `ServicioJwtTest` · P7 Humo "SEGURIDAD" | ✔ |
| RF-02 | Ver la agenda de turnos (RECEPCION, MEDICO, ADMIN) | `GestionTurnos.listarTurnos` | P1 `GestionTurnosTest` · P7 Humo "TURNOS" | ✔ |
| RF-03 | Reservar un turno directo | `FachadaReservas.reservar`, `FabricaTurnos` | P1 `FachadaReservasTest`, `FabricaTurnosTest` · P7 Humo "TURNOS" | ✔ |
| RF-04 | No superponer turnos de un profesional ni agendar en el pasado | `GestionTurnos.agendar` | P1 `GestionTurnosTest` · P7 Humo "TURNOS" | ✔ |
| RF-05 | Cancelar, atender y archivar turnos según el estado (patrón State) | `modelo.estado.*`, `GestionTurnos` | P1 `EstadoDelTurnoTest`, `GestionTurnosTest` · P4 PIT · P7 Humo "PATRON STATE" | ✔ |
| RF-06 | Reserva guiada en pasos con memoria del servidor (`@Stateful`) | `AsistenteReservaTurno`, `ConversacionDeReserva` | P1 `AsistenteReservaTurnoTest` · P7 Humo "ASISTENTE" | ✔ |
| RF-07 | Padrón de pacientes: alta, búsqueda, edición e historial | `GestionPacientes` | P1 `GestionPacientesTest` · P7 Humo "PACIENTES" | ✔ |
| RF-08 | Profesionales, disponibilidad semanal y horarios libres | `GestionProfesionales` | P1 `GestionProfesionalesTest` · P7 Humo "PROFESIONALES" | ✔ |
| RF-09 | Validar la cobertura médica (SOAP legado / REST partner) | `GestorCobertura`, adaptadores | P1 `GestorCoberturaTest`, `FallbacksTest`, `TiemposDeEsperaTest` · P7 Humo "COBERTURA" | ✔ |
| RF-10 | Orientación de especialidad con IA | `GestorOrientacion` (gateway), `AdaptadorIaGroq` | P1 `GestorOrientacionTest`, `AdaptadorIaGroqTest`, `FallbacksTest` · P11 `evaluar-ia.sh` · P7 Humo "ASISTENTE DE IA" | ✔ |
| RF-11 | Avisos al confirmar un turno (mail, recordatorio, agenda del profesional) | `NotificadorTurnos`, 3 MDB, `GestionNotificaciones` | P1 `ConsumoDeEventosTest`, `GestionNotificacionesTest`, `EventoTurnoTest` · P7 Humo "MENSAJERIA" | ✔ |
| RF-12 | Tablero del día | `FachadaReservas.resumenDelDia` | P1 `FachadaReservasTest.elTableroResumeElDia` · P9 k6 | ✔ |
| RF-13 | Consultar mensajes fallidos (ADMIN) | `RecursoMensajeria`, `MonitorMensajesFallidos` | P1 `ConsumoDeEventosTest`, `GestionNotificacionesTest` · P7 Humo "MENSAJERIA" | ✔ |
| RF-14 | Interfaz web por rol | `src/main/webapp` | P13 recorrido del [manual](Manual_de_Usuario.md) · P8 ZAP | ◐ (sin tests automáticos de UI) |

## Requisitos no funcionales — resiliencia e integración (3.er checkpoint)

| Id | Requisito | Componente | Pruebas | Estado |
|---|---|---|---|---|
| RNF-01 | Integraciones SOAP, REST y mensajería funcionando | adaptadores, `NotificadorTurnos`, MDB | P7 Humo "COBERTURA", "MENSAJERIA" | ✔ |
| RNF-02 | Timeout de 5 s con fallback | `TiemposDeEspera`, `GestorCobertura.respaldo`, `CacheCobertura`, `GestorOrientacion` + `OrientacionDeRespaldo` | P1 `FallbacksTest`, `TiemposDeEsperaTest` · P10 procedimiento "Timeout de 5 s" | ✔ |
| RNF-03 | Consumidores idempotentes | `GestionNotificaciones` (`eventoId`, `tipo`) | P1 `GestionNotificacionesTest.elMismoEventoDosVecesGeneraUnSoloAviso` | ✔ |
| RNF-04 | Reintentos y DLQ | `ConsumoDeEventos`, `configurar-mensajeria.cli`, `MonitorMensajesFallidos` | P1 `ConsumoDeEventosTest` · P7 Humo "MENSAJERIA" (mensaje defectuoso → DLQ) | ✔ |
| RNF-05 | Si se apaga el broker no se pierden mensajes | mensajes persistentes, suscripciones durables, envío XA | P10 procedimiento "Broker apagado" (probado 19/11) | ✔ (manual) |
| RNF-06 | `Idempotency-Key` en la API | `FachadaReservas.reservarConClave` | P1 `FachadaReservasTest` · P7 Humo "IDEMPOTENCY-KEY" | ✔ |
| RNF-07 | ETag en la API | `Etiquetas`, `@Version`, `RecursoTurnos`, `RecursoPacientes` | P1 `EtiquetasTest`, `GestionTurnosTest`, `GestionPacientesTest` · P7 Humo "ETAG" | ✔ |
| RNF-08 | Errores en Problem Details | `Problemas`, `TipoProblema`, `Manejador*` | P1 `ProblemasTest`, `MecanismoAutenticacionJwtTest` · P7 Humo "PROBLEM DETAILS" | ✔ |
| RNF-09 | IA a través de su gateway | `GestorOrientacion` (validación, límite, filtro, fallback) | P1 `FallbacksTest` · P11 | ✔ |
| RNF-10 | Pipeline de CI con reporte de cobertura | `.github/workflows/ci.yml` | Job `calidad` (resumen y artefacto JaCoCo) | ✔ |

## Requisitos no funcionales — entrega final

| Id | Requisito / umbral | Evidencia | Estado |
|---|---|---|---|
| RNF-11 | Build reproducible (G1) | `./mvnw` (Maven 3.9.9 fijado), enforcer (Maven ≥ 3.9, JDK ≥ 17, todos los plugins con versión), `Dockerfile` | ✔ |
| RNF-12 | Pipeline de CI en verde (G3) | GitHub Actions, 6 jobs | ✔ |
| RNF-13 | Sin secretos en el historial (G5) | Job `secretos` (gitleaks sobre todos los commits) | ✔ |
| RNF-14 | Cobertura de negocio ≥ 80 % líneas y ≥ 70 % ramas | JaCoCo, regla `PACKAGE` en `pom.xml` | ✔ (95,0 % / 82,5 %) |
| RNF-15 | Mutation score ≥ 60 % | PIT, job `mutacion` | ✔ (85 %) |
| RNF-16 | Quality Gate de Sonar aprobado | Job `sonar` | ✗ falta configurar SonarCloud |
| RNF-17 | Carga: p95 < 500 ms | k6 en el job `punta-a-punta` | ✔ (35,6 ms local) |
| RNF-18 | BOLA/IDOR | Humo "SEGURIDAD EN VIVO"; `GestionTurnosTest`, `GestionPacientesTest` | ✔ |
| RNF-19 | BFLA | Humo "SEGURIDAD" y "SEGURIDAD EN VIVO" (`@RolesAllowed`) | ✔ |
| RNF-20 | JWT inválidos | Humo: firma alterada, `alg:none`, firma ajena, mal formado; `ServicioJwtTest` (vencido) | ✔ |
| RNF-21 | Inyecciones | Humo: SQL en búsqueda, ruta y login; XSS almacenado devuelto como JSON y escapado en el front (`esc()`) | ✔ |
| RNF-22 | 429 ante fuerza bruta | `ServicioAutenticacion`, `LimitadorDeFrecuencia`; `ServicioAutenticacionTest`, `LimitadorDeFrecuenciaTest`, humo | ✔ |
| RNF-23 | ZAP sin alertas High | Job `punta-a-punta` (falla si hay High) | ✔ (0 High, 0 Medium) |
| RNF-24 | Testing de la funcionalidad de IA | `pruebas/ia/` + unitarias | ✔ |
| RNF-25 | Sin defectos Bloqueantes ni Críticos abiertos | [Plan de pruebas §8](Plan_de_Pruebas.md) | ✔ |
| RNF-26 | Documentación completa | README, arquitectura, plan, matriz, manual, informe, declaración de IA | ◐ (v1; se completa para la entrega final) |
