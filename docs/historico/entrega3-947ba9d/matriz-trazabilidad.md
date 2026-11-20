# MediConecta — Matriz de trazabilidad (v1, checkpoint 3)

> Versión 1 — 18/11/2026. Relaciona cada requisito con la funcionalidad que lo implementa
> (archivos) y con las pruebas que lo verifican. Los identificadores de prueba están
> definidos en el [plan de pruebas](plan-de-pruebas.md). **Estado**: *Verificado* = hay una
> prueba ejecutada que pasa; *Parcial* = implementado pero con una parte sin ejecutar;
> *Pendiente* = no ejecutado.

## 1. Requisitos funcionales y de arquitectura (TP integrador)

| Id | Requisito | Implementación | Pruebas | Estado |
|---|---|---|---|---|
| RF-01 | Gestión de turnos (alta, consulta, cancelación, atención, archivado) | `negocio.GestionTurnos`, `presentacion.RecursoTurnos` | PU-03, PU-16, smoke §TURNOS | Verificado |
| RF-02 | Padrón de pacientes | `negocio.GestionPacientes`, `RecursoPacientes` | PU-04, smoke §PACIENTES | Verificado |
| RF-03 | Profesionales y disponibilidad | `negocio.GestionProfesionales`, `RecursoProfesionales` | PU-05, smoke §PROFESIONALES | Verificado |
| RF-04 | Reserva guiada (componente stateful) | `negocio.AsistenteReservaTurno`, `presentacion.ConversacionDeReserva`, `RecursoReserva` | PU-07, smoke §ASISTENTE | Verificado |
| RF-05 | Reserva como flujo transaccional de varios pasos | `negocio.FachadaReservas#reservar` (JTA + JMS XA) | PU-06, smoke | Verificado (rollback XA no forzado en prueba) |
| RF-06 | Orientación de especialidad con IA | `integraciones.GestorOrientacion`, `AdaptadorIaGroq` | PU-13, PU-14, smoke §Entrega 3 | Verificado (sin proveedor real) |
| RA-01 | Patrones State, Factory, Facade, Adapter, Repository | `modelo.estado`, `FabricaTurnos`, `FachadaReservas`, `integraciones.*`, `datos.*` | PU-01, PU-02, PU-06, PU-12 | Verificado |
| RA-02 | Integración SOAP con sistema legado | `AdaptadorObraSocialLegado`, `simulacion.ServicioObraSocialLegadoImpl` | PU-12, smoke §COBERTURA (WSDL + consulta) | Verificado |
| RA-03 | Integración REST con partner | `AdaptadorObraSocialPartner`, `simulacion.RecursoPartnerCobertura` | PU-12, PU-14, smoke §COBERTURA | Verificado |
| RA-04 | Cola punto a punto y tópico pub/sub | `mensajeria.*` | PU-15, smoke §MENSAJERIA | Verificado |
| RA-05 | Seguridad declarativa por roles | `@RolesAllowed` en EJB, `negocio.Roles`, `web.xml` | PU-18, smoke §SEGURIDAD | Verificado |

## 2. Checkpoint 3 (20/11)

