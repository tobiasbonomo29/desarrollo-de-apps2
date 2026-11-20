# MediConecta — Declaración de uso de IA generativa

> Borrador del 18/11/2026. **El equipo tiene que revisarlo y completarlo** con lo que
> efectivamente usó cada integrante en las entregas anteriores: este documento solo puede
> afirmar con certeza lo que consta en el repositorio y en la sesión del checkpoint 3.

## 1. IA como funcionalidad del sistema

MediConecta incluye un asistente que sugiere la especialidad a partir de lo que la persona
cuenta (paso 1 de la reserva, "Prefiero escribirlo").

- **Proveedor:** API compatible con OpenAI "chat completions"; por omisión Groq con el
  modelo `openai/gpt-oss-120b` (`integraciones.AdaptadorIaGroq`). La clave vive solo en el
  servidor (`mediconecta.ia.clave` / `MEDICONECTA_IA_CLAVE`), nunca en el repositorio ni en
  el navegador.
- **Límites de diseño:** solo sugiere; la elección la hace la persona. Las especialidades
  sugeridas se validan contra las que tienen profesionales activos. No diagnostica ni
  indica tratamientos; ante signos de emergencia indica llamar al 107 / 911.
- **Resiliencia (checkpoint 3):** timeout de 5 s, circuit breaker y fallback por palabras
  clave (`OrientadorPorPalabrasClave`); la respuesta indica `origen: IA` o `origen: REGLAS`.
- **Datos enviados:** solo los mensajes de la conversación; no se envían nombre, documento
  ni historia clínica. La conversación no se guarda en el servidor.
- **Límite de uso:** 10 consultas por minuto por usuario (429).

## 2. IA como herramienta de desarrollo

| Etapa | Herramienta | Uso | Revisión humana |
|---|---|---|---|
| Entregas 1 y 2 | *(completar por el equipo)* | *(completar)* | *(completar)* |
| Checkpoint 3 (18/11/2026) | Claude Code (Anthropic), agente de programación | Diagnóstico del proyecto; implementación de resiliencia (timeout, circuit breaker, fallback), Idempotency-Key, ETag, Problem Details, consumidores idempotentes, DLQ, protección contra fuerza bruta, corrección de BOLA; tests unitarios nuevos; pipeline de CI; scripts de carga; borradores del plan de pruebas, la matriz de trazabilidad y este documento | *(completar: quién revisó cada cambio y qué se modificó)* |

## 3. Criterios aplicados

- Todo resultado de pruebas, cobertura y carga que figura en la documentación proviene de
  ejecuciones reales; lo que no se pudo ejecutar figura como pendiente.
- El código generado se integró respetando la arquitectura y las convenciones existentes y
  pasa los mismos tests y umbrales que el resto del proyecto.
- Cada integrante debe poder explicar cualquier parte del sistema en la defensa,
  independientemente de cómo se haya escrito.
