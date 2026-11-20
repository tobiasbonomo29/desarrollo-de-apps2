# MediConecta — Plan de pruebas (v1)
> **Versión superada (v1, 19/11/2026).** La versión final, basada en la rama `integracion-final`, está en [`documentacion-final/`](../documentacion-final/README.md).

**Versión:** 1.0 · **Fecha:** 19/11/2026 · **Alcance:** 3.er checkpoint (20/11) y entrega final.
La correspondencia entre requisitos y casos de prueba está en
[Matriz_de_Trazabilidad.md](Matriz_de_Trazabilidad.md).

---

## 1. Objetivo

Verificar que MediConecta cumple sus requisitos funcionales (turnos, pacientes,
profesionales, reserva guiada, cobertura, asistente de IA, avisos) y los no funcionales de
los *Criterios de Testing*: seguridad, resiliencia, rendimiento, calidad de código y
documentación. También busca dejar evidencia reproducible de cada umbral.

## 2. Alcance

| Incluido | Excluido |
|---|---|
| Backend Jakarta EE (EJB, JAX-RS, JPA, JMS, SOAP/REST, seguridad JWT) | Envío real de mails/SMS (los avisos se registran, no se envían) |
| Frontend (pantallas, flujo de reserva, manejo de errores) | Compatibilidad con navegadores viejos (sin ES modules) |
| Integraciones: sistema legado SOAP, partner REST, proveedor de IA (Groq) | Alta disponibilidad / varias instancias |
| Migraciones de base, despliegue con Docker, pipeline de CI | |

## 3. Niveles y tipos de prueba

| # | Tipo | Qué cubre | Herramienta | Dónde | Cuándo corre |
|---|---|---|---|---|---|
| P1 | **Unitarias** | Reglas de negocio, patrón State, Factory, Facade, asistente `@Stateful`, idempotencia, consumidores JMS, Problem Details, JWT, limitador, fallbacks | JUnit 5 (EJB como objetos + repositorios en memoria) | `src/test/java` (194 tests) | Cada build y CI (`calidad`) |
| P2 | **Contrato de la API** | Que `openapi.json` documente exactamente los endpoints reales | JUnit 5 (`ContratoOpenApiTest`) | `src/test/java/.../presentacion` | Cada build |
| P3 | **Cobertura** | ≥ 80 % de líneas total; **capa de negocio ≥ 80 % líneas y ≥ 70 % ramas** | JaCoCo | `pom.xml` (`mvn verify`) | Cada build y CI |
| P4 | **Mutación** | Que los tests detecten cambios en la lógica: **mutation score ≥ 60 %** | PIT | `pom.xml` | CI (`mutacion`) |
| P5 | **Análisis estático** | Estilo y errores frecuentes; **Quality Gate de Sonar** | Checkstyle (build) · SonarCloud | `config/checkstyle` · CI (`sonar`) | Cada build · CI |
| P6 | **Migraciones** | Que `db/migraciones` se aplique sobre una base vacía y sea idempotente; que Hibernate valide el esquema | Flyway + Hibernate `validate` | CI (`migraciones`) y despliegue | CI |
| P7 | **Sistema / punta a punta** | Todo el sistema desplegado como lo usa un cliente: 160+ verificaciones (ver §5) | bash + curl + jq | `scripts/smoke-test.sh` | CI (`punta-a-punta`) y antes de cada demo |
| P8 | **Seguridad en vivo** | BOLA/IDOR, BFLA, JWT inválidos (`alg:none`, firma ajena, mal formado, vencido), inyección SQL y XSS, **429 ante fuerza bruta**, cabeceras | `smoke-test.sh` (sección "seguridad en vivo") + **OWASP ZAP** baseline (sin alertas High) | CI y local | CI |
| P9 | **Carga** | **p95 < 500 ms** y < 1 % de errores con 20 usuarios concurrentes | k6 | `pruebas/carga/carga.js` | CI |
| P10 | **Resiliencia** | Timeout de 5 s con fallback (cobertura e IA), consumidores idempotentes, reintentos y DLQ, broker caído sin pérdida de mensajes, `Idempotency-Key`, ETag | Unitarias + `smoke-test.sh` + procedimientos manuales (§6) | | CI + demo |
| P11 | **IA** | Que el asistente oriente bien, detecte emergencias, resista *prompt injection*, no se salga de tema ni invente especialidades; que el gateway limite y tenga fallback | `pruebas/ia/evaluar-ia.sh` (12 casos, umbral 80 %) + unitarias (`FallbacksTest`, `AdaptadorIaGroqTest`, `GestorOrientacionTest`) | | Manual (necesita la clave de IA) + CI (fallback) |
| P12 | **Secretos** | Que ningún commit del historial tenga secretos | gitleaks | CI (`secretos`) | CI |
| P13 | **Aceptación / exploratorias** | Recorrido de cada rol por la interfaz siguiendo el [manual de usuario](Manual_de_Usuario.md) | Navegador | | Antes de cada entrega |

