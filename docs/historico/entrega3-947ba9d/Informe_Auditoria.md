# MediConecta — Informe de auditoría técnica (entrega 2, checkpoint 3 y entrega final)

> 18/11/2026. Auditoría con **ejecución real**: build desde un entorno limpio, 203 tests
> unitarios y de integración, despliegue en WildFly 36.0.1.Final (`standalone-full.xml`),
> smoke test end-to-end, pruebas de seguridad, OWASP ZAP, carga, mensajería con reinicio
> del broker e IA contra un proveedor simulado. No se hicieron commits, pushes ni tags.

## 0. Alcance y limitaciones del entorno

| Elemento | Situación |
|---|---|
| Documento "Criterios de Testing" | **No está en el entorno** (se buscó en el repositorio, en la otra copia `D:\UADE\DesApps2\TPO` y en Descargas/Documentos). Los umbrales se tomaron del pedido: 80 % líneas, 70 % ramas, 60 % mutación, p95 < 500 ms, ZAP sin High. Las condiciones G1–G9 **no se pueden contrastar con el PDF oficial**: solo está `Resumen_TP_Integrador_Desarrollo_Aplicaciones_II.pdf`, que no las enumera |
| PostgreSQL / Docker | **No disponibles.** Todas las pruebas sobre servidor usaron el plan B en memoria del proyecto (sin cambiar el código, siguiendo `beans.xml`) |
| Jira | El equipo usa **Trello** (`docs/trello-actualizaciones.md`); no hay acceso desde este entorno |
| Sonar / GitHub Actions | Sin `SONAR_TOKEN` ni ejecución de Actions desde acá |
| Clave del proveedor de IA | No hay; se usó un proveedor simulado local |

Estados: **APROBADO** = verificado con evidencia · **CORREGIDO Y APROBADO** = se encontró un
problema, se corrigió y se reverificó · **PARCIAL** · **NO CUMPLE** · **NO VERIFICABLE**.

## 1. Segunda entrega parcial (13/11)

| Requisito | Estado | Evidencia | Problema encontrado | Corrección realizada |
|---|---|---|---|---|
| Componentes en capas y desplegados | APROBADO | WAR desplegado en WildFly 36; `presentacion` → `negocio` → `datos` | — | — |
| Flujo completo de punta a punta | APROBADO | Smoke 119/119: reserva por API y por asistente stateful, con aviso JMS | — | — |
| Patrones de diseño | APROBADO | State, Factory, Facade, Adapter (dos familias), Repository; tests PU-01/02/06/12/13 | — | — |
| Patrón State en la entidad central | APROBADO | `modelo.estado`, `Turno.cancelar/atender/archivar` | — | — |
| Transiciones inválidas → 409 | APROBADO | Smoke: cancelar dos veces, archivar dos veces, PROGRAMADO→archivar, ATENDIDO→atender/cancelar = 409; `EstadoDelTurnoTest` | — | — |
| JWT funcionando | APROBADO | Login → token; `ServicioJwtTest`, smoke | — | — |
| Control de acceso por roles | APROBADO | `AutorizacionDeclaradaTest` + 11 casos BFLA en vivo | — | — |
| Rechazo sin autenticación | CORREGIDO Y APROBADO | 401 Problem Details en vivo | Cada 401 dejaba un stack trace en el log (inundación de disco) | Error page 401 (`RespuestaNoAutenticado`) |
| Rechazo sin permisos | APROBADO | 403 en vivo (BFLA) | — | — |
| Validación de tokens | APROBADO | 7 casos en vivo: ausente, basura, `alg=none`, otro secreto, rol alterado, vencido bien firmado, emisor ajeno → 401 | — | — |
| Mensajería en un proceso de negocio | APROBADO | Reserva → cola + tópico; log de los 3 consumidores | — | — |
| Primeros tests unitarios | APROBADO | 203 tests (eran 58) | — | — |
| Feedback de diseño incorporado | NO VERIFICABLE | `Entrega_Obligatoria_2.md` §9.6 dice "Pendiente de completar" | No está documentado qué observó la cátedra | — (debe completarlo el equipo) |
| Tareas pendientes del tablero | NO VERIFICABLE | Sin acceso a Trello/Jira | — | — |

