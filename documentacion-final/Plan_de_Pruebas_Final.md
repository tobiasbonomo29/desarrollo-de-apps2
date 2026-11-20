# MediConecta — Plan de pruebas final (v2)

**Fecha:** 20/11/2026 · **Código de referencia:** `eda4b02` (rama `integracion-final`) ·
**Reemplaza a:** `docs/Plan_de_Pruebas.md` (v1, 19/11, rama de Bellinzona).
Estructura basada en ISO/IEC/IEEE 29119-3, según los *Criterios de Testing* (Clase 15).

## 1. Alcance

| Dentro | Fuera |
|---|---|
| Backend Jakarta EE (EJB, JAX-RS, JPA, JMS, SOAP/REST, JWT), interfaz web, integraciones (SOAP legado, partner REST, IA Groq), migraciones, Docker Compose, CI | Envío real de mails/SMS (se registran), navegadores sin ES modules, alta disponibilidad / varias instancias |

## 2. Niveles y tipos

| # | Nivel | Herramienta | Dónde | Cuándo |
|---|---|---|---|---|
| P1 | Unitario (EJB como objetos + repos en memoria) | JUnit 5 | `src/test/java` (195) | Build y CI `calidad` |
| P2 | Contrato de la API | `ContratoOpenApiTest` | build | Build |
| P3 | Cobertura | JaCoCo (negocio ≥ 80 % / 70 %) | `pom.xml` | Build y CI |
| P4 | Mutación | PIT (negocio + `modelo.estado`, ≥ 60 %) | `pom.xml` | CI `mutacion` |
| P5 | Análisis estático | Checkstyle (build) · SonarCloud | `config/checkstyle`, CI `sonar` | Build · CI (**Sonar sin configurar**) |
| P6 | Migraciones | Flyway sobre PostgreSQL | CI `migraciones` | CI |
| P7 | Sistema / E2E de API | `scripts/smoke-test.sh` (148 verificaciones) | CI `punta-a-punta` | CI y antes de la demo |
| P8 | Seguridad | humo “seguridad en vivo” + ZAP baseline + gitleaks | CI | CI |
| P9 | Carga | k6 `pruebas/carga/carga.js` | CI | CI |
| P10 | Resiliencia | unitarias + humo + procedimientos manuales (§6) | | CI + demo |
| P11 | IA | unitarias (proveedor simulado) + `pruebas/ia/evaluar-ia.sh` (proveedor real, 12 casos) | | CI · manual con clave |
| P12 | Aceptación / exploratoria | Manual de usuario | `docs/Manual_de_Usuario.md` | Antes de la entrega |

No existen: E2E de interfaz (Playwright/Cypress), Testcontainers/Arquillian, Schemathesis/SoapUI, SCA de dependencias.

## 3. Entornos y datos

| Entorno | Cómo | Uso |
|---|---|---|
| Unitario | `./mvnw verify` | P1–P5 |
| Docker Compose | `cp .env.example .env` (completar) y `docker compose up -d --build` | P6–P10, demo |
| Local WildFly | `scripts/wildfly/configurar-wildfly.ps1` + PostgreSQL | Desarrollo |
| CI | GitHub Actions, Ubuntu, JDK 17 | Todo salvo P11 real y P12 |

Datos: `CargaDeDatosDemo` (4 profesionales, 4 pacientes, 3 turnos; usuarios `paciente`, `recepcion`, `medico`, `admin` con `MEDICONECTA_DEMO_CLAVE`). El humo crea pacientes con documentos aleatorios de 8 dígitos y cancela sus turnos.

## 4. Criterios de entrada y salida

**Entrada:** el build compila y `/api/salud` responde 200.

