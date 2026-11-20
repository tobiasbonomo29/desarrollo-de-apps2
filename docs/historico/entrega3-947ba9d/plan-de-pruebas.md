# MediConecta — Plan de pruebas (v1, checkpoint 3)

> Versión 1 — 18/11/2026. Describe las pruebas que **existen y se ejecutaron** sobre el
> sistema real. Lo que todavía no se pudo ejecutar figura como *pendiente* en §7, sin
> resultado inventado.

## 1. Objetivo y alcance

Verificar que MediConecta (Jakarta EE 10 sobre WildFly) cumple:

- las funciones de negocio: turnos, pacientes, profesionales, reserva guiada (stateful),
  cobertura (SOAP + REST), orientación con IA;
- los requisitos del checkpoint 3: integraciones de punta a punta, resiliencia (timeout de
  5 s, fallback, circuit breaker), mensajería confiable (idempotencia, DLQ, persistencia y
  recuperación), contrato REST (Idempotency-Key, ETag, Problem Details);
- los requisitos de seguridad: autenticación JWT, BFLA, BOLA/IDOR, JWT inválidos,
  inyección, fuerza bruta (429), cabeceras de seguridad;
- los atributos de calidad de la entrega final: cobertura, mutation score, carga.

Fuera de alcance de esta versión: pruebas de interfaz automatizadas (Selenium/Playwright),
pruebas de compatibilidad entre navegadores y pruebas contra los sistemas reales de las
obras sociales (no existen: están simulados dentro del WAR).

## 2. Niveles de prueba y herramientas

| Nivel | Herramienta | Dónde | Necesita servidor |
|---|---|---|---|
| Unitarias de negocio, modelo, seguridad, mensajería | JUnit 5 | `src/test/java/**` | No |
| Integración de la capa REST (recursos + mappers + filtros reales) | JUnit 5 + dispatcher en memoria de RESTEasy (`ApiRestTest`) | `presentacion` | No |
| Integración HTTP del adapter de IA y del partner REST | JUnit 5 + servidor HTTP local del JDK (`AdaptadorIaGroqHttpTest`) | `integraciones` | No (servidor falso local) |
| Regla de autorización (BFLA) | JUnit 5 + reflexión (`AutorizacionDeclaradaTest`) | `presentacion` | No |
| Cobertura de código | JaCoCo 0.8.12 (umbral 80 % líneas, 70 % ramas) | `mvnw verify` | No |
| Mutation testing | PIT 1.17.4 (umbral 60 %) | `mvnw test-compile org.pitest:pitest-maven:mutationCoverage` | No |
| Sistema / end-to-end | `scripts/smoke-test.ps1` (119 casos) | WildFly desplegado | Sí |
| Recuperación del broker | Procedimiento §5.3 (CLI de WildFly) | WildFly desplegado | Sí |
| Carga | `scripts/carga/prueba-carga.py` (Python 3, biblioteca estándar) | WildFly desplegado | Sí |
| Secretos en el historial | gitleaks (job `secretos` del CI) | GitHub Actions | No |
| Análisis estático / Quality Gate | SonarCloud (job `sonar`, opcional) | GitHub Actions | No |
| Vulnerabilidades dinámicas | OWASP ZAP 2.16.1, escaneo activo web y API autenticada (§5.5) | WildFly desplegado | Sí |
| Seguridad de la API | `scripts/seguridad/pruebas-seguridad.py` (47 casos) | WildFly desplegado | Sí |

Los tests unitarios no usan base de datos ni contenedor: los EJB se instancian como objetos
comunes y se cablean con los repositorios en memoria (`negocio.Escenario`,
`negocio.Inyeccion`). Las pruebas de IA **nunca llaman al proveedor real**: usan un
orientador falso o un servidor HTTP local que imita la API "chat completions".

## 3. Entorno

| Elemento | Unitarias / CI | Sistema (ejecución del 18/11/2026) |
|---|---|---|
| JDK | 17 (CI, Temurin) · 21 (local) | 21.0.10 |
| Build | Maven 3.9.9 vía `mvnw` (versión fija) | — |
| Servidor | — | WildFly 36.0.1.Final, `standalone-full.xml` |
| Datos | Repositorios en memoria | **Plan B en memoria** (ver §7: no había PostgreSQL ni Docker en la máquina) |
| Configuración | — | `configurar-seguridad.cli` + `configurar-mensajeria.cli` del repositorio |

