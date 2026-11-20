# MediConecta — Informe de seguridad y calidad

**Fecha:** 20/11/2026 · **Código:** `eda4b02` · **CI:** run #4 (https://github.com/tobiasbonomo29/desarrollo-de-apps2/actions/runs/44658335940)

## 1. Matriz SEC (eje D)

| Criterio | Estado | Evidencia | Tratamiento |
|---|---|---|---|
| BOLA/IDOR → 403/404 | CUMPLE | Humo "seguridad en vivo" (403); `GestionTurnosTest`, `GestionPacientesTest` | Corregido en la integración (antes 400, D-07) |
| BFLA → 403 | CUMPLE | `@RolesAllowed` en cada EJB; humo SEGURIDAD | — |
| JWT vencido, alterado, `alg=none`, otro emisor → 401 | CUMPLE | `ServicioJwtTest` (10), `MecanismoAutenticacionJwtTest`, humo | — |
| Mass assignment | PARCIAL | DTO `PedidoTurno`; el negocio fija id, estado y archivado | Falta test dedicado |
| Inyección SQL / XSS | CUMPLE | JPQL parametrizado; humo (SQL en búsqueda, ruta y login; XSS almacenado devuelto como JSON); `esc()` en la interfaz | — |
| XXE en SOAP | NO VERIFICABLE | Sin prueba que envíe entidades externas | Agregar prueba con DOCTYPE |
| Fuerza bruta → 429 | CUMPLE | 5 fallos/usuario o 30/origen en 5 min; `ServicioAutenticacionTest`, `LimitadorDeFrecuenciaTest`, humo | — |
| ZAP baseline + activo autenticado | PARCIAL | Baseline pasivo en CI: 65 PASS, 0 FAIL, 3 WARN (10027 comentarios sospechosos ×3, 10049 contenido no almacenable ×6, 10109 aplicación web moderna ×1; todas informativas). `evidencias/ci/zap-baseline.txt` | Falta escaneo activo autenticado |
| Dependencias (SCA) | NO EJECUTADO | — | Agregar OWASP Dependency-Check o Trivy al CI |
| Cabeceras, CORS, cookies | CUMPLE | CSP (pantallas sin `unsafe-inline`), nosniff, `X-Frame-Options: DENY`, HSTS bajo HTTPS, `no-store` en la API; sin CORS; `JSESSIONID` HttpOnly + SameSite=Strict; `CabecerasSeguridadTest` | Swagger UI usa CSP propia más permisiva |
| Logs y errores | CUMPLE | Problem Details sin trazas; `ProblemasTest` | — |
| TLS y hash adaptativo | PARCIAL | PBKDF2-HMAC-SHA256, 120 000 iteraciones (`HashDeClavesTest`) | TLS depende del despliegue |

## 2. Secretos

- **gitleaks 8.21.2** sobre todo el historial en CI: 36 commits, **0 hallazgos** (`evidencias/ci/gitleaks.txt`).
- **Riesgo residual (no reportado por gitleaks):** desde `c285ce8` (12/11) hasta antes de `62f2abf`/`947ba9d`, `ServicioJwt.java` contenía un secreto JWT de desarrollo literal. El código actual no tiene valor por omisión y exige `MEDICONECTA_JWT_SECRETO`.
  - Riesgo: un escáner distinto (trufflehog) podría marcarlo en vivo (G6); cualquier despliegue que hubiera usado ese valor permitiría forjar tokens.
  - Procedimiento: no reescribir el historial sin acuerdo del equipo; verificar que ningún entorno use ese valor (rotar el secreto en todos los despliegues); documentarlo ante la cátedra. Si el equipo decide limpiarlo: `git filter-repo --replace-text` sobre una copia, *force push* coordinado de todas las ramas y re-clonado de todos los integrantes (operación destructiva).
- No se publicaron secretos nuevos: `.env` no se versiona; los logs de CI guardados en `evidencias/` no contienen tokens.

## 3. Calidad de código

| Control | Resultado |
|---|---|
| Checkstyle (`config/checkstyle/checkstyle.xml`) | 0 violaciones; corta el build |
| Enforcer | Maven ≥ 3.9, JDK ≥ 17, plugins con versión fija |
| JaCoCo | Negocio 95,0 % líneas / 82,6 % ramas; alcance global del pom 86,7 %; proyecto completo 72,5 % / 69,2 % |
| PIT | 85 % (338/396) sobre `negocio` y `modelo.estado`; test strength 88 % |
| SonarCloud Quality Gate | **No ejecutado** (falta `SONAR_TOKEN`, `SONAR_ORGANIZACION`, `SONAR_PROYECTO`) |
| Tests inestables | No medido (falta correr la suite 3 veces seguidas) |