| Criterio de salida | Umbral | Resultado (20/11) | Estado |
|---|---|---|---|
| Tests unitarios | 100 % | 195/195 | ✔ |
| Cobertura negocio | ≥ 80 % líneas, ≥ 70 % ramas | 95,0 % / 82,6 % | ✔ |
| Mutation score dominio | ≥ 60 % | 85 % (338/396) | ✔ |
| Humo E2E | 100 % | 148/148 (CI run #4) | ✔ |
| Carga | p95 < 500 ms, error < 1 %, **50 usuarios × 5 min** | p95 22,18 ms, 0 %, **20 usuarios × 45 s** | ◐ |
| ZAP | 0 High; Medium justificadas | baseline: 0 FAIL, 3 WARN informativas; sin activo autenticado | ◐ |
| Secretos | 0 | gitleaks 0 (36 commits) | ✔ |
| IA | ≥ 80 % | 12/12 documentado el 19/11, sin salida versionada; no reejecutado | ◐ |
| Quality Gate Sonar | aprobado | no ejecutado | ✗ |
| Defectos Bloqueantes/Críticos abiertos | 0 | 0 | ✔ |
| Pipeline | ≤ 15 min | 4 min 12 s | ✔ |
| Suite 3 veces sin flaky | idéntica | no ejecutado | ✗ |

## 5. Riesgos (probabilidad × impacto)

| Riesgo | P | I | Nivel | Cobertura por pruebas |
|---|---|---|---|---|
| Acceso a datos de otro paciente (BOLA) | Media | Alto | **Alto** | `GestionTurnosTest`, `GestionPacientesTest`, humo seguridad en vivo (403) |
| Turnos superpuestos o duplicados | Media | Alto | **Alto** | `GestionTurnosTest`, Idempotency-Key (`FachadaReservasTest`, humo) |
| Pérdida de avisos si cae el broker | Baja | Alto | Medio | Mensajes persistentes + suscripciones durables; procedimiento manual §6 |
| Sistema externo colgado | Media | Medio | Medio | `TiemposDeEsperaTest`, `FallbacksTest` (sin circuit breaker) |
| Proveedor de IA caído o respuesta peligrosa | Media | Medio | Medio | `FallbacksTest`, `evaluar-ia.sh` (urgencias, injection, fuera de dominio) |
| Fuerza bruta en login | Media | Medio | Medio | `ServicioAutenticacionTest`, humo 429 |
| Regresión al integrar ramas | Alta | Alto | **Alto** | CI completo en cada push (lo detectó: D-09, D-10) |
| Demo en otra máquina | Media | Alto | **Alto** | Docker Compose verificado en CI |

## 6. Procedimientos de resiliencia en vivo

| Escenario | Procedimiento | Esperado |
|---|---|---|
| Timeout de 5 s (cobertura) | `jboss-cli`: `/system-property=mediconecta.simulacion.demoraMs:add(value=8000)` y consultar cobertura | ~5 s, `contingencia: true`, origen `CACHE` o `CONTINGENCIA` |
| IA caída | Quitar `MEDICONECTA_IA_CLAVE` y usar “Prefiero escribirlo” | Respaldo por palabras clave; urgencias detectadas |
| Broker caído | Pausar `ColaConfirmacionesMediConecta`, reservar, `kill -9` de WildFly, reiniciar | Confirmación entregada tras el reinicio (probado 19/11 por M. Bellinzona) |
| DLQ | Admin: `POST /api/mensajeria/mensaje-defectuoso` | 4 intentos (1, 2, 4 s) y aparece en `GET /api/mensajeria/mensajes-fallidos` |
| Idempotency-Key | Repetir `POST /api/turnos` con la misma clave | Mismo turno, `Idempotent-Replayed: true`; otro cuerpo → 422 |
| ETag | `PUT …/cancelar` con `If-Match` viejo | 412 |

## 7. Especificación de casos

Los casos de sistema están implementados en `scripts/smoke-test.sh`, agrupados por sección
(salud, cabeceras, contrato, seguridad, Problem Details, profesionales, pacientes, turnos,
Idempotency-Key, ownership, State, cobertura, asistente, mensajería, seguridad en vivo, ETag,
IA). Cada verificación indica dato, acción y resultado esperado. Por conteo automático de las
expectativas 4xx, **57 de 148 (38,5 %) son negativas**; sumando los casos de borde de las
unitarias (fechas pasadas, documentos inválidos, transiciones inválidas, límites de la IA) el
porcentaje sube, pero **falta una especificación caso por caso** con Id, precondición,
prioridad y tipo que demuestre el mínimo del 40 % exigido. Tarea pendiente para Jira.

## 8. Defectos

Ver [Registro_de_Defectos.md](Registro_de_Defectos.md) (D-01 a D-10, ninguno abierto bloqueante o crítico).

## 9. Responsables

| Rol | Integrante |
|---|---|
| QA responsable (cargado en Jira) | *(a definir por el equipo)* |
| Seguridad · Rendimiento · IA | *(a definir por el equipo)* |
