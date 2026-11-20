# MediConecta — Checklist de requisitos finales

**Fecha:** 20/11/2026 · **Código:** `eda4b02` · Fuente: *Criterios de Testing y Aseguramiento de Calidad* (Clase 15).
Estados: CUMPLE (evidencia ejecutada) · PARCIAL · NO CUMPLE · NO VERIFICABLE · PENDIENTE (acción del equipo).

## Condiciones de admisibilidad (eliminatorias)

| | Condición | Estado | Evidencia | Acción pendiente | Responsable |
|---|---|---|---|---|---|
| G1 | Build reproducible, un comando, ≤ 15 min | CUMPLE en CI | `docker compose up -d --build` en run #4; pipeline 4 min 12 s | Probar en una máquina limpia siguiendo solo el README | equipo |
| G2 | Tag en `main` | PENDIENTE | — | Revisar y mergear el PR a `facu`, luego a `main`; crear `v1.0-final` (o `v1.0-testing` si la cátedra lo exige) | equipo |
| G3 | CI en verde sobre el tag | PARCIAL | Run #4 verde en `integracion-final`; Sonar omitido | Re-ejecutar sobre el tag; configurar Sonar | equipo |
| G4 | Plan, matriz, informe de ejecución, defectos en Teams | PARCIAL | `documentacion-final/` | Subir a Teams; especificación caso por caso (≥ 40 % negativos) | QA |
| G5 | Jira actualizado | NO VERIFICABLE | — | Cargar casos, D-01…D-10 y O-01…O-03 | QA |
| G6 | Sin secretos en el historial | CUMPLE con riesgo | gitleaks 0 | Decidir sobre el secreto JWT antiguo (ver Informe de seguridad §2) | equipo |
| G7 | Demo en vivo | NO VERIFICABLE | Procedimientos en Plan de pruebas §6 | Ensayar con Docker Compose | equipo |
| G8 | Declaración de IA | PENDIENTE | Borrador final | Completar y firmar | cada integrante |
| G9 | Honestidad del informe | PARCIAL | Informe y registro declaran brechas | Contrastar con Jira | QA |

## Ejes

| Eje | Requisito | Estado | Acción |
|---|---|---|---|
| A | HU con ≥ 3 criterios Gherkin, MoSCoW | NO VERIFICABLE | Traer HU de la entrega del diseño/Jira y mapearlas en la matriz |
| A | ≥ 5 escenarios E2E de interfaz en CI | NO CUMPLE | Playwright/Cypress |
| A | Responsive 360/768/1280 y Lighthouse ≥ 90 | NO VERIFICABLE | Medir |
| A | Prueba del manual de usuario | PENDIENTE | Ensayar `docs/Manual_de_Usuario.md` |
| B | Cobertura negocio ≥ 80/70 | CUMPLE | 95,0 / 82,6 |
| B | Mutation ≥ 60 % | CUMPLE | 85 % |
| B | Quality Gate Sonar | NO CUMPLE (no ejecutado) | Configurar SonarCloud |
| B | Testcontainers/Arquillian | NO CUMPLE | Agregar |
| B | Colección de API en CI | PARCIAL | Humo bash; confirmar con la cátedra |
| B | Contrato OpenAPI + WSDL | PARCIAL | Validar respuestas y WSDL |
| B | 409 en transiciones inválidas | CUMPLE | `EstadoDelTurnoTest` + humo |
| B | Pipeline ≤ 15 min | CUMPLE | 4 min 12 s |
| B | 0 flaky (3 corridas) | NO VERIFICABLE | Correr 3 veces |
| C | Timeout + reintento/circuit breaker + fallback | PARCIAL | Falta reintento o circuit breaker |
| C | Broker caído | PARCIAL (manual 19/11) | Reensayar sobre la versión integrada |
| C | Duplicado, DLQ, rollback, Idempotency-Key, ETag, Problem Details | CUMPLE | Tests + humo |
| C | Carga 50 usuarios × 5 min | PARCIAL | Ajustar `carga.js` (hoy 20 × 45 s) |
| D | BOLA, BFLA, JWT, inyección, 429, cabeceras, errores | CUMPLE | Humo + tests |
| D | XXE, SCA, ZAP activo autenticado | NO CUMPLE / NO EJECUTADO | Agregar |
| E | Declaración y plan de IA | PENDIENTE | Firmas |
| E | Testing de la IA | PARCIAL | Reejecutar `evaluar-ia.sh` y versionar la salida |

**Veredicto:** la entrega **no está lista** para declararse aprobada; el código integrado está verificado y
publicado, pero quedan pendientes eliminatorios del equipo (G2, G5, G8) y brechas de los ejes A–E.
