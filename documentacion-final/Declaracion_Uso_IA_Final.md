# MediConecta — Declaración de uso de IA generativa (versión final)

> **Estado: BORRADOR PENDIENTE DE VALIDACIÓN Y FIRMA.** Este documento reúne lo que consta en el
> repositorio y en los commits. Cada integrante debe revisar la sección 3, completar lo que hizo
> personalmente (herramienta, versión, prompt representativo, qué aceptó y qué descartó) y firmar.
> No se completaron datos que no constan en el repositorio.

## 1. IA como funcionalidad del producto

| | |
|---|---|
| Funcionalidad | Asistente "Prefiero escribirlo" del paso 1 de la reserva: sugiere una especialidad |
| Proveedor y modelo | Groq (API compatible con OpenAI), `openai/gpt-oss-120b` por omisión (configurable) |
| Acceso | Solo desde el servidor (`GestorOrientacion` → `AdaptadorIaGroq`); la clave no llega al navegador ni al repositorio |
| Controles | Prompt de sistema (no diagnosticar ni recetar, urgencias al 107/911, fuera de tema), ≤ 20 mensajes y ≤ 1000 caracteres, 10 consultas/min por usuario, especialidad inexistente descartada, timeout 2 s / 5 s, fallback por palabras clave |
| Datos enviados | Solo el texto del chat y la lista de especialidades; sin nombre, documento ni historia clínica; la conversación no se guarda |
| Pruebas | Unitarias con proveedor simulado (`AdaptadorIaGroqTest`, `GestorOrientacionTest`, `FallbacksTest`, en verde en CI); evaluación real `pruebas/ia/evaluar-ia.sh` con 12 casos (orientación, urgencias, *prompt injection* directa e indirecta, fuera de dominio, especialidades inexistentes) |
| Resultado real | Documentado por M. Bellinzona el 19/11: 11/12 y luego 12/12 tras corregir un caso mal planteado (la IA tenía razón: Dermatología existía por los datos del humo). **Sin salida versionada; no reejecutado sobre la versión integrada** |

## 2. IA usada en el desarrollo y el testing

| Herramienta | Uso que consta | Cómo se verificó |
|---|---|---|
| Claude Code (Anthropic) | Implementación, integraciones, seguridad, tests, CI, documentación (declarado en `docs/Declaracion_Uso_IA.md` y en `docs/Entrega_Obligatoria_1.md` §12). Los commits de la integración final (`4fa4efe`, `9c4a39e`, `eda4b02` y el de documentación) llevan la línea `Co-Authored-By: Claude` | Build, 195 tests, PIT, CI con Docker Compose, humo, k6, ZAP, gitleaks |
| Codex CLI (OpenAI) | Previsto como revisor independiente de la integración; **no se pudo usar** (el modelo configurado no está disponible para la cuenta) | — |
| *(completar)* | Otras herramientas que haya usado cada integrante (Copilot, ChatGPT, etc.) | *(completar)* |

### Prompt representativo (integración final)

> "Integrar los dos repositorios (Bellinzona como base funcional), conservar historial y
> evidencias, resolver conflictos justificando cada decisión, verificar build, tests, cobertura,
> PIT, CI y secretos, y generar la documentación final basada solo en evidencia ejecutada."

### Casos en que la IA se equivocó y cómo se detectó

1. Al resolver el conflicto de `mvnw` en Windows, la IA dejó el archivo sin permiso de ejecución. Lo detectó el CI (D-09) y se corrigió.
2. Al cambiar BOLA de 400 a 403, la IA actualizó 11 expectativas del humo pero omitió una; el CI la marcó como fallida (D-10).
3. *(Bellinzona)* En la evaluación de IA, un caso suponía que Dermatología no existía; el error era del caso, no del sistema.

### Qué se descartó

- El test `AutorizacionDeclaradaTest` de `947ba9d`: la IA intentó conservarlo, pero dependía de clases retiradas y fallaba.
- La generación del informe en Word por automatización: se colgaba; se dejó el PDF generado desde HTML.

## 3. Plan de IA (8.2)

| Herramienta | Objetivo medible | Riesgo que controla |
|---|---|---|
| Playwright con generación asistida de escenarios desde las HU | ≥ 5 escenarios E2E en CI | Regresiones de interfaz (eje A) |
| Evaluación de IA automatizada en CI con proveedor real en un job manual | ≥ 80 % de aciertos con salida versionada | Cambios de comportamiento del modelo |
| Revisión de código con IA en cada PR (revisor independiente) | 0 hallazgos críticos sin tratar | Errores de integración como D-09/D-10 |

## 4. Responsabilidad y firmas

Todo test o cambio asistido por IA debe estar revisado por un integrante que pueda explicarlo en la defensa. Un "CUMPLE" sin test ni evidencia se considera NO VERIFICABLE.

| Integrante | Partes que revisó | Firma | Fecha |
|---|---|---|---|
| Facundo Sánchez | Integración final y documentación (pendiente de confirmar) | | |
| Mateo Bellinzona | | | |
| Facundo Morán | | | |
| Tobías Bonomo | | | |
| Laureano Gómez Moreno | | | |