| Id | Requisito | Implementación | Pruebas | Estado |
|---|---|---|---|---|
| C3-01 | Timeout de 5 s en integraciones | `integraciones.ProteccionIntegracion` (corte con `ManagedExecutorService`); timeouts de cliente en `AdaptadorObraSocialPartner`, `AdaptadorObraSocialLegado`, `AdaptadorIaGroq` | PU-11 (llamada lenta), PU-14 (timeout HTTP real), PU-13 | Verificado (unitario); en servidor solo el camino feliz |
| C3-02 | Fallback ante falla o indisponibilidad | `GestorCobertura` + `MemoriaCobertura` (último dato bueno / "sin verificar"); `GestorOrientacion` + `OrientadorPorPalabrasClave` | PU-12, PU-13, smoke (origen IA o REGLAS) | Verificado |
| C3-03 | Fallas externas sin bloquear el sistema | Circuit breaker en `ProteccionIntegracion`; adapters con `NOT_SUPPORTED` (sin transacción abierta durante la llamada remota) | PU-11, PU-12 | Verificado (unitario) |
| C3-04 | Consumidores idempotentes | `mensajeria.ConsumidorIdempotente`, tabla `mensajes_procesados`, propiedad `claveIdempotencia` | PU-15, smoke (`procesadosPorConsumidor`) | Verificado |
| C3-05 | Dead Letter Queue | `DestinosJms` (`ColaConfirmacionesDLQ`), `MonitorColaMuerta`, `scripts/wildfly/configurar-mensajeria.cli` | PS-JMS-1, smoke §DLQ | Verificado |
| C3-06 | Sin pérdida de mensajes ante reinicio | `DeliveryMode.PERSISTENT`, journal persistente, suscripciones durables | PS-JMS-2, PS-JMS-3 | Verificado (WildFly 36, plan B) |
| C3-07 | Recuperación de mensajes pendientes | ídem | PS-JMS-2, PS-JMS-3 | Verificado |
| C3-08 | Idempotency-Key | `FachadaReservas#reservarIdempotente`, `RegistroIdempotencia`, `RecursoTurnos#crear`, `LimpiezaClavesIdempotencia`, `js/api.js` | PU-06, PU-16, smoke | Verificado (tabla en PostgreSQL pendiente) |
| C3-09 | ETag y pedidos condicionales | `presentacion.Etiquetas`, `RecursoTurnos`, `RecursoPacientes` | PU-16, smoke (304, 412) | Verificado |
| C3-10 | Problem Details (RFC 9457) | `DetalleProblema`, `Problemas`, `Manejador*`, `MecanismoAutenticacionJwt` | PU-09, PU-16, PU-17, smoke | Verificado |
| C3-11 | Códigos HTTP consistentes | Mappers: 400/401/403/404/405/409/412/415/422/429/500/502 | PU-16, PU-17 | Verificado |
| C3-12 | IA por su gateway con timeout, errores y pruebas sin servicio real | `AdaptadorIaGroq`, `GestorOrientacion`, `OrientadorPorPalabrasClave` | PU-13, PU-14 | Verificado |
| C3-13 | CI: compila, testea, cobertura, informa errores, sin credenciales | `.github/workflows/ci.yml`, `mvnw`, JaCoCo en `pom.xml` | Ejecución local de `mvnw verify` | Parcial: falta la primera corrida en GitHub |
| C3-14 | Plan de pruebas, matriz, manual | `docs/plan-de-pruebas.md`, este documento, `docs/manual-usuario.md` | — | Hecho (v1) |

## 3. Entrega final

| Id | Requisito | Implementación / evidencia | Estado |
|---|---|---|---|
| EF-01 | Build reproducible | Maven Wrapper 3.9.9, versiones fijas, `.gitattributes`; build desde copia limpia con repositorio Maven vacío | Verificado |
| EF-02 | Pipeline de CI en verde | `ci.yml` | Pendiente (primera corrida) |
| EF-03 | Sin secretos en repositorio e historial | Secreto JWT fijo eliminado (aleatorio si no se configura); job gitleaks | Parcial (ver plan §7.3) |
| EF-04 | Cobertura ≥ 80 % líneas / ≥ 70 % ramas | JaCoCo: total 84,3 % / 83,1 %; capa de negocio 94,3 % / 90,2 % (sin `datos.jpa`) | Verificado |
| EF-05 | Mutation score ≥ 60 % | PIT: 77 % total, 85 % capa de negocio | Verificado |
| EF-06 | Quality Gate Sonar | job `sonar` | Pendiente (sin token) |
| EF-07 | Carga p95 < 500 ms | `scripts/carga/prueba-carga.py`: p95 110,7 ms, 0 errores | Parcial (plan B, falta PostgreSQL) |
| EF-08 | Testing de IA | PU-13, PU-14 | Verificado |
| EF-09 | BOLA/IDOR | `GestionTurnos#obtenerTurno`, `GestionPacientes#buscarPorDocumento`, `AccesoDenegadoException` → 403 | Verificado |
| EF-10 | BFLA | `@RolesAllowed` + PU-18 | Verificado |
| EF-11 | JWT inválidos | `ServicioJwt`, `MecanismoAutenticacionJwt` | Verificado |
| EF-12 | Inyecciones | JPQL parametrizado, documento validado (DNI numérico), `esc()`/`textContent` en el front; `pruebas-seguridad.py` + ZAP activo | Verificado (en plan B; JPQL real por revisión de código) |
| EF-13 | Fuerza bruta → 429 | `seguridad.ProteccionAbuso`, `LimitadorDeIntentos`, `ManejadorDemasiadosIntentos` | Verificado |
| EF-14 | OWASP ZAP sin High | ZAP 2.16.1 activo web + API autenticada (`scripts/seguridad/zap-api.yaml`): 0 High / 0 Medium / 0 Low en la aplicación tras corregir XSS reflejado, 500, CSP y SameSite | Verificado |
| EF-15 | Documentación honesta del uso de IA | `docs/declaracion-uso-ia.md` | Borrador: el equipo debe completarla |
| EF-16 | Sin defectos bloqueantes ni críticos abiertos | 7 defectos encontrados en la auditoría, todos corregidos y reverificados (plan de pruebas §5.5) | Verificado para lo ejecutable |
| EF-17 | Demo en vivo | Plan B documentado verificado; smoke 119/119; proveedor de IA simulado para mostrar fallback | Verificado (falta ensayo con PostgreSQL) |