## 4. Criterios de entrada y salida

- **Entrada:** el proyecto compila con `mvnw verify`; para pruebas de sistema, el WAR está
  desplegado y `GET /api/salud` responde 200.
- **Salida (aprobación):** 0 tests fallidos; 0 defectos bloqueantes o críticos abiertos; cobertura ≥ 80 % líneas y ≥ 70 % ramas;
  mutation score ≥ 60 %; smoke test sin fallas; p95 de carga < 500 ms con < 1 % de errores;
  ZAP sin alertas *High*; gitleaks sin hallazgos; Quality Gate de Sonar aprobado.

## 5. Casos de prueba por área

Los identificadores (`PU-*` unitarias, `PS-*` sistema) son los que usa la
[matriz de trazabilidad](matriz-trazabilidad.md).

### 5.1 Unitarias e integración sin servidor (203 tests)

| Id | Clase de prueba | Qué verifica |
|---|---|---|
| PU-01 | `EstadoDelTurnoTest` | Patrón State: transiciones válidas e inválidas (→ 409) |
| PU-02 | `FabricaTurnosTest` | Factory: estado inicial, minutos truncados, coherencia de especialidad |
| PU-03 | `GestionTurnosTest` | Agenda, superposición, pasado, cancelación, archivado, ownership (403), BOLA en `obtenerTurno` |
| PU-04 | `GestionPacientesTest` | Padrón, documento único e inmutable, BOLA sobre fichas (403 sin revelar existencia) |
| PU-05 | `GestionProfesionalesTest` | Alta (ADMIN), franjas, agenda propia del MEDICO, horarios libres |
| PU-06 | `FachadaReservasTest` | Reserva de punta a punta, alta al vuelo, aviso JMS; **Idempotency-Key**: reintento, 422, 409 en curso, clave por usuario, vencimiento 24 h |
| PU-07 | `AsistenteReservaTurnoTest` | Componente stateful: pasos, validaciones, volver, ownership |
| PU-08 | `ServicioAutenticacionTest` | Login, mensaje uniforme, **bloqueo por fuerza bruta** (cuenta e IP), desbloqueo temporal |
| PU-09 | `ServicioJwtTest`, `MecanismoAutenticacionJwtTest` | Firma HS256, vencimiento, `alg:none`, token adulterado, 401 en Problem Details, recursos públicos |
| PU-10 | `HashDeClavesTest`, `ProteccionAbusoTest` | PBKDF2; ventana deslizante y límites |
| PU-11 | `ProteccionIntegracionTest` | **Timeout** (corta una llamada lenta), fallback, **circuit breaker** (abre a las 3 fallas, semiabierto a los 30 s) |
| PU-12 | `CoberturaResilienteTest`, `GestorCoberturaTest` | Adapter SOAP/REST, **fallback** al último dato bueno y "sin verificar", circuito abierto, timeouts del cliente SOAP |
| PU-13 | `OrientacionIaTest`, `GestorOrientacionTest`, `OrientadorPorPalabrasClaveTest` | IA: especialidad de la lista, descarte de inventadas, **fallback por reglas** ante falla o timeout, urgencias, **429 a las 10 consultas/min** |
| PU-14 | `AdaptadorIaGroqTest`, `AdaptadorIaGroqHttpTest` | Formato del pedido, lectura de la respuesta, errores 5xx, **timeout real del cliente HTTP**, sin clave no hay llamada; errores del partner REST |
| PU-15 | `MensajeriaTest` | Productor PERSISTENT con clave; **consumidores idempotentes**; mensaje veneno → excepción (reintento/DLQ); monitor de DLQ que nunca falla |
| PU-16 | `ApiRestTest` | REST real: 201 + Location + ETag; **Idempotency-Replayed**; 422; **304** con If-None-Match; **412** con If-Match viejo; **Problem Details** en 400/403/404/405/409/412/422 y JSON inválido; `Cache-Control` |
| PU-17 | `ManejadoresTest` | 429 + Retry-After, 401 + WWW-Authenticate, 502, 500 sin detalle interno, cabeceras de seguridad |
| PU-18 | `AutorizacionDeclaradaTest` | **BFLA**: todo método público de un EJB declara `@RolesAllowed`/`@PermitAll`; operaciones sensibles restringidas |
| PU-19 | `SoporteNegocioTest` | Carga demo idempotente, fechas, limpieza de claves vencidas |

