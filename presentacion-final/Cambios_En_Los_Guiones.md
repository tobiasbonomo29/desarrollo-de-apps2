# Cambios en los guiones — qué hay que volver a estudiar

> No estaba disponible `Guion_Exposicion_Final.md` (lo citan las notas del PPTX). Esta lista se basa
> en lo que cambió en cada diapositiva: son las frases que ya no se pueden decir y su reemplazo.

## Facundo Sánchez (diap. 1–4)
**No necesita cambiar nada.** Las diapositivas 1 a 4 quedaron exactamente iguales y no contienen datos que la integración haya modificado.

## Mateo Bellinzona (diap. 5–8)
- Diap. 5: filtros → decir "CSP, nosniff y no-store" (no "ETag y caché").
- Diap. 6: agregar "esquema versionado con Flyway" y "el CI levanta todo con Docker Compose".
- Diap. 7: **dejar de decir "el resto son stateless" sin matiz** → "uno stateful, el resto stateless, y los avisos los consumen MDB (beans por mensajes)".
- Diap. 8: sin cambios.

## Facundo Morán (diap. 9–12)
- Diap. 9 y 10: sin cambios.
- Diap. 11: "**cuatro intentos** (entrega + 3 reintentos) y después la DLQ" (no "tres"); la clase es **MonitorMensajesFallidos** (no MonitorColaMuerta); "lo verifica el CI con Docker Compose" (no "probado en vivo el 19/11").
- Diap. 12: **no mencionar circuit breaker** (la versión final no lo tiene); en su lugar "/api/salud indica si la base y el broker están arriba (503 si se caen)". Timeout: "se corta a los 5 segundos" (no "5,04 s"). DLQ: "esperas de 1, 2 y 4 segundos". Si preguntan por circuit breaker: es un pendiente identificado.

## Tobías Bonomo (diap. 13–16)
- Diap. 13: **195 tests** (no 203); **148 verificaciones** con `smoke-test.sh` (no 119 con `.ps1`); carga **20 usuarios · 45 s**; seguridad = humo + **ZAP baseline** (no "47 casos" ni "escaneo activo"); contrato con `ContratoOpenApiTest` (no "dispatcher de RESTEasy"); **IA simulada en tests y real en 12 casos manuales** (no "la IA nunca se llama de verdad"); **10 defectos**.
- Diap. 14: **95,0 % líneas / 82,6 % ramas en negocio; 85 % de mutación (338/396) sobre negocio + State; 72,5 % en todo el proyecto, sin umbral**. Ya no decir 84,3 / 83,1 / 77 %. Commit **eda4b02**.
- Diap. 15: **seis jobs** en verde, run #4 de la rama `integracion-final`, 4 min 12 s; gitleaks **0 hallazgos**; falta mergear el PR a facu y main y crear el tag. Sonar sigue omitido (igual que antes).
- Diap. 16: decir que las respuestas las verifica **el humo en CI (20/11)** y que ZAP es **baseline**.

## Laureano Gómez Moreno (diap. 17–20)
- Diap. 17: sin cambios.
- Diap. 18: **no mencionar ProteccionIntegracion, circuit breaker ni OrientadorPorPalabrasClave**; decir "timeouts de 2 s de conexión y 5 s de lectura" y "OrientacionDeRespaldo con contingencia en true". La tabla ya no muestra tiempos medidos: muestra qué test verifica cada caso. Nuevo cierre: "la evaluación con Groq real dio 12/12 el 19/11, pero no guardamos la salida ni la repetimos sobre la versión integrada".
- Diap. 19: **195 tests; 95 % negocio (72,5 % total); 85 % mutación; 148/148; p95 22 ms con PostgreSQL; 0 High en ZAP baseline; todo del CI del 20/11**.
- Diap. 20: terminar con "**Después de la demo, preguntas. ¡Gracias!**".
