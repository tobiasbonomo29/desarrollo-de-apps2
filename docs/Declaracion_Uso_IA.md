# MediConecta — Declaración de uso de inteligencia artificial
> **Versión superada (v1, 19/11/2026).** La versión final, basada en la rama `integracion-final`, está en [`documentacion-final/`](../documentacion-final/README.md).

## 1. IA como parte del producto

MediConecta **incluye** una funcionalidad de IA: el asistente "Prefiero escribirlo" del
primer paso de la reserva, que sugiere una especialidad a partir de lo que cuenta la
persona.

| | |
|---|---|
| Proveedor | Groq (API compatible con *chat completions* de OpenAI) |
| Modelo | `openai/gpt-oss-120b` (configurable con `mediconecta.ia.modelo`) |
| Acceso | Solo desde el servidor, a través del gateway `GestorOrientacion`. La clave nunca llega al navegador ni al repositorio |
| Controles | Instrucciones de sistema (no diagnosticar ni recetar, derivar emergencias al 107/911, no salirse de tema), validación de la entrada, límite de 10 consultas por minuto por usuario, la especialidad sugerida se descarta si no existe, timeout de 5 s con fallback por palabras clave |
| Datos enviados | Solo el texto que escribe la persona en el chat; no se envían nombre, documento ni historia clínica. La conversación no se guarda |
| Evaluación | `pruebas/ia/evaluar-ia.sh` (12 casos: orientación, emergencias, *prompt injection*, fuera de tema, no inventar especialidades) y tests unitarios |

## 2. IA como herramienta de desarrollo

El equipo usó **Claude Code** (Anthropic) como asistente de desarrollo, en conjunto con el
trabajo de los integrantes, a lo largo del proyecto.

**Para qué se usó:**

- Implementación de funcionalidades del backend y del frontend.
- Integraciones (SOAP, REST, mensajería JMS, asistente de IA) y su resiliencia (timeouts,
  fallbacks, reintentos, DLQ).
- Seguridad: autenticación, autorización, cabeceras y corrección de vulnerabilidades.
- Tests unitarios, pruebas de punta a punta, de carga y de seguridad.
- Infraestructura: migraciones, Docker, pipeline de CI y herramientas de calidad.
- Documentación técnica y de usuario.

**Cómo se usó:** el equipo definió qué había que hacer a partir de las consignas y las
correcciones de la cátedra. La herramienta propuso e implementó cambios, y el equipo los
revisó y verificó con los tests, el despliegue en WildFly y en Docker, y las pruebas de
punta a punta, carga y seguridad. Los resultados que figuran en el plan de pruebas salen de
ejecuciones reales.

## 3. Responsabilidad del equipo

- El equipo es responsable del código entregado, lo haya escrito una persona o una
  herramienta. Cada integrante revisa y entiende las partes generadas con asistencia de IA,
  porque la defensa es individual y puede preguntar por cualquier parte.
- Ninguna decisión de seguridad se aceptó sin una prueba que la verifique (ver la matriz de
  trazabilidad).
- No se le dieron a la herramienta datos personales reales: los datos del sistema son
  ficticios.

**Firmas:** *(nombre de cada integrante)*