## 2. Tercer checkpoint (20/11)

| Requisito | Estado | Evidencia | Problema encontrado | Corrección realizada |
|---|---|---|---|---|
| API REST | CORREGIDO Y APROBADO | Smoke 119/119; ZAP API autenticado sin 500 | `POST /api/pacientes` con caracteres especiales en el documento → 500 con el paciente ya guardado | DNI numérico validado en el negocio; `Location` codificado |
| SOAP | APROBADO | Smoke: WSDL publicado y consulta OSDE por SOAP | — | — |
| Mensajería en los procesos | APROBADO | Smoke + log | — | — |
| Comunicación entre componentes | APROBADO | Fachada → 3 componentes + JMS; asistente stateful → fachada | — | — |
| Timeout de 5 s | APROBADO | En vivo: proveedor IA lento cortado a los **5,07 s**; `ProteccionIntegracionTest` | — | — |
| Fallback | APROBADO | En vivo: IA → `origen REGLAS`; SOAP caído → último dato bueno / `SIN_VERIFICAR` | — | — |
| Manejo de excepciones | CORREGIDO Y APROBADO | Problem Details en todos los errores; 500 sin detalle interno | Error de `@Remove` del stateful al expirar la sesión (`EJBAccessException`) | `@PermitAll` en `finalizar()` |
| Fallas simuladas | APROBADO | Circuit breaker abierto en la 3.ª falla (log `[RESILIENCIA] ... ABIERTO`) | — | — |
| Consumidores idempotentes | APROBADO | `MensajeriaTest`; `procesadosPorConsumidor` en vivo | — | — |
| DLQ | APROBADO | Mensaje veneno: 3 entregas fallidas → `ColaConfirmacionesDLQ` → `MonitorColaMuerta` | Retardo de 1 s entre reintentos no observado | — (no afecta la DLQ; documentado) |
| Persistencia de mensajes / reinicio del broker | APROBADO | Cola (turno 22) y tópico durable (turno 4) procesados tras `:shutdown(restart=true)` | — | — |
| Duplicados y fallidos | APROBADO | Duplicados: unitario (el broker no permite forzarlos a mano); fallidos: DLQ en vivo | — | — |
| Idempotency-Key | APROBADO | Smoke: reintento → mismo turno + `Idempotency-Replayed`; otro cuerpo → 422 | — | — |
| ETag / condicionales | APROBADO | Smoke: 304 y 412; `ApiRestTest` | — | — |
| Problem Details | CORREGIDO Y APROBADO | `application/problem+json` en 400–502 | El 401 provocaba error en el log (ver entrega 2) | Error page 401 |
| Códigos HTTP | APROBADO | 400/401/403/404/405/409/412/415/422/429/500/502 verificados | — | — |
| IA vía gateway | APROBADO | En vivo con proveedor simulado: `origen IA` (0,09 s), cabecera `Authorization` y modelo correctos | — | — |
| IA: errores, timeout, indisponibilidad | APROBADO | 503 → fallback inmediato; lento → 5,07 s; vuelve a `IA` al recuperarse; `AdaptadorIaGroqHttpTest` | — | — |
| IA con el proveedor real | NO VERIFICABLE | Sin clave en el entorno | — | — |
| Pipeline de CI configurado | PARCIAL | `.github/workflows/ci.yml` (build+cobertura, PIT, gitleaks, Sonar opcional); comandos verificados localmente | No se pudo ejecutar en GitHub. El token `alg=none` literal del smoke habría hecho fallar gitleaks | Token generado en tiempo de ejecución |
| Build reproducible | APROBADO | Copia limpia + repositorio Maven vacío: `mvnw clean verify` OK en 43 s | — | — |
| Tests automáticos y cobertura parcial | APROBADO | `verify`: 203 tests, reporte JaCoCo y umbrales | — | — |
| Plan de pruebas v1 | APROBADO | `docs/plan-de-pruebas.md` (actualizado con esta auditoría) | — | — |
| Matriz de trazabilidad | APROBADO | `docs/matriz-trazabilidad.md` | — | — |
| Manual de usuario v1 | APROBADO | `docs/manual-usuario.md` | — | — |