**Resultado (18/11/2026, `mvnw clean verify`, versión final):** 203 tests, 0 fallas, 0 errores.

### 5.2 Sistema: `scripts/smoke-test.ps1`

119 verificaciones contra el WAR desplegado. Áreas: salud; login JWT; seguridad por roles
(403); profesionales; pacientes; turnos (400/404/409, formato Entrega 1); ownership y
archivado; patrón State; cobertura SOAP y REST; asistente stateful; y la sección
**Entrega 3**: Problem Details (incluye 401 y ruta inexistente), JWT `alg=none`,
Idempotency-Key (reintento, `Idempotency-Replayed`, 422), ETag (304/412/200), BOLA (403),
CSP/nosniff, campo de contingencia de cobertura, orientación IA o fallback, 429 +
`Retry-After`, DLQ (403 a no-ADMIN, 202, llegada a la DLQ) y, con `-LogWildFly`, entrega por
cola y tópico.

**Resultado (18/11/2026):** 119 OK, 0 fallidas. Evidencia:
[`evidencias/entrega3/smoke-test-wildfly36.txt`](evidencias/entrega3/smoke-test-wildfly36.txt).

### 5.3 Falla y recuperación de la mensajería

| Id | Escenario | Procedimiento | Resultado esperado | Resultado 18/11 |
|---|---|---|---|---|
| PS-JMS-1 | Mensaje veneno | `POST /api/mensajeria/prueba-dlq` (ADMIN) | 3 entregas fallidas con rollback y paso a `ColaConfirmacionesDLQ`; `MonitorColaMuerta` lo registra | OK: 3 × `AMQ154004` y luego `[JMS-DLQ]` (el retardo de 1 s entre reintentos **no** se observó; los 3 intentos fueron casi inmediatos) |
| PS-JMS-2 | Reinicio con mensajes pendientes en la **cola** | CLI `…/message-driven-bean=ReceptorConfirmacion:stop-delivery`; reservar un turno; `message-count` = 1; `:shutdown(restart=true)` | Tras el reinicio el mensaje se procesa (journal persistente) | OK: turno 22 confirmado después del redespliegue |
| PS-JMS-3 | Reinicio con mensajes pendientes en el **tópico** | Igual, con `SuscriptorAvisoPaciente` | La suscripción durable conserva el aviso | OK: aviso del turno 4 entregado después del reinicio |
| PS-JMS-4 | Entrega duplicada | Unitaria `MensajeriaTest` (el broker no permite forzarla a mano) | El efecto se aplica una vez por consumidor | OK |

Evidencia: [`recuperacion-broker.txt`](evidencias/entrega3/recuperacion-broker.txt) y
[`log-wildfly-extracto.txt`](evidencias/entrega3/log-wildfly-extracto.txt).

### 5.4 Carga

`python scripts/carga/prueba-carga.py --usuarios 20 --segundos 60 --escrituras`
(mezcla de lecturas de agenda, detalle, padrón, tablero y búsqueda, más altas con
Idempotency-Key). Usar `127.0.0.1`: en Windows `localhost` prueba IPv6 primero y agrega
~2 s por pedido (se observó en la primera corrida y se descartó).

**Resultado (18/11/2026, versión final de la auditoría, misma máquina, plan B en memoria):**
46.791 pedidos, 0 errores, 779 pedidos/s, p50 9,3 ms, **p95 110,7 ms**, p99 165,0 ms →
aprobado (< 500 ms). Evidencia: [`prueba-carga.json`](evidencias/entrega3/prueba-carga.json).
**Limitación:** sin PostgreSQL; hay que repetirla con la base real.

### 5.5 Seguridad