## 4. Entornos

| Entorno | Cómo se levanta | Uso |
|---|---|---|
| Unitario | `./mvnw verify` (sin servidor ni base) | P1–P4 |
| Local | WildFly 36 (`standalone-full.xml`) + PostgreSQL 16 en Docker (ver README §3) | Desarrollo, P7–P11, demo |
| Docker Compose | `cp .env.example .env && docker compose up -d --build` | Idéntico a CI; P7–P10 |
| CI | GitHub Actions (`.github/workflows/ci.yml`), Ubuntu, JDK 17, Maven 3.9.9 vía `./mvnw` | Todo salvo P11 con modelo real y P13 |

**Datos de prueba:** juego de datos de `CargaDeDatosDemo` (4 profesionales, 4 pacientes,
3 turnos, usuarios `paciente`/`recepcion`/`medico`/`admin` con la contraseña de
`MEDICONECTA_DEMO_CLAVE`). Las pruebas de punta a punta crean sus propios pacientes con
documentos aleatorios y cancelan los turnos que crean.

## 5. Cómo ejecutar cada prueba

```bash
./mvnw verify                                            # P1, P2, P3, P5 (Checkstyle)
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage   # P4 -> target/pit-reports/index.html
./mvnw flyway:migrate flyway:info -Dflyway.password=...  # P6
scripts/smoke-test.sh http://localhost:8080/mediconecta  # P7, P8, P10
pruebas/ia/evaluar-ia.sh http://localhost:8080/mediconecta   # P11 (con MEDICONECTA_IA_CLAVE en el servidor)

# P9 - carga (Linux; en macOS usar host.docker.internal y sacar --network host)
docker run --rm -i --network host -e BASE_URL=http://localhost:8080/mediconecta -e CLAVE=Demo.2026 \
    grafana/k6:0.54.0 run - < pruebas/carga/carga.js

# P8 - OWASP ZAP baseline
docker run --rm --network host -v "$PWD/zap:/zap/wrk:rw" ghcr.io/zaproxy/zaproxy:stable \
    zap-baseline.py -t http://localhost:8080/mediconecta/ -J zap.json -r zap.html -I

# P12 - secretos en el historial
docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:v8.21.2 git /repo --redact -v
```

**SonarCloud (P5).** Requiere crear el proyecto en <https://sonarcloud.io> (gratis para
repositorios públicos) y configurar en GitHub → *Settings → Secrets and variables →
Actions*: el secreto `SONAR_TOKEN` y las variables `SONAR_ORGANIZACION` y `SONAR_PROYECTO`.
Desde ese momento el job `sonar` analiza cada push y falla si el Quality Gate no aprueba
(`-Dsonar.qualitygate.wait=true`).

## 6. Pruebas de resiliencia en vivo (para la demo)

| Escenario | Procedimiento | Resultado esperado |
|---|---|---|
| **Timeout de 5 s con fallback (cobertura)** | `jboss-cli --connect --command="/system-property=mediconecta.simulacion.demoraMs:add(value=8000)"` y consultar la cobertura de un paciente (Swiss Medical o OSDE). Después `...:remove`. | La respuesta llega a los ~5 s con `"contingencia": true` y `origen` `CACHE` (si hubo una consulta buena antes) o `CONTINGENCIA`. La pantalla no se rompe. |
| **Timeout / caída de la IA con fallback** | Sacar `MEDICONECTA_IA_CLAVE` (o apuntar `mediconecta.ia.url` a una URL inválida) y usar "Prefiero escribirlo". | Responde la orientación de respaldo (`"contingencia": true`); ante "dolor fuerte en el pecho" marca urgente. |
| **Broker apagado: no se pierden mensajes** | 1) Pausar la cola: `/subsystem=messaging-activemq/server=default/runtime-queue=jms.queue.ColaConfirmacionesMediConecta:pause()`. 2) Reservar un turno: la confirmación queda en la cola. 3) Matar WildFly (y con él el broker embebido) con `kill -9`. 4) Volver a levantarlo. | Tras el reinicio el broker entrega el mensaje pendiente y aparece la confirmación en "Avisos enviados". Probado el 19/11/2026: el turno 61 recibió su confirmación 15 s después del reinicio. Los mensajes son persistentes y las suscripciones al tópico son durables. |
| **Reintentos y DLQ** | Como ADMIN: `POST /api/mensajeria/mensaje-defectuoso`. | El log muestra los intentos 1 a 4 (espera de 1, 2 y 4 s) y el mensaje aparece en `GET /api/mensajeria/mensajes-fallidos`. |
| **Consumidor idempotente** | Test `GestionNotificacionesTest.elMismoEventoDosVecesGeneraUnSoloAviso`. | Una entrega repetida no genera un segundo aviso. |
| **Idempotency-Key** | Repetir el mismo `POST /api/turnos` con la misma clave. | Mismo turno, `Idempotent-Replayed: true`; con otro cuerpo, `422`. |
| **ETag / concurrencia** | `GET /api/turnos/{id}` → `ETag`; `PUT .../cancelar` con `If-Match` viejo. | `412` y el turno no cambia; con el ETag vigente se cancela y devuelve un ETag nuevo. |

