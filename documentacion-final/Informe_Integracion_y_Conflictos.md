# MediConecta — Informe de integración y resolución de conflictos

**Fecha:** 20/11/2026 · **Rama:** `integracion-final` · **Responsable de la integración:** Facundo Sánchez (`facu526`)

## 1. Punto de partida

| Repositorio | Rama | Último commit | Autor principal |
|---|---|---|---|
| Equipo — `tobiasbonomo29/desarrollo-de-apps2` | `facu` | `947ba9d` "checkpoint 3 - resiliencia, mensajeria confiable, contrato REST y auditoria" | Facundo Morán (`Jotafacu890`) |
| Bellinzona — `Bellinzona1/desarrollodeapps2` | `facu` | `052aa90` (8 commits) | Mateo Bellinzona |

Ancestro común: `3babcd4` (asistente de IA, 18/11). Las referencias remotas se verificaron con
`git ls-remote` antes de operar; coincidían con las informadas.

Diagnóstico: **las dos ramas reimplementaron en paralelo el mismo checkpoint** (resiliencia,
mensajería confiable, Problem Details, `Idempotency-Key`, `ETag`, seguridad, CI). `947ba9d`
tocó 136 archivos y la rama de Bellinzona 183; 77 archivos los modificaron ambas y el merge
dejó 66 en conflicto.

## 2. Estrategia

- **Merge real** (`git merge --no-ff bellinzona/facu` sobre `origin/facu`): el commit
  `4fa4efe` tiene como padres `947ba9d` y `052aa90`, así que el historial y la autoría de
  ambas ramas quedan intactos. No se reescribió ni se rebaseó ningún commit.
- **Base funcional:** el código de Bellinzona, como estaba acordado. Antes de resolver se
  comparó, componente por componente, qué ofrecía cada rama (ver §3) y qué tipos referenciaba
  cada versión de los archivos en conflicto.
- **No se usó resolución automática global** (`-X theirs`/`ours`). Se resolvió por áreas, con
  la justificación de cada una, y se revisaron también los archivos que Git había fusionado
  sin conflicto (5 de ellos mezclaban líneas de ambas ramas).
- Se incorporaron de `947ba9d` las correcciones que la base no tenía y que la cátedra exige.

## 3. Comparación de las dos implementaciones

| Capacidad | `947ba9d` (Morán) | `052aa90` (Bellinzona) | Queda en la versión final |
|---|---|---|---|
| Idempotency-Key | `RegistroIdempotencia`, `IdempotenciaException`, `LimpiezaClavesIdempotencia` | `ClaveIdempotencia`, `HuellaSolicitud`, `ConflictoIdempotenciaException`, `LimpiezaIdempotencia` | Bellinzona |
| Consumidor idempotente | `ConsumidorIdempotente` + tabla `mensaje_procesado` | `ConsumoDeEventos` + `Notificacion` con `UNIQUE (evento_id, tipo)` | Bellinzona |
| DLQ | `MonitorColaMuerta`, 3 intentos, 1 s | `MonitorMensajesFallidos`, **4 intentos, 1-2-4 s**, tabla `mensajes_fallidos` | Bellinzona |
| Timeout + fallback de cobertura | `ProteccionIntegracion` (timeout + **circuit breaker**), `MemoriaCobertura` | `TiemposDeEspera` (2 s / 5 s), `CacheCobertura` | Bellinzona (**sin circuit breaker**) |
| Fallback de IA | `OrientadorPorPalabrasClave` | `OrientacionDeRespaldo` | Bellinzona |
| Rate limiting | `ProteccionAbuso`, `LimitadorDeIntentos` | `LimitadorDeFrecuencia` | Bellinzona |
| Cabeceras HTTP | `FiltroCabecerasSeguridad`, `FiltroCacheApi` | `CabecerasSeguridad` | Bellinzona |
| 401 en Problem Details | `RespuestaNoAutenticado` + `error-page` en `web.xml` | dentro de `MecanismoAutenticacionJwt` | Bellinzona |
| Problem Details | `Problemas` propio | `Problemas` + `TipoProblema` (`urn:mediconecta:problema:*`) | Bellinzona |
| **Acceso a recurso ajeno (BOLA)** | **403** (`AccesoDenegadoException`) | 400 (`ReglaNegocioException`) | **Morán** (lo exige el eje D) |
| **Formato del documento** | **6–10 dígitos** (hallazgo de ZAP) | cualquier texto | **Morán** |
| Esquema de base | `hbm2ddl=update` + `scripts/db/esquema.sql` | Flyway V1–V3 + `validate` | Bellinzona |
| Build y CI | wrapper, JaCoCo, PIT, gitleaks, Sonar (4 jobs) | + enforcer, Checkstyle, migraciones, Docker Compose, humo, k6, ZAP (6 jobs) | Bellinzona |
| Despliegue | scripts PowerShell | + `Dockerfile`, `compose.yaml`, `.env.example` | Bellinzona |
| Tests | 203 (incluye `ApiRestTest`, `MensajeriaTest`, `ProteccionIntegracionTest`…) | 194 | Bellinzona + 1 test nuevo y aserciones nuevas → **195** |