| Control | Cómo se prueba | Resultado 18/11/2026 |
|---|---|---|
| BFLA | PU-18 + smoke + `scripts/seguridad/pruebas-seguridad.py` (11 casos por rol) | OK |
| BOLA / IDOR | PU-03/04/06/16 + smoke + script (9 casos: turno, ficha, historial, cobertura, reserva, asistente, agenda de otro médico) | OK |
| JWT inválidos | PU-09 + smoke + script: ausente, basura, `alg=none`, otro secreto, rol alterado, **vencido bien firmado**, emisor ajeno | OK (7 casos → 401) |
| Inyección | Script: SQL/JPQL en búsqueda, especialidad y ruta; JSON con tipos inesperados; XSS almacenado; *prompt injection* con rol `system`. Revisión de código: JPQL siempre parametrizado; el frontend usa `esc()`/`textContent` | OK (sobre plan B en memoria; contra PostgreSQL vale la revisión de código) |
| Fuerza bruta / 429 | PU-08 + smoke (6.º intento → 429 + `Retry-After`); durante el escaneo de ZAP el login quedó bloqueado (612 respuestas 429) | OK |
| Cabeceras | PU-17 + smoke + script (CSP **sin `unsafe-inline`**, nosniff, X-Frame-Options, Referrer-Policy, `no-store`, cookie `SameSite=Strict`) | OK |
| **OWASP ZAP 2.16.1** | (a) escaneo activo sin autenticar (`-quickurl`); (b) escaneo activo **autenticado** de la API con `scripts/seguridad/zap-api.yaml` (3509 pedidos a `/api`, verificado con el access log) | **0 High, 0 Medium, 0 Low en la aplicación**. Queda 1 Medium fuera del WAR: falta CSP en `robots.txt`/`sitemap.xml` de la raíz de WildFly |
| Secretos en el historial | Job `secretos` (gitleaks) del CI; búsqueda local en los 26 commits de claves con formato conocido y literales con forma de JWT | Local OK (0 hallazgos); gitleaks pendiente de la primera corrida del CI |

Evidencias: [`pruebas-seguridad.txt`](evidencias/entrega3/pruebas-seguridad.txt),
[`zap-api.html`](evidencias/entrega3/zap-api.html), [`zap-api.json`](evidencias/entrega3/zap-api.json),
[`zap-web-final.json`](evidencias/entrega3/zap-web-final.json) (tokens censurados).

#### Defectos que encontraron estas pruebas (todos corregidos y reverificados)

| Defecto | Severidad | Cómo apareció | Corrección |
|---|---|---|---|
| Cada 401 escribía un ERROR con stack trace (~6 KB) en el log: un atacante sin credenciales podía llenar el disco (20 MB en un escaneo) | Alta | Escaneo ZAP | El mecanismo JWT pide el 401 al contenedor y el cuerpo lo escribe `seguridad.RespuestaNoAutenticado` (`<error-page>` 401). 52 pedidos 401 → 0 líneas de log |
| `POST /api/pacientes` con `{`, comillas o `<script>` en el documento: 500 **con el paciente ya guardado** | Alta | Escaneo ZAP (25 respuestas 500 y alerta High de XSS reflejado) | El negocio exige DNI de 6 a 10 dígitos (`GestionPacientes.FORMATO_DOCUMENTO`) y el `Location` se arma con plantilla codificada |
| CSP con `style-src 'unsafe-inline'` | Media | Escaneo ZAP | 22 `style="..."` reemplazados por clases utilitarias en `styles.css` |
| Cookie de sesión sin `SameSite` | Baja | Escaneo ZAP | `WEB-INF/undertow-handlers.conf` (`SameSite=Strict`) |
| El plan B documentado no desplegaba (WFLYWELD0037) | Alta para la demo | Despliegue siguiendo el README | `<scan><exclude>` de `datos.jpa` dentro del bloque del plan B en `beans.xml` |
| `@Remove finalizar()` del asistente stateful rechazado al expirar la sesión (`EJBAccessException`) | Media | Log de WildFly al redesplegar | `@PermitAll` en `finalizar()` |
| Token `alg=none` literal en el smoke test: gitleaks lo marcaría como secreto y el CI fallaría | Media (CI) | Revisión de la regla `jwt` de gitleaks | El token se arma en tiempo de ejecución |

### 5.6 IA y resiliencia en vivo