## 3. Entrega final (27/11 o 04/12)

| Requisito | Estado | Evidencia | Problema encontrado | Corrección realizada |
|---|---|---|---|---|
| G: build reproducible | APROBADO | Ver checkpoint | — | — |
| G: tag `v1.0-final` en `main` antes de las 08:00 | NO VERIFICABLE | Trabajo en rama `facu`, sin commit | — | Manual (§6) |
| G: CI en verde | NO VERIFICABLE | Falta push y la primera corrida | — | — |
| G: Jira actualizado | NO VERIFICABLE | Sin acceso (el equipo usa Trello) | — | — |
| G: sin secretos en código e historial | PARCIAL | Código: sin secretos fijos. Historial (26 commits): sin claves con formato conocido ni literales JWT. Conserva el antiguo secreto JWT de desarrollo (en desuso) | gitleaks no se pudo ejecutar localmente (bloqueado por permisos) | Job gitleaks en el CI |
| G: demo en vivo | CORREGIDO Y APROBADO | Plan B documentado + smoke 119/119 | El plan B tal como estaba documentado no desplegaba (`WFLYWELD0037`) | `<scan><exclude>` de `datos.jpa` en `beans.xml` |
| G: declaración de uso de IA | PARCIAL | `docs/declaracion-uso-ia.md` | Faltan las entregas 1 y 2 y la revisión humana | Borrador listo para completar |
| G: informe técnico honesto | APROBADO | Este documento + plan de pruebas + evidencias | — | — |
| G restantes (hasta G9) | NO VERIFICABLE | Sin el PDF oficial de criterios | — | — |
| Cobertura de la capa de negocio ≥ 80 % líneas | APROBADO | **94,3 %** (total 84,3 %) | — | — |
| Cobertura de la capa de negocio ≥ 70 % ramas | APROBADO | **90,2 %** (total 83,1 %) | — | — |
| Mutation score ≥ 60 % | APROBADO | **77 %** total, **85 %** en negocio | — | — |
| Quality Gate de Sonar | NO VERIFICABLE | Sin `SONAR_TOKEN` | — | Job listo en el CI |
| Carga p95 < 500 ms | PARCIAL | **p95 110,7 ms**, 46.791 pedidos, 0 errores (20 usuarios, 60 s) | Medido sin PostgreSQL | — |
| BOLA/IDOR | APROBADO | 9 casos en vivo → 403 | — (corregido en la ronda anterior) | — |
| BFLA | APROBADO | 11 casos en vivo + test por reflexión | — | — |
| JWT inválidos | APROBADO | 7 casos en vivo | — | — |
| Inyecciones | CORREGIDO Y APROBADO | SQL/JPQL, JSON, XSS y *prompt injection*; ZAP activo | XSS reflejado (ZAP, High) en `documento` | Validación de DNI |
| Fuerza bruta → 429 | APROBADO | Smoke + 612 respuestas 429 durante el ataque de ZAP | — | — |
| OWASP ZAP sin High | CORREGIDO Y APROBADO | 0 High/Medium/Low en la app (web + API autenticada, 3509 pedidos) | XSS reflejado (High), CSP `unsafe-inline` (Medium), cookie sin SameSite (Low), 25 respuestas 500 | Validación, clases CSS, `undertow-handlers.conf` |
| IA: integración, errores, timeout, tests | APROBADO | En vivo + `OrientacionIaTest`, `AdaptadorIaGroqHttpTest` | — | — |
| Sin defectos bloqueantes o críticos abiertos | APROBADO (para lo ejecutable) | 7 defectos encontrados, 7 corregidos y reverificados | — | — |