## 4. Resolución por archivo

### 4.1 Archivos en conflicto (66)

| Área | Archivos | Decisión | Motivo |
|---|---|---|---|
| Integraciones | `AdaptadorIaGroq`, `AdaptadorObraSocialLegado`, `AdaptadorObraSocialPartner`, `GestorCobertura`, `GestorOrientacion`, `ResultadoCobertura`, `SugerenciaEspecialidad` | Bellinzona | La versión de Morán depende de `ProteccionIntegracion`, `MemoriaCobertura` y `OrientadorPorPalabrasClave` |
| Mensajería | `DestinosJms`, `NotificadorTurnos`, `ReceptorConfirmacion`, `SuscriptorAvisoPaciente`, `SuscriptorAvisoProfesional`, `RecursoMensajeria`, `configurar-mensajeria.cli` | Bellinzona | Morán depende de `MonitorColaMuerta`/`DiagnosticoMensajeria`; la DLQ final usa 4 intentos con espera creciente |
| Idempotencia y REST | `RepositorioIdempotencia` (+ JPA y memoria), `FachadaReservas`, `ResultadoReserva`, `Etiquetas`, `RecursoTurnos`, `RecursoPacientes` | Bellinzona | Morán depende de `RegistroIdempotencia`/`IdempotenciaException` |
| Problem Details | `DetalleProblema`, `Problemas`, 7 `Manejador*`, `RecursoAutenticacion` | Bellinzona + mapeo 403 | Una sola taxonomía (`TipoProblema`) usada por OpenAPI y el humo |
| Seguridad | `MecanismoAutenticacionJwt`, `ServicioJwt`, `ServicioAutenticacion` | Bellinzona | Morán depende de `ProteccionAbuso` |
| Negocio | `GestionTurnos` | Bellinzona + 4 `AccesoDenegadoException` | 403 en recursos ajenos |
| Build/CI | `pom.xml`, `ci.yml`, `mvnw`, `mvnw.cmd`, `persistence.xml`, `beans.xml`, `smoke-test.ps1` | Bellinzona | Enforcer, Checkstyle, Flyway, Sonar, alcance de PIT sobre el dominio, 6 jobs |
| Esquema | `scripts/db/esquema.sql` (modificado por Morán, borrado por Bellinzona) | Borrado | Lo reemplazan las migraciones Flyway |
| Frontend | `turno-nuevo.html`, `styles.css`, `index.html`, `login.html`, `api.js`, `dashboard.js`, `mis-turnos.js`, `profesionales.js`, `reserva.js` | Bellinzona | Rediseño con `layout.js` y fuentes locales; los cambios de Morán apuntaban a su formato de errores |
| Documentos raíz | `README.md`, `CLAUDE.md`, `docs/arquitectura.md` | Bellinzona, luego actualizados | Describen la implementación adoptada |
| Tests | `AsistenteReservaTurnoTest`, `Escenario`, `FachadaReservasTest`, `GestionPacientesTest`, `GestionProfesionalesTest`, `GestionTurnosTest`, `ServicioAutenticacionTest`, `MecanismoAutenticacionJwtTest` | Bellinzona + aserciones nuevas | Prueban la implementación adoptada |

### 4.2 Archivos fusionados sin conflicto pero revisados

| Archivo | Hallazgo | Decisión |
|---|---|---|
| `GestionPacientes.java` | Git mezcló validación de documento y 403 de Morán con el código de Bellinzona, y duplicó el control de propiedad en `buscarPorDocumento` | Se conservan la validación y el 403; se quita el control duplicado (Bellinzona ya lo hace en `consultarFicha`/`historialDeTurnos`) |
| `GestionProfesionales.java` | 403 de Morán | Se conserva |
| `pacientes.html` | `pattern="[0-9]{6,10}"` de Morán | Se conserva (coherente con la validación) |
| `AsistenteReservaTurno.java` | Dos javadocs para el mismo `@PermitAll` | Bellinzona |
| `web.xml` | `error-page` 401 hacia `RespuestaNoAutenticado`, que se retira | Bellinzona |
| `configurar-wildfly.ps1` | Paso 5 de Morán duplicaba la configuración de mensajería que Bellinzona ya aplica | Bellinzona |
| `Inyeccion.java` (tests) | Morán lo hizo público para sus tests de otros paquetes | Bellinzona (esos tests no se conservan) |
| `docs/manual-usuario.md` | Versión actualizada por Morán | Bellinzona en `docs/`; la de Morán, en el histórico |