Con el proveedor simulado `scripts/ia/proveedor-ia-simulado.py` y WildFly apuntando a él
(`mediconecta.ia.url`), el gateway real se comportó así: **ok** → `origen IA` en 0,09 s
(con cabecera `Authorization` y modelo correctos); **lento (8 s)** → corte a los **5,07 s**
y `origen REGLAS`; **503** → `origen REGLAS` inmediato; **ok** de nuevo → `origen IA`
([evidencia](evidencias/entrega3/ia-gateway-en-vivo.txt)). Con el WSDL del legado apuntando
a un puerto caído: dato de contingencia con la fecha de la última consulta, "SIN_VERIFICAR"
sin dato previo, y circuito ABIERTO a la tercera falla
([evidencia](evidencias/entrega3/cobertura-fallback-en-vivo.txt)).
**No verificado:** la integración con el proveedor real (Groq), porque no hay clave en este
entorno.

## 6. Calidad del código

| Métrica | Umbral | Resultado 18/11/2026 (versión final) | Cómo se mide |
|---|---|---|---|
| Cobertura de líneas, total | ≥ 80 % | **84,3 %** (1779/2110) | JaCoCo, `target/site/jacoco/index.html` |
| Cobertura de ramas, total | ≥ 70 % | **83,1 %** (575/692) | ídem |
| Cobertura de líneas, capa de negocio (`negocio`, `integraciones`, `mensajeria`, `modelo.estado`) | ≥ 80 % | **94,3 %** (1088/1154) | ídem |
| Cobertura de ramas, capa de negocio | ≥ 70 % | **90,2 %** (404/448) | ídem |
| Mutation score total | ≥ 60 % | **77 %** (762/994), test strength 88 % | PIT, `target/pit-reports/index.html` |
| Mutation score, capa de negocio | ≥ 60 % | **85 %** (469/550) | ídem |
| Quality Gate Sonar | aprobado | **No verificable** (requiere `SONAR_TOKEN`) | job `sonar` del CI |

La cobertura excluye `datos.jpa` (necesita un `EntityManager` real; esos repositorios se
ejercitan en el smoke test contra PostgreSQL). La exclusión está declarada en `pom.xml` y
en `sonar.coverage.exclusions`. La capa más baja es `presentacion` (51,9 % de líneas):
recursos delgados que el smoke test ejercita en el servidor.

Build reproducible: copia limpia del árbol + repositorio Maven local **vacío** →
`mvnw clean verify` en 43 s, mismos tests y umbrales, WAR sin bibliotecas empaquetadas.

## 7. Pendientes y riesgos

1. **Ejecución contra PostgreSQL**: en esta máquina no hay PostgreSQL ni Docker; smoke,
   recuperación, carga y seguridad corrieron con los repositorios en memoria. Falta
   repetirlos con la base real (tablas nuevas `claves_idempotencia` y `mensajes_procesados`,
   restricción única ante pedidos concurrentes, inyección contra JPQL real).
2. **Sonar y gitleaks**: configurados en el CI; falta la primera corrida en GitHub (Sonar
   además necesita `SONAR_TOKEN`, `SONAR_ORGANIZATION` y `SONAR_PROJECT_KEY`). El historial
   conserva el antiguo secreto JWT de desarrollo, que ya no se usa.
3. **Retardo entre reintentos de JMS**: configurado en 1 s pero no observado (los 3
   intentos fueron casi inmediatos); no afecta la llegada a la DLQ.
4. **IA con el proveedor real**: sin clave en este entorno.
5. **CSP en la raíz de WildFly** (`robots.txt`, `sitemap.xml`): es contenido del servidor,
   no del WAR; se resuelve en la configuración de Undertow del servidor de la demo.

## 8. Cómo reproducir

```powershell
.\mvnw.cmd clean verify                                               # 203 tests + cobertura + umbrales
.\mvnw.cmd test-compile org.pitest:pitest-maven:mutationCoverage      # mutation testing
.\scripts\smoke-test.ps1 -LogWildFly <wildfly>\standalone\log\server.log
python scripts\carga\prueba-carga.py --usuarios 20 --segundos 60 --escrituras
python scripts\seguridad\pruebas-seguridad.py --secreto-jwt <secreto del servidor>
zap.bat -cmd -port 8095 -autorun scripts\seguridad\zap-api.yaml   # con ZAP_AUTH_HEADER_VALUE="Bearer <token>"
```