## 4. Defectos encontrados en esta auditoría

| # | Defecto | Severidad | Detección | Corrección | Verificación |
|---|---|---|---|---|---|
| 1 | Inundación del log con stack traces en cada 401 | Alta | ZAP (20 MB en un escaneo) | `MecanismoAutenticacionJwt` + `RespuestaNoAutenticado` + `<error-page>` | 52 pedidos 401 → 0 líneas de log |
| 2 | 500 y paciente persistido con documento inválido | Alta | ZAP | `GestionPacientes.FORMATO_DOCUMENTO`, `Location` con plantilla | 0 respuestas 500 en el escaneo final; tests nuevos |
| 3 | XSS reflejado en `documento` | Alta (ZAP) | ZAP | Ídem 2 | ZAP final sin High |
| 4 | Plan B documentado no desplegaba | Alta para la demo | Despliegue | `beans.xml` | Desplegado + smoke 119/119 |
| 5 | `@Remove` rechazado al expirar la sesión | Media | Log | `@PermitAll` | Sin `EJBAccessException` en los redespliegues siguientes |
| 6 | CSP con `'unsafe-inline'` | Media | ZAP | 22 estilos en línea → clases CSS | CSP sin `unsafe-inline`; ZAP sin la alerta |
| 7 | Cookie sin `SameSite`; JWT literal que gitleaks marcaría | Baja / Media (CI) | ZAP / revisión | `undertow-handlers.conf`; token generado en el smoke | `Set-Cookie ... SameSite=Strict`; smoke 119/119 |

## 5. Pruebas ejecutadas (resultados reales, 18/11/2026)

| Prueba | Resultado |
|---|---|
| `mvnw clean verify` (código final) | 203 tests, 0 fallas; cobertura 84,3 % / 83,1 % |
| Build limpio (repositorio Maven vacío) | OK, 43 s, WAR sin bibliotecas |
| PIT | 762/994 = 77 %; test strength 88 % |
| Smoke test (WildFly 36, plan B documentado) | 119/119 |
| `pruebas-seguridad.py` | 47/47 |
| ZAP web (sin autenticar) | 0 High; 1 Medium fuera del WAR (raíz de WildFly) |
| ZAP API autenticada | 0 High / 0 Medium / 0 Low |
| Carga | p95 110,7 ms, 0 errores |
| IA en vivo (simulado) | ok / lento (5,07 s) / error / recuperación: los 4 casos correctos |
| Cobertura SOAP caída | contingencia, SIN_VERIFICAR y circuito abierto |
| Recuperación del broker | Cola y tópico durable OK |

Evidencias en [`evidencias/entrega3/`](evidencias/entrega3/).

## 6. Pendientes y acciones manuales

1. Hacer push y verificar los 4 jobs del CI (incluido gitleaks).
2. Configurar SonarCloud (`SONAR_TOKEN`, `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY`) y confirmar el Quality Gate.
3. Repetir smoke, recuperación, carga, `pruebas-seguridad.py` y ZAP **con PostgreSQL**.
4. Probar el asistente con la clave real del proveedor (`MEDICONECTA_IA_CLAVE`) en el servidor de la demo.
5. Completar `Entrega_Obligatoria_2.md` §9.6 (feedback de diseño) y `docs/declaracion-uso-ia.md`.
6. Actualizar Trello/Jira.
7. Conseguir el PDF de criterios (G1–G9) y contrastarlo con este informe.
8. Configurar CSP en la raíz de WildFly del servidor de la demo (opcional; fuera del WAR).
9. Crear el tag `v1.0-final` en `main` antes de las 08:00 del día de la exposición.
10. Ensayar la demo.