## 7. Criterios de entrada, salida y aprobación

**Entrada:** el build compila y el despliegue responde `200` en `/api/salud`.

**Salida (todos obligatorios):**

| Criterio | Umbral | Medición actual (19/11/2026) |
|---|---|---|
| Tests unitarios | 100 % en verde | 194 / 194 |
| Cobertura de la capa de negocio | ≥ 80 % líneas, ≥ 70 % ramas | 95,0 % líneas · 82,5 % ramas |
| Mutation score (negocio + State) | ≥ 60 % | 85 % (395 mutantes, 337 detectados) |
| Prueba de punta a punta | 100 % en verde | 148 / 148 (local) |
| Carga | p95 < 500 ms, errores < 1 % | p95 = 35,6 ms, 0 % errores, ~200 req/s con 20 usuarios |
| ZAP baseline | 0 alertas High | 0 High, 0 Medium, 0 Low (4 informativas) |
| Secretos en el historial | 0 hallazgos | 0 (gitleaks) |
| Evaluación de IA | ≥ 80 % de aciertos | 11/12 en la primera corrida (el caso fallido era un error del caso: ver Informe); corregido |
| Quality Gate de Sonar | Aprobado | Pendiente: falta crear el proyecto en SonarCloud (§5) |
| Defectos abiertos | 0 Bloqueantes, 0 Críticos | 0 (ver §8) |

## 8. Gestión de defectos

Severidades: **Bloqueante** (impide usar el sistema o la demo), **Crítico** (pérdida o
exposición de datos, falla de seguridad, funcionalidad principal rota), **Mayor**
(funcionalidad secundaria rota con alternativa), **Menor** (cosmético). Cada defecto se
registra en Jira con pasos para reproducirlo, severidad, responsable y el test que lo cubre
una vez corregido.

Defectos encontrados por estas pruebas (todos corregidos y cubiertos por tests):

| Id | Severidad | Defecto | Corrección | Test |
|---|---|---|---|---|
| D-01 | Crítico | IDOR: un PACIENTE podía leer turnos ajenos (`GET /api/turnos/{id}`) | `GestionTurnos.obtenerTurno` exige que sea propio | `GestionTurnosTest.unPacienteSoloPuedeConsultarSusPropiosTurnos`, humo |
| D-02 | Crítico | BOLA: un PACIENTE podía leer fichas ajenas (`GET /api/pacientes/{documento}`) | `GestionPacientes.consultarFicha` | `GestionPacientesTest.unPacienteSoloPuedeConsultarSuPropiaFicha`, humo |
| D-03 | Crítico | Secreto JWT y contraseña demo escritos en el código | Configuración por entorno; secreto aleatorio si falta | `ServicioJwtTest`, `CargaDeDatosDemoTest` |
| D-04 | Mayor | Los MDB se tragaban los errores: un fallo perdía el mensaje | `setRollbackOnly` + reintentos + DLQ | `ConsumoDeEventosTest` |
| D-05 | Mayor | Al vencer la sesión, `@Remove` del asistente fallaba por `@RolesAllowed` (43 veces en el log) | `@PermitAll` en `finalizar()` | revisión del log del servidor |
| D-06 | Menor | Sin timeouts, un sistema externo colgado retenía hilos y transacciones | Timeouts + fallback | `FallbacksTest`, `TiemposDeEsperaTest` |

## 9. Riesgos

| Riesgo | Mitigación |
|---|---|
| El proveedor de IA cambia de modelo o se cae el día de la demo | Gateway con fallback por palabras clave; `evaluar-ia.sh` antes de exponer |
| La prueba de carga corre en una máquina más lenta | Margen amplio (p95 actual 35 ms vs. 500 ms) |
| Alguien agrega un endpoint sin documentar | `ContratoOpenApiTest` rompe el build |
| La demo se hace en otra máquina | Docker Compose levanta todo igual que CI |

## 10. Responsables

| Rol | Integrante |
|---|---|
| QA / plan de pruebas | *(completar)* |
| Seguridad (ZAP, pruebas en vivo) | *(completar)* |
| Rendimiento (k6) | *(completar)* |
| IA (evaluación) | *(completar)* |