### 4.3 Archivos que solo existían en `947ba9d`

| Tipo | Archivos | Decisión |
|---|---|---|
| Código alternativo (23 clases) | `ProteccionIntegracion`, `MemoriaCobertura`, `OrientadorPorPalabrasClave`, `ConsumidorIdempotente`, `DiagnosticoMensajeria`, `EstadoMensajeria`, `MensajeTurno`, `MonitorColaMuerta`, `MensajeProcesado`, `RegistroIdempotencia`, `RepositorioMensajesProcesados*`, `DemasiadosIntentosException`, `IdempotenciaException`, `LimpiezaClavesIdempotencia`, `FiltroCabecerasSeguridad`, `FiltroCacheApi`, `ManejadorDemasiadosIntentos`, `ManejadorIdempotencia`, `ManejadorPropiedadAjena`, `LimitadorDeIntentos`, `ProteccionAbuso`, `RespuestaNoAutenticado` | Se retiran del árbol (siguen en el historial). Duplicaban beans, tablas y mappers de la base |
| Código incorporado | `AccesoDenegadoException` | Se conserva; mapeada a 403 en `Problemas` |
| Tests (11 clases) | `AdaptadorIaGroqHttpTest`, `CoberturaResilienteTest`, `OrientacionIaTest`, `OrientadorPorPalabrasClaveTest`, `ProteccionIntegracionTest`, `MensajeriaTest`, `SoporteNegocioTest`, `ApiRestTest`, `AutorizacionDeclaradaTest`, `ManejadoresTest`, `ProteccionAbusoTest` | Se retiran: prueban las clases alternativas. Se intentó conservar `AutorizacionDeclaradaTest`; falló porque referencia `DiagnosticoMensajeria` y exige anotaciones en `SondaMensajeria.responde` y `MonitorSalud.verificar` (ver §6) |
| Documentación y evidencias | `Checkpoint_3.md`, `Informe_Auditoria.md`, `plan-de-pruebas.md`, `matriz-trazabilidad.md`, `declaracion-uso-ia.md`, `evidencias/entrega3/*` | Movidos a `docs/historico/entrega3-947ba9d/` con un README que aclara su alcance |
| Scripts de prueba | `prueba-carga.py`, `pruebas-seguridad.py`, `proveedor-ia-simulado.py`, `zap-api.yaml` | Movidos al histórico; no se reverificaron contra la versión final (no había Python en la máquina) |

## 5. Errores causados por la integración y corregidos

| Commit | Error | Corrección |
|---|---|---|
| `9c4a39e` | `mvnw` quedó `100644` al resolver el conflicto en Windows; tres jobs de CI fallaron con *Permission denied* | Modo `100755` restaurado; se verificó que `smoke-test.sh` y `evaluar-ia.sh` conservaran el suyo |
| `eda4b02` | Una verificación del humo seguía esperando 400 en un recurso ajeno (el sistema ya respondía 403) | Expectativa corregida; humo 148/148 |

## 6. Verificación

- Local (JDK 21, `./mvnw` 3.9.9): `clean verify` → 195 tests, 0 fallas, Checkstyle sin violaciones, umbrales de JaCoCo cumplidos; PIT 85 % (338/396).
- GitHub Actions, run #4 sobre `eda4b02`: los 6 jobs en verde; Sonar se omite por falta de token (no hay Quality Gate).
- Observación no corregida (fuera del alcance de la integración): `SondaMensajeria.responde()` y `MonitorSalud.verificar()` son métodos públicos de EJB sin `@RolesAllowed`/`@PermitAll`. El humo de CI muestra que `/api/salud` responde, por lo que hoy no causan falla; conviene anotarlos explícitamente.

## 7. Atribución

Los commits `4fa4efe`, `9c4a39e` y `eda4b02` (y el de documentación posterior) se crearon con la
identidad `Facundo Sanchez <155477256+facu526@users.noreply.github.com>`, configurada solo en
este repositorio local. El email *noreply* corresponde al ID de GitHub 155477256 de `facu526`.
Los commits anteriores conservan sus autores, fechas y firmas originales: 22 de Facundo
Sánchez (previos a la integración), 9 de Mateo Bellinzona, 3 de `tobiasbonomo29` y 1 de
`Jotafacu890` (Facundo Morán). Los commits de esta integración incluyen la línea
`Co-Authored-By` de la herramienta de IA que asistió el trabajo.
