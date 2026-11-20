# Material histórico del commit 947ba9d (checkpoint 3, implementación de Facundo Morán)

Esta carpeta conserva la documentación, las evidencias y los scripts que acompañaban al
commit `947ba9d` ("checkpoint 3 - resiliencia, mensajeria confiable, contrato REST y
auditoria"), una implementación paralela a la de la rama de Mateo Bellinzona.

En la integración final (rama `integracion-final`) se adoptó como base funcional el código
de Bellinzona. El código de `947ba9d` **no forma parte del sistema final**, pero sigue
completo en el historial de Git (es uno de los padres del commit de integración):

```bash
git show 947ba9d --stat
git checkout 947ba9d -- <ruta>   # para recuperar un archivo puntual
```

Por eso, **las cifras y evidencias de esta carpeta corresponden a `947ba9d` y no al sistema
final**: 203 tests, 84,3 % / 83,1 % de cobertura total, 77 % de mutación, smoke 119/119,
p95 110,7 ms en plan B, circuit breaker de 3 fallas, DLQ tras 3 intentos, etc. Las
mediciones de la versión integrada están en `documentacion-final/`.

| Contenido | Qué era |
|---|---|
| `Checkpoint_3.md`, `Informe_Auditoria.md` | Estado y auditoría del checkpoint 3 sobre `947ba9d` |
| `plan-de-pruebas.md`, `matriz-trazabilidad.md`, `manual-usuario.md`, `declaracion-uso-ia.md` | Versiones de Morán, reemplazadas por las de `docs/` y `documentacion-final/` |
| `evidencias/` | Salidas reales de smoke test, carga, ZAP, recuperación del broker y gateway de IA sobre `947ba9d` (WildFly 36, plan B) |
| `scripts/` | `prueba-carga.py`, `pruebas-seguridad.py`, `proveedor-ia-simulado.py`, `zap-api.yaml`: herramientas de esa versión; no se reverificaron contra la versión final |

Dos correcciones de `947ba9d` sí se incorporaron al código final (ver
`documentacion-final/Informe_Integracion_y_Conflictos.md`):

1. Acceso a recursos ajenos (BOLA/IDOR) responde **403** (`AccesoDenegadoException`) en lugar de 400.
2. El documento del paciente se valida como número de 6 a 10 dígitos.
