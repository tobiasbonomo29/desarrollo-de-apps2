# MediConecta — Manual técnico y de despliegue

**Código:** rama `integracion-final`, commit `eda4b02`. Complementa el `README.md` (referencia completa de la API) y `docs/arquitectura.md`.

## 1. Requisitos

| Para | Necesita |
|---|---|
| Compilar y testear | JDK 17 o superior (verificado con 17 en CI y 21 local). Maven lo descarga `./mvnw` (3.9.9) |
| Levantar todo | Docker con Docker Compose |
| Despliegue manual | WildFly con `standalone-full.xml`, PostgreSQL 16 |

## 2. Opción A — Docker Compose (un solo comando)

```bash
cp .env.example .env      # completar MEDICONECTA_DB_CLAVE, MEDICONECTA_JWT_SECRETO (≥ 32 caracteres),
                          # MEDICONECTA_DEMO_CLAVE (≥ 8; vacía = sin usuarios demo), MEDICONECTA_IA_CLAVE (opcional)
docker compose up -d --build
docker compose ps         # "app" pasa a healthy cuando /api/salud responde
```

Servicios: `db` (PostgreSQL 16), `migraciones` (Flyway 11, `db/migraciones`), `app` (WildFly con el WAR, configurado por `docker/configurar-wildfly-docker.cli`).
Aplicación: `http://localhost:8080/mediconecta/` · API: `/mediconecta/api` · documentación: `/mediconecta/api-docs.html`.
Apagar: `docker compose down` (conserva datos) o `docker compose down -v` (borra la base).
Verificado en GitHub Actions (run #4): build desde cero, `/api/salud` UP y humo 148/148.

## 3. Opción B — WildFly local

1. `./mvnw clean verify` → `target/mediconecta.war`.
2. PostgreSQL 16 con base `mediconecta` (`scripts/db/levantar-postgres.ps1` o `scripts/db/crear-base.sql`).
3. WildFly: `bin/standalone.bat -c standalone-full.xml`.
4. `scripts/wildfly/configurar-wildfly.ps1 -WildFly <ruta>`: driver, datasource `java:/jdbc/MediConectaDS`, migraciones Flyway, mensajería (4 intentos, DLQ), límites, endurecimiento, propiedades (`mediconecta.jwt.secreto`, `mediconecta.demo.clave`) y seguridad JWT.
5. Copiar el WAR a `standalone/deployments/`.

## 4. Configuración

| Propiedad / variable | Uso | Por omisión |
|---|---|---|
| `MEDICONECTA_JWT_SECRETO` / `mediconecta.jwt.secreto` | Firma de JWT | **ninguno** (obligatorio) |
| `MEDICONECTA_DEMO_CLAVE` / `mediconecta.demo.clave` | Contraseña de usuarios demo | sin usuarios demo |
| `MEDICONECTA_IA_CLAVE`, `mediconecta.ia.url`, `mediconecta.ia.modelo` | Proveedor de IA | sin clave → respaldo por palabras clave; modelo `openai/gpt-oss-120b` |
| `mediconecta.integraciones.timeout.conexion.ms` / `.respuesta.ms` | Timeouts SOAP/REST | 2000 / 5000 |
| `mediconecta.simulacion.demoraMs` | Demora simulada de las obras sociales (demo de timeout) | 0 |

## 5. Operación

- Salud: `GET /api/salud` (readiness: base y broker; 503 si caen) y `/api/salud/vivo`.
- DLQ: `GET /api/mensajeria/mensajes-fallidos` (ADMIN); prueba: `POST /api/mensajeria/mensaje-defectuoso`.
- Logs: `docker compose logs -f app` o `standalone/log/server.log`.

## 6. Pruebas

| Comando | Qué ejecuta |
|---|---|
| `./mvnw verify` | Checkstyle, 195 tests, JaCoCo con umbrales, WAR |
| `./mvnw test-compile org.pitest:pitest-maven:mutationCoverage` | PIT (≥ 60 %) |
| `scripts/smoke-test.sh http://localhost:8080/mediconecta` | Humo de sistema (148 verificaciones) |
| `docker run --rm -i --network host -e BASE_URL=… -e CLAVE=… grafana/k6:0.54.0 run - < pruebas/carga/carga.js` | Carga |
| `pruebas/ia/evaluar-ia.sh <url>` | Evaluación de IA con el proveedor real (requiere clave en el servidor) |

## 7. Problemas conocidos

- Al resolver merges en Windows, verificar que `mvnw`, `scripts/smoke-test.sh` y `pruebas/ia/evaluar-ia.sh` conserven el modo ejecutable (`git ls-files -s`).
- `scripts/smoke-test.ps1` no se ejecutó tras los últimos cambios; la versión bash es la de referencia.
