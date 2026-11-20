# Cambios en la presentación final

**Original:** `original/MediConecta_Presentacion_Final.pptx` (sin modificar; basado en `947ba9d`).
**Actualizada:** `MediConecta_Presentacion_Final_ACTUALIZADA.pptx` y `.pdf` (basada en `integracion-final`, código `eda4b02`, CI run #4).

**Alcance del cambio:** solo se reemplazaron textos y los valores del gráfico de la diapositiva 14. No se agregaron, quitaron ni reordenaron diapositivas; no cambiaron diseño, colores, tipografías, capturas, numeración ni responsables. La única variación de formato es una fuente 20 % más chica para el nombre `MonitorMensajesFallidos` (diap. 11), que no entraba en su caja.

> **Diapositivas 21 a 27:** el archivo recibido tiene **20 diapositivas**; las de respaldo 21–27 no existen. No se crearon (la consigna pedía no agregar diapositivas salvo imprescindible). El material de respaldo para preguntas está en `documentacion-final/`.

## Resumen

| Diap. | Responsable | Clasificación | ¿Cambió? |
|---|---|---|---|
| 1 Portada | Facundo Sánchez | CORRECTA | No |
| 2 Problema | Facundo Sánchez | CORRECTA | No |
| 3 Solución | Facundo Sánchez | CORRECTA | No |
| 4 Reserva | Facundo Sánchez | CORRECTA | No |
| 5 Arquitectura | Mateo Bellinzona | DESACTUALIZADA (detalle) | Sí, 1 texto |
| 6 Tecnologías | Mateo Bellinzona | DESACTUALIZADA | Sí, 2 textos |
| 7 Componentes | Mateo Bellinzona | INCORRECTA (tipos EJB) | Sí, 2 textos |
| 8 Patrones | Mateo Bellinzona | CORRECTA | No |
| 9 Funcionalidades | Facundo Morán | CORRECTA (capturas de la UI anterior) | No |
| 10 Reserva | Facundo Morán | CORRECTA | No |
| 11 Integraciones | Facundo Morán | INCORRECTA (3 vs 4 intentos, clase) | Sí, 4 textos |
| 12 Resiliencia | Facundo Morán | INCORRECTA (circuit breaker) | Sí |
| 13 Testing | Tobías Bonomo | INCORRECTA / DESACTUALIZADA | Sí |
| 14 Cobertura | Tobías Bonomo | DESACTUALIZADA (otro commit y alcance) | Sí, gráfico y textos |
| 15 CI | Tobías Bonomo | DESACTUALIZADA | Sí |
| 16 Seguridad | Tobías Bonomo | DESACTUALIZADA (fechas/fuente) | Sí, 2 textos |
| 17 IA | Laureano Gómez Moreno | CORRECTA | No |
| 18 Gateway | Laureano Gómez Moreno | INCORRECTA (clases y mediciones de 947ba9d) | Sí |
| 19 Resultados | Laureano Gómez Moreno | DESACTUALIZADA | Sí |
| 20 Cierre | Laureano Gómez Moreno | INCOMPLETA (gracias antes de la demo) | Sí, 1 texto |

**Sin cambios (8):** 1, 2, 3, 4, 8, 9, 10, 17. **Modificadas (12):** 5, 6, 7, 11, 12, 13, 14, 15, 16, 18, 19, 20.

## Detalle por diapositiva modificada

### 5 — Arquitectura
| Antes | Ahora | Por qué | Evidencia |
|---|---|---|---|
| Filtros: "CSP · ETag · caché" | "CSP · nosniff · no-store" | El filtro final (`CabecerasSeguridad`) pone CSP, nosniff y `Cache-Control: no-store`; el ETag lo calculan los recursos (`Etiquetas`), no un filtro. El `FiltroCacheApi` de Morán no quedó | `seguridad/CabecerasSeguridad.java`, `presentacion/Etiquetas.java` |

### 6 — Tecnologías
| Antes | Ahora | Por qué | Evidencia |
|---|---|---|---|
| PostgreSQL 16 · "JPA + Hibernate" | "JPA + Hibernate + Flyway" | El esquema ahora se versiona con Flyway | `db/migraciones/V1–V3`, `persistence.xml` (`validate`) |
| GitHub Actions · "CI + gitleaks" | "CI · Docker Compose · gitleaks" | El CI levanta todo con Docker Compose | `.github/workflows/ci.yml`, `compose.yaml` |

### 7 — Componentes
| Antes | Ahora | Por qué | Evidencia |
|---|---|---|---|
| "Siete componentes: uno stateful para la conversación y el resto stateless." | "Siete componentes: uno stateful, el resto stateless; los avisos los consumen MDB." | Los consumidores de C5 son `@MessageDriven`, no stateless | `ReceptorConfirmacion`, `SuscriptorAviso*`, `MonitorMensajesFallidos` |
| "C5 · JMS cola + tópico" | "C5 · @Stateless + MDB" | Ídem | `NotificadorTurnos` (`@Stateless`) |

### 11 — Integraciones
| Antes | Ahora | Por qué | Evidencia |
|---|---|---|---|
| "Probado en vivo el 19/11: respuestas y log reales del servidor." | "Verificado en CI con Docker Compose (20/11): humo de cobertura y mensajería en verde." | La prueba en vivo era de `947ba9d` | CI run #4, `evidencias/ci/humo-smoke-test.txt` |
| DLQ "tras 3 intentos fallidos" | "tras 4 intentos" | La versión final hace 1 entrega + 3 reintentos (1, 2, 4 s) | `scripts/wildfly/configurar-mensajeria.cli` (`max-delivery-attempts=4`) |
| "MonitorColaMuerta" | "MonitorMensajesFallidos" | La clase de Morán no está en la versión final | `mensajeria/MonitorMensajesFallidos.java` |
| Log "[JMS-COLA] confirmacion enviada -> Turno 4: Diego Sosa…" | "[JMS-COLA] confirmacion: turno <id> procesado (evento <id>)" | Es el formato del log del código final; no se inventó un turno concreto | `mensajeria/ConsumoDeEventos.java`, `ReceptorConfirmacion.java` |

### 12 — Resiliencia
| Antes | Ahora | Por qué | Evidencia |
|---|---|---|---|
| Subtítulo "…Medido en el servidor real." | "…Verificado con tests y en CI." | Las mediciones eran de `947ba9d` | `FallbacksTest`, `TiemposDeEsperaTest`, CI |
| Timeout "5,04 s · IA que tarda 8 s…" · "En vivo 19/11" | "5 s · Obra social o IA que no responde: se corta y responde el respaldo." · "Test + CI" | Valor configurado del código final; no hay medición en vivo de esta versión | `TiemposDeEspera` (2 s/5 s), `AdaptadorIaGroq` (2 s/5 s) |
| Tarjeta "Circuit breaker · 3 fallas · Abre el circuito 30 s" | "Salud del sistema · /api/salud · Informa si la base y el broker están arriba (503 si caen)." | **La versión final no tiene circuit breaker** (era `ProteccionIntegracion` de Morán). Se reemplazó por una capacidad real de resiliencia | `RecursoSalud`, `MonitorSalud`, humo SALUD |
| Fallback · "En vivo 19/11" | "Test + CI" | Ídem | `FallbacksTest` |
| DLQ "3 intentos · 3 reintentos (1 s)…" | "4 intentos · 3 reintentos (1, 2 y 4 s) y pasa a MensajesFallidos." | Configuración final | `configurar-mensajeria.cli` |
| Consumidores "Test + en vivo" | "Test" | No hay prueba en vivo de esta versión | `GestionNotificacionesTest` |
| Persistentes "Prueba 18/11" | "Prueba 19/11" | La prueba de reinicio de la base adoptada es la del 19/11 (Bellinzona) | `docs/Plan_de_Pruebas.md` §6 |

### 13 — Estrategia de pruebas
| Antes | Ahora | Por qué |
|---|---|---|
| "47 casos de ataque · escaneo activo" | "humo de seguridad + ZAP baseline" | `pruebas-seguridad.py` (47 casos) era de Morán; en CI corre ZAP **baseline** (pasivo) |
| "20 usuarios · 60 s" | "k6 · 20 usuarios · 45 s" | Escenario de `pruebas/carga/carga.js` |
| "smoke-test.ps1 · 119 verificaciones" | "smoke-test.sh · 148 verificaciones" | Humo final en CI |
| "203 tests" (2 veces) | "195 tests" | Suite final |
| "La API REST real, sin servidor · dispatcher en memoria de RESTEasy" | "El contrato de la API, en cada build · ContratoOpenApiTest…" | `ApiRestTest` de Morán no está en la versión final |
| "La IA nunca se llama de verdad · servidor HTTP falso…" | "IA simulada en tests, real en la evaluación · unitarias con proveedor simulado; 12 casos con Groq real (manual)." | Pedido explícito: distinguir simulado de real |
| "7 defectos…" | "10 defectos registrados (D-01 a D-10) · todos corregidos; 0 críticos abiertos" | `documentacion-final/Registro_de_Defectos.md` |

### 14 — Cobertura y mutation testing
| Antes | Ahora | Por qué |
|---|---|---|
| "Medido el 19/11 sobre el commit 947ba9d…" | "Medido el 20/11 sobre el commit eda4b02 (integración final)…; igual en CI." | Código final |
| Gráfico: Líneas total 84,3/80 · Ramas total 83,1/70 · Líneas negocio 94,3/80 · Ramas negocio 90,2/70 · Mutación total 76,7/60 · Mutación negocio 85,3/60 | Líneas negocio 95,0/80 · Ramas negocio 82,6/70 · Mutación dominio 85,4/60 · Líneas alcance pom 86,7/80 · Líneas proyecto 72,5 (sin umbral) · Ramas proyecto 69,2 (sin umbral) | Las cifras viejas eran de otra suite y otro alcance (PIT sobre todo el código). No se reemplazaron "automáticamente": cada barra dice qué alcance mide. La hoja Excel embebida también se actualizó |
| Nota "Capa de negocio = negocio + integraciones + mensajería + modelo.estado" | "Negocio = paquete negocio (JaCoCo); mutación = negocio + modelo.estado (PIT). Proyecto completo: sin umbral." | Alcances reales del `pom.xml` final |
| "203", "762 / 994 (77 %)", "119 / 119 en WildFly" | "195", "338 / 396 (85 %) · negocio + State", "148 / 148 de humo en Docker Compose (CI)" | `evidencias/ci/pit.txt`, `humo-smoke-test.txt` |

### 15 — Integración continua
| Antes | Ahora |
|---|---|
| "Cada push compila, testea, mide cobertura y mutación, y busca secretos…" | "Cada push: build, tests, cobertura, mutación, secretos y prueba completa en Docker Compose." |
| "mvnw verify · 203 tests · JaCoCo 80/70 · WAR · 35 s" | "mvnw verify · Checkstyle · 195 tests · JaCoCo 80/70 · WAR · 39 s" |
| "mínimo 60 % · 2 min 48 s" | "mínimo 60 % · 85 % · 47 s" |
| "todo el historial · en paralelo · 5 s" | "todo el historial · 0 hallazgos · 6 s" |
| "Run #1 · rama facu · commit 947ba9d · 4 jobs en 3 min 29 s" | "Run #4 · rama integracion-final · commit eda4b02 · 6 jobs (+ migraciones y Docker/humo/k6/ZAP) en 4 min 12 s" |
| "…integrar a main y crear el tag v1.0-final." | "…mergear el PR a facu y main, y crear el tag v1.0-final." |

Sonar sigue como "omitido: sin SONAR_TOKEN" (correcto: no se declara un Quality Gate aprobado).
**Pendiente:** la captura de GitHub Actions de esta diapositiva es la del run #1 (`947ba9d`); conviene reemplazarla por una captura real del run #4 (https://github.com/tobiasbonomo29/desarrollo-de-apps2/actions/runs/44658335940).

### 16 — Seguridad
| Antes | Ahora | Por qué |
|---|---|---|
| "Respuestas reales de la API (19/11)" | "Respuestas reales de la API (humo en CI, 20/11)" | Mismos códigos verificados por el humo de la versión final; el 403 de "turno ajeno" es correcto **gracias a la integración** (antes la base respondía 400) |
| "OWASP ZAP (18/11)" | "OWASP ZAP baseline (CI 20/11)" | Resultado del run #4: 0 FAIL, 3 advertencias informativas |

### 18 — Gateway de IA
| Antes | Ahora | Por qué |
|---|---|---|
| Subtítulo "…pasa por la misma protección que SOAP y REST." | "…timeout y respaldo, igual que SOAP y REST." | No hay `ProteccionIntegracion` |
| Caja "ProteccionIntegracion · timeout 5 s + circuit breaker" | "Timeouts · conexión 2 s · lectura 5 s" | `AdaptadorIaGroq` final |
| "OrientadorPorPalabrasClave · origen: REGLAS" / "…o el circuito está abierto" | "OrientacionDeRespaldo · contingencia: true" / "si falla, tarda o no hay clave" | Clases finales |
| Tabla "Medido en vivo el 19/11…" con tiempos 0,03/5,04/0,02 s de `947ba9d` | "Comportamiento del gateway verificado por tests y CI (20/11)"; columna Evidencia: FallbacksTest / Humo IA en CI; respuestas de respaldo genéricas | Las mediciones eran de otra implementación |
| "Lo que no verificamos: el proveedor real (Groq): no había clave…" | "La evaluación de 12 casos con Groq real (19/11, M. Bellinzona: 12/12 tras corregir un caso) no tiene salida guardada y no se repitió sobre la versión integrada." | Hay evaluación real documentada, pero sin evidencia versionada |

### 19 — Resultados
| Antes | Ahora |
|---|---|
| 203 tests · 19/11 | 195 tests · 20/11 |
| 84,3 % cobertura de líneas · 83,1 % ramas · 94,3 % en negocio | 95,0 % líneas en la capa de negocio · 82,6 % ramas · 72,5 % en todo el proyecto |
| 77 % mutation score · 85 % en la capa de negocio | 85 % mutation score · negocio + State (338 / 396) |
| 119 / 119 · smoke en WildFly 19/11 | 148 / 148 · humo en Docker Compose · CI 20/11 |
| 110,7 ms · 18/11, sin PostgreSQL | 22,2 ms · CI 20/11, con PostgreSQL |
| 0 High · web + API autenticada · 18/11 | 0 High · baseline en CI · 20/11 |

### 20 — Cierre
| Antes | Ahora | Por qué |
|---|---|---|
| "¡Gracias!  ·  Preguntas" (antes de la demo) | "Después de la demo: preguntas  ·  ¡Gracias!" | Cambio mínimo para que el cierre sea coherente con la demo |

## Afirmaciones que todavía requieren confirmación
- Diap. 9 y otras capturas: son de la interfaz del 17/09; la versión integrada cambió estilos.
- Diap. 12 "Mensajes persistentes · Prueba 19/11": prueba manual de Bellinzona, no repetida.
- Diap. 13/18: la evaluación con Groq real (12/12) no tiene salida versionada.
- Diap. 15: captura del run #1.
- Diap. 19: la carga es de 20 usuarios × 45 s, no los 50 × 5 min del criterio.
