# MediConecta — Mejoras a partir de la corrección de la 2.ª entrega

Respuesta, punto por punto, a la devolución de la 2.ª entrega parcial (13/11). Para cada
observación: qué se hizo, dónde está y cómo comprobarlo.

**Cómo verificar todo de una vez**

```bash
mvn verify                                   # linter + 171 tests + cobertura mínima (80 %)
cp .env.example .env && docker compose up -d --build
scripts/smoke-test.sh                        # 123 pruebas de punta a punta
```

El pipeline de CI ([`.github/workflows/ci.yml`](../.github/workflows/ci.yml)) corre esos
mismos pasos en cada push.

---

## 1. Mensajería

> *Los mensajes JMS van en texto plano y los consumidores sólo loguean; faltan
> idempotencia, reintentos y DLQ.*

| Observación | Qué se hizo | Dónde |
|---|---|---|
| Texto plano | El mensaje es un **JSON con contrato y versión** (`eventoId`, `tipo`, `version`, `ocurridoEn`, `turnoId`, `pacienteId`, `profesionalId`, `especialidad`, `fechaHora`). El consumidor valida el contrato y **rechaza versiones que no conoce**. Lleva ids y no datos personales. Los datos clave van también como propiedades JMS para poder filtrar con selectores. Mensajes persistentes. | `mensajeria.EventoTurno`, `NotificadorTurnos` |
| Consumidores que solo loguean | Cada consumidor hace trabajo real y deja un registro en la tabla `notificaciones`: la **cola** registra el mail de confirmación; un suscriptor del **tópico** programa el recordatorio por SMS 24 h antes; el otro registra el aviso en la agenda del profesional. Se ven en el detalle del turno ("Avisos enviados") y en `GET /api/turnos/{id}/notificaciones`. | `negocio.GestionNotificaciones`, `app/turno.html` |
| Idempotencia | **Consumidor idempotente**: cada aviso se identifica por `(eventoId, tipo)`. Antes de generarlo se consulta; si ya existe, se ignora. La restricción única de la tabla cubre dos entregas simultáneas. | `GestionNotificaciones`, `V2__...sql` (`uk_notificaciones_evento_tipo`) |
| Reintentos | Los MDB **ya no se tragan el error**: marcan la transacción para deshacerse y el broker reentrega. Política en Artemis: **4 entregas con backoff exponencial** (1 s, 2 s, 4 s; tope 8 s). | `mensajeria.ConsumoDeEventos`, `scripts/wildfly/configurar-mensajeria.cli` |
| DLQ | Cola de mensajes muertos `MensajesFallidos`, declarada por el WAR. Tras el último intento el mensaje va ahí y `MonitorMensajesFallidos` lo registra (`mensajes_fallidos`) y alerta en el log. Un ADMIN lo consulta en `GET /api/mensajeria/mensajes-fallidos`. | `DestinosJms`, `MonitorMensajesFallidos` |
| (extra) Pérdida de mensajes | Las suscripciones al tópico son **durables**: si un consumidor está caído, el broker le guarda los mensajes. | `SuscriptorAvisoPaciente`, `SuscriptorAvisoProfesional` |

**Demostración en vivo de reintentos + DLQ** (como ADMIN):

```bash
curl -X POST -H "Authorization: Bearer $TOKEN_ADMIN" -H "Content-Type: application/json" \
     http://localhost:8080/mediconecta/api/mensajeria/mensaje-defectuoso
# en el log: 4 intentos ("intento 1".."intento 4") a 1, 2 y 4 s; luego [JMS-DLQ] ...
curl -H "Authorization: Bearer $TOKEN_ADMIN" http://localhost:8080/mediconecta/api/mensajeria/mensajes-fallidos
```

Tests: `EventoTurnoTest`, `ConsumoDeEventosTest`, `GestionNotificacionesTest`.

## 2. API

> *Faltan timeouts, Problem Details, OpenAPI e Idempotency-Key.*

