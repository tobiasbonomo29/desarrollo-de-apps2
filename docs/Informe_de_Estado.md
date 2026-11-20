# MediConecta — Informe de estado (3.er checkpoint, 20/11/2026)
> **Versión superada (v1, 19/11/2026).** La versión final, basada en la rama `integracion-final`, está en [`documentacion-final/`](../documentacion-final/README.md).

Informe honesto de qué está hecho, cómo se midió y qué falta. Base para el informe final.

## 1. 3.er checkpoint: estado de cada punto

| Punto | Estado | Evidencia |
|---|---|---|
| Integraciones SOAP, REST y mensajería funcionando | ✔ | Prueba de punta a punta: cobertura por SOAP (OSDE) y por REST (Swiss Medical), tres consumidores JMS |
| Timeout de 5 s con fallback | ✔ | Cobertura: último resultado bueno o "a verificar". IA: orientación por palabras clave. `FallbacksTest`; demo con `mediconecta.simulacion.demoraMs` |
| Consumidores idempotentes y DLQ | ✔ | `GestionNotificacionesTest`, `ConsumoDeEventosTest`; mensaje defectuoso → 4 intentos → `MensajesFallidos` |
| Si se apaga el broker no se pierden mensajes | ✔ | Probado el 19/11: cola pausada, `kill -9` de WildFly, reinicio, la confirmación pendiente se entregó (mensajes persistentes, suscripciones durables) |
| Idempotency-Key y ETag | ✔ | `POST /api/turnos` con `Idempotency-Key`; `ETag` / `If-None-Match` (304) / `If-Match` (412) en turnos y pacientes |
| Problem Details | ✔ | Todo error en `application/problem+json`; `ProblemasTest` |
| IA funcionando a través de su gateway | ✔ | `GestorOrientacion`; evaluación 12 casos con el modelo real |
| CI con reporte de cobertura parcial | ✔ | GitHub Actions: resumen por paquete y artefacto JaCoCo |
| Plan de pruebas y matriz de trazabilidad (v1) | ✔ | [Plan_de_Pruebas.md](Plan_de_Pruebas.md), [Matriz_de_Trazabilidad.md](Matriz_de_Trazabilidad.md) |
| Manual de usuario (v1) | ✔ | [Manual_de_Usuario.md](Manual_de_Usuario.md) |

## 2. Entrega final: condiciones G1–G9

| Condición | Estado | Comentario |
|---|---|---|
| G1 Build reproducible | ✔ | `./mvnw` fija Maven 3.9.9; el enforcer exige JDK ≥ 17 y versión fija en todos los plugins; Docker construye desde cero |
| G2 Tag `v1.0-final` en `main` antes de las 08:00 | **Pendiente** | Se hace al cerrar la entrega: `git tag -a v1.0-final -m "Entrega final" && git push origin v1.0-final` sobre `main` del repositorio del equipo |
| G3 Pipeline de CI en verde | ✔ | Se verifica en cada push |
| G4 Jira al día | **Pendiente (equipo)** | No se puede resolver desde el código |
| G5 Sin secretos en el historial | ✔ | gitleaks sobre todos los commits: 0 hallazgos; job `secretos` en CI |
| G6 Demo en vivo | Preparada | Guion de resiliencia en el plan de pruebas §6 |
| G7 Declaración de uso de IA | Borrador | [Declaracion_Uso_IA.md](Declaracion_Uso_IA.md): falta que cada integrante la complete y la firme |
| G8 Informe honesto | Este documento (v1) | |
| G9 *(según el PDF)* | Revisar | *(completar con la condición del PDF de Criterios de Testing)* |

## 3. Ejes A–E: mediciones del 19/11/2026

| Umbral | Medido | Cómo |
|---|---|---|
| Cobertura de negocio ≥ 80 % líneas y ≥ 70 % ramas | **95,0 % / 82,5 %** | JaCoCo (`./mvnw verify` corta si baja) |
| Mutation score ≥ 60 % | **85 %** (337 de 395 mutantes) | PIT sobre `negocio` y `modelo.estado` |
| Quality Gate de Sonar aprobado | **Sin medir** | El job está listo; falta crear el proyecto en SonarCloud y cargar `SONAR_TOKEN` |
| Carga p95 < 500 ms | **35,6 ms** (0 % errores, ~200 req/s, 20 usuarios) | k6, en una Mac con WildFly local; CI la repite en cada push |
| BOLA/IDOR, BFLA, JWT inválidos, inyecciones, 429 | ✔ en la prueba de punta a punta | `scripts/smoke-test.sh`, sección "seguridad en vivo" |
| ZAP sin alertas High | **0 High, 0 Medium, 0 Low** | ZAP baseline; antes del endurecimiento había 3 Medium en la página de bienvenida de WildFly y 1 en la app (Google Fonts sin SRI), corregidas |
| Testing de IA | **12/12** con el modelo real | `pruebas/ia/evaluar-ia.sh` |
| Defectos Bloqueantes/Críticos abiertos | **0** | Plan de pruebas §8 (6 defectos encontrados y corregidos) |

## 4. Limitaciones y cosas que no se hicieron

- **No hay tests automáticos de la interfaz** (Selenium/Playwright); la interfaz se prueba
  a mano siguiendo el manual y con ZAP.
- **La versión PowerShell de la prueba de punta a punta (`smoke-test.ps1`) no se ejecutó
  después de los últimos cambios** (se trabajó en macOS sin PowerShell). La versión bash es
  la que corre en CI y la más completa.
- El broker JMS está **embebido** en WildFly: "apagar el broker" se prueba matando el
  servidor. Con un broker externo se podría apagar por separado.
- El limitador de intentos y la caché de cobertura viven **en memoria de una instancia**;
  con varias instancias habría que llevarlos a un almacén compartido.
- Los avisos (mail, SMS, agenda) se **registran** pero no se envían a un proveedor real.
- La evaluación de IA depende del modelo del proveedor: puede variar entre corridas.
  En la primera corrida un caso "falló" porque las pruebas de punta a punta crean un
  profesional de Dermatología y el caso suponía que esa especialidad no existía. Se
  corrigió el caso, no el sistema.
- **Gestión:** la devolución observó que el 88 % de los commits era de una persona. El
  trabajo de esta etapa también lo hizo una sola persona (con asistencia de IA, ver la
  declaración). Hay que repartir la defensa y el trabajo restante.

## 5. Próximos pasos

1. Crear el proyecto en SonarCloud y aprobar el Quality Gate.
2. Correr `smoke-test.ps1` en Windows.
3. Poner Jira al día; repartir la defensa por bloques.
4. Completar y firmar la declaración de uso de IA.
5. Mergear a `main` del repositorio del equipo, verificar CI en verde y crear el tag
   `v1.0-final` antes de las 08:00 del día de la exposición.
