# MediConecta — Documentación final del TPO

**Rama:** `integracion-final` · **Commit de referencia del código:** `eda4b02` ·
**CI:** run #4, 6 jobs en verde — https://github.com/tobiasbonomo29/desarrollo-de-apps2/actions/runs/44658335940

## Contenido

| Archivo | Estado |
|---|---|
| `MediConecta_Informe_Final_TPO.pdf` | Completo (generado desde `fuentes/informe.html` con Edge headless) |
| `MediConecta_Informe_Final_TPO.docx` | **Pendiente**: la automatización de Word se colgaba y no hay pandoc/python-docx en la máquina. Para obtenerlo: abrir `fuentes/informe.html` en Word y *Guardar como .docx* |
| `Informe_Integracion_y_Conflictos.md` | Completo |
| `Plan_de_Pruebas_Final.md` | Completo (reemplaza `docs/Plan_de_Pruebas.md` v1) |
| `Matriz_de_Trazabilidad_Final.md` | Completo, salvo el mapeo a HU originales (no están en el repo) |
| `Registro_de_Defectos.md` | Completo (faltan Id de Jira) |
| `Informe_Seguridad_y_Calidad.md` | Completo |
| `Manual_Tecnico_y_Despliegue.md` | Completo |
| `Declaracion_Uso_IA_Final.md` | Borrador pendiente de validación y firma |
| `Checklist_Requisitos_Finales.md` | Completo |
| `evidencias/ci/` | Resúmenes del run #4: calidad, PIT, gitleaks, migraciones, humo 148/148, k6, ZAP, Sonar (omitido), `jobs.json` |
| `evidencias/local/` | `jacoco.csv`, cobertura por paquete, resumen de PIT y surefire (build local, JDK 21) |
| `fuentes/` | HTML del informe, figuras (`figuras/*.html` → `img/*.png` con `render-figuras.ps1`), `generar-pdf.js`, capturas |

Manual de usuario vigente: `docs/Manual_de_Usuario.md` (sin cambios funcionales que lo invaliden).
Material histórico de `947ba9d`: `docs/historico/entrega3-947ba9d/`.

## Resultados verificados

| Medición | Valor | Dónde |
|---|---|---|
| Tests JUnit | 195 / 195 | local y CI |
| Cobertura capa de negocio | 95,0 % líneas (720/758) · 82,6 % ramas (276/334) | JaCoCo |
| Cobertura alcance de la regla global del pom | 86,7 % líneas · 79,0 % ramas | JaCoCo |
| Cobertura proyecto completo | 72,5 % líneas (1766/2436) · 69,2 % ramas (556/804) | JaCoCo |
| Mutation score (negocio + modelo.estado) | 85 % (338/396), test strength 88 % | PIT, local y CI |
| Humo en Docker Compose | 148 / 148 | CI |
| Carga k6 | 20 VUs, 45 s, 9467 pedidos, p95 22,18 ms, 0 % errores | CI |
| OWASP ZAP baseline | 65 PASS, 0 FAIL, 3 WARN informativas | CI |
| gitleaks | 0 hallazgos en 36 commits | CI |
| Migraciones Flyway | V1–V3 aplicadas; segunda corrida sin cambios | CI |
| Sonar Quality Gate | No ejecutado (falta token) | CI |
| Pipeline | 4 min 12 s | CI |

## Regenerar

```powershell
node fuentes/figuras/generar-figuras.js; powershell -File fuentes/figuras/render-figuras.ps1
node fuentes/generar-pdf.js
& "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --headless=new --no-pdf-header-footer --print-to-pdf="<ruta>\MediConecta_Informe_Final_TPO.pdf" "file:///<ruta>/fuentes/informe-pdf.html"
```