| Observación | Qué se hizo | Dónde |
|---|---|---|
| Timeouts | **Clientes:** el adaptador REST y el SOAP tienen timeout de conexión (2 s) y de respuesta (5 s), configurables; si se agota, `504` (`tiempo-agotado`) en lugar de `502`. El WSDL viaja dentro del WAR, así el cliente SOAP no descarga el contrato (sin timeout) en cada llamada. **Servidor:** transacciones JTA 60 s, HTTP 30/60 s, espera de conexión del pool 5 s, consultas JPA 5 s. | `integraciones.TiemposDeEspera`, `AdaptadorObraSocial*`, `configurar-limites.cli`, `persistence.xml` |
| Problem Details | **Todo** error sale como `application/problem+json` (RFC 9457) con `type` estable (`urn:mediconecta:problema:*`), `title`, `status`, `detail` e `instance`. Incluye los que genera JAX-RS (404 de ruta, 405 con `Allow`, 415), el JSON mal formado (`400` y no `500`) y el 401 del mecanismo JWT. Un error no previsto devuelve `500` sin detalles internos y con un `idError` que es el mismo que queda en el log. | `presentacion.Problemas`, `DetalleProblema`, `TipoProblema`, `Manejador*` |
| OpenAPI | Contrato **OpenAPI 3.1** con las 39 operaciones, esquemas, seguridad JWT, roles por operación (`x-roles`) y respuestas de error. Swagger UI en `/api-docs.html`. **Un test compara el contrato con los recursos JAX-RS reales**: documentar de menos o de más rompe el build. | `src/main/webapp/openapi.json`, `api-docs.html`, `ContratoOpenApiTest` |
| Idempotency-Key | `POST /api/turnos` acepta `Idempotency-Key` (por usuario, 24 h, huella SHA-256 del pedido): mismo pedido → el mismo turno y `Idempotent-Replayed: true`; otro pedido con la misma clave → `422`; dos simultáneos → `409` y el segundo se deshace con sus mensajes JMS. El frontend la manda siempre. Un timer EJB borra las claves vencidas. | `FachadaReservas.reservarConClave`, `ClaveIdempotencia`, `LimpiezaIdempotencia`, `js/turno-nuevo.js` |

Tests: `ProblemasTest`, `ContratoOpenApiTest`, `FachadaReservasTest`, `TiemposDeEsperaTest`,
`IntegracionExternaExceptionTest`.

## 3. Seguridad

> *Hay secretos por defecto y faltan cabeceras de seguridad.*

| Observación | Qué se hizo | Dónde |
|---|---|---|
| Secreto JWT por defecto | Eliminado. Se configura con `MEDICONECTA_JWT_SECRETO`; si falta, se genera uno **aleatorio** al arrancar (nadie puede fabricar tokens leyendo el repositorio). | `ServicioJwt` |
| Contraseña demo en el código | Eliminada. Los usuarios demo solo se crean si se configura `MEDICONECTA_DEMO_CLAVE` (mínimo 8 caracteres); si no, no se crea ninguno. | `CargaDeDatosDemo` |
| Credenciales de base por defecto | El datasource no tiene usuario ni contraseña por omisión (ni en local ni en Docker); compose exige las variables y se niega a arrancar sin ellas. | `configurar-datasource.cli`, `docker/configurar-wildfly-docker.cli`, `compose.yaml`, `.env.example` |
| Cabeceras | `Content-Security-Policy` estricta (sin scripts ni estilos en línea: se quitaron todos los `style="..."`), `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener/Resource-Policy`, HSTS (si es HTTPS) y `Cache-Control: no-store` en la API. Swagger UI se carga con versión fija y Subresource Integrity. | `seguridad.CabecerasSeguridad` |
| (extra) IDOR | Un PACIENTE podía leer turnos ajenos con `GET /api/turnos/{id}`. Corregido. | `GestionTurnos.obtenerTurno` |
| (extra) Cookie | `JSESSIONID` con `HttpOnly` y `SameSite=Strict`. | `web.xml`, `WEB-INF/undertow-handlers.conf` |

Tests: `CabecerasSeguridadTest`, `ServicioJwtTest`, `MecanismoAutenticacionJwtTest`,
`CargaDeDatosDemoTest`, `GestionTurnosTest`.

## 4. DevOps

> *No hay Docker de la app, CI, health check ni migraciones.*

| Observación | Qué se hizo | Dónde |
|---|---|---|
| Docker de la app | `Dockerfile` multi-etapa (Maven → WildFly 36 oficial configurado por CLI embebido, con los mismos scripts que la instalación local) y `HEALTHCHECK`. `compose.yaml` levanta base, migraciones y app en orden, con secretos desde `.env`. | `Dockerfile`, `compose.yaml`, `docker/` |
| CI | GitHub Actions en cada push/PR: (1) linter + tests + cobertura mínima, con artefactos y resumen de cobertura; (2) migraciones sobre una base vacía, dos veces; (3) Docker Compose + prueba de punta a punta de 123 verificaciones. | `.github/workflows/ci.yml` |
| Health check | `/api/salud` pasó de un saludo a un **readiness** real (PostgreSQL con `SELECT 1` y timeout de 2 s, broker JMS): `200 UP` o `503 DOWN` con el componente caído. `/api/salud/vivo` es el liveness. | `RecursoSalud`, `negocio.MonitorSalud`, `SondaBaseDeDatos`, `SondaMensajeria` |
| Migraciones | **Flyway**: `V1__esquema_inicial.sql` (idéntico a lo que creaba Hibernate) y `V2__...` (índices y tablas nuevas). Hibernate pasó de `update` a **`validate`**. Las bases existentes se toman como versión 1 y solo reciben la V2 (verificado sobre una base de la Entrega 2: columnas idénticas a una base nueva). | `db/migraciones/`, `pom.xml`, `persistence.xml` |

## 5. Calidad

> *Sin cobertura ni linter.*

| Observación | Qué se hizo | Dónde |
|---|---|---|
| Cobertura | **JaCoCo**: informe en cada `mvn test` y **mínimo de 80 % de líneas** en `mvn verify` (hoy ~84 % sobre la lógica). Las clases que solo existen dentro del contenedor quedan fuera del mínimo y las cubre la prueba de punta a punta. Los tests pasaron de **58 a 171** (asistente `@Stateful`, fachada, pacientes, profesionales, MDB, Problem Details, seguridad...). | `pom.xml`, `src/test/java` |
| Linter | **Checkstyle 10** en la fase `validate`: corta el build ante imports sin usar, `==` entre Strings, `equals` sin `hashCode`, bloques vacíos, nombres fuera de convención, etc. | `config/checkstyle/checkstyle.xml` |

## 6. Gestión

> *El Trello no está actualizado y el 88 % de los commits está en una persona.*

Esto no se resuelve con código. Queda como tarea del equipo:

- Actualizar el Trello con una tarjeta por bloque de esta lista (mensajería, API,
  seguridad, DevOps, calidad), con su responsable.
- Repartir la defensa: que cada integrante presente y responda por un bloque.
- De acá en adelante, trabajar con ramas y pull requests por integrante, de modo que la
  autoría de los commits refleje quién hizo cada parte.

---

## 7. 3.er checkpoint (20/11): resiliencia

Lo pedido para el checkpoint, además de lo anterior:

| Pedido | Qué se hizo |
|---|---|
| Timeout de 5 s con fallback | Cobertura: último resultado bueno (24 h) o "a verificar", con `contingencia: true`. IA: orientación de respaldo por palabras clave (detecta emergencias). Los adaptadores corren fuera de transacción para que el fallback pueda responder. |
| Si se apaga el broker no se pierden mensajes | Mensajes persistentes y suscripciones durables; probado matando WildFly con mensajes en la cola (plan de pruebas §6). |
| ETag | `ETag` en turnos y pacientes (`@Version`), `If-None-Match` → 304, `If-Match` → 412. |
| IA a través de su gateway | `GestorOrientacion`: validación, límite de 10/min (429), filtro de la salida y fallback. |
| Plan de pruebas, matriz de trazabilidad y manual de usuario | [Plan_de_Pruebas.md](Plan_de_Pruebas.md), [Matriz_de_Trazabilidad.md](Matriz_de_Trazabilidad.md), [Manual_de_Usuario.md](Manual_de_Usuario.md). |

Y adelantado para la entrega final: 429 ante fuerza bruta, corrección de un segundo BOLA
(fichas de pacientes), PIT (85 %), k6 (p95 35,6 ms), ZAP en CI (0 High), gitleaks en CI,
job de SonarCloud, Maven Wrapper + enforcer, evaluación de IA y
[Informe_de_Estado.md](Informe_de_Estado.md).

