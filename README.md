# MediConecta

**Trabajo Práctico Integrador — Desarrollo de Aplicaciones II — UADE**
**Entrega Obligatoria N.º 2 (2.º parcial)** · con las mejoras pedidas en la corrección del 13/11

MediConecta es un sistema de gestión de turnos médicos construido sobre **Jakarta EE 10**
y desplegado en **WildFly**. Tiene componentes de negocio *stateless* y *stateful* en una
arquitectura estricta de tres capas, los patrones DAO, Facade, Factory, Adapter y **State**,
autenticación con **JWT** y autorización por rol, mensajería **JMS** (cola y tópico),
integración **SOAP + REST**, persistencia con **JPA + PostgreSQL**, tests unitarios y una
interfaz web que sigue el prototipo de Figma.

Después de la corrección de la 2.ª entrega se sumó lo que faltaba para un sistema
operable: mensajes JMS en **JSON versionado** con **consumidores idempotentes**,
**reintentos con backoff y DLQ**; errores en **Problem Details (RFC 9457)**, contrato
**OpenAPI 3.1**, **`Idempotency-Key`** y **timeouts** en todas las integraciones;
**cabeceras de seguridad** y **ningún secreto por defecto**; **Docker Compose**, **CI en
GitHub Actions**, **health check** real y **migraciones Flyway**; **cobertura con JaCoCo**
(mínimo 80 %) y **linter Checkstyle**. El detalle punto por punto está en
[docs/Mejoras_Correccion_Entrega_2.md](docs/Mejoras_Correccion_Entrega_2.md).

| | |
|---|---|
| **Aplicación** | <http://localhost:8080/mediconecta/> |
| **Health check** | <http://localhost:8080/mediconecta/api/salud> |
| **API (OpenAPI 3.1)** | <http://localhost:8080/mediconecta/api-docs.html> · contrato en [`openapi.json`](src/main/webapp/openapi.json) |
| **Mejoras tras la corrección** | [docs/Mejoras_Correccion_Entrega_2.md](docs/Mejoras_Correccion_Entrega_2.md) |
| **Pruebas** | [Plan de pruebas](docs/Plan_de_Pruebas.md) · [Matriz de trazabilidad](docs/Matriz_de_Trazabilidad.md) · [Informe de estado](docs/Informe_de_Estado.md) |
| **Usuarios finales** | [Manual de usuario](docs/Manual_de_Usuario.md) |
| **Uso de IA** | [Declaración de uso de IA](docs/Declaracion_Uso_IA.md) |
| **Documento de la entrega** | [docs/Entrega_Obligatoria_2.md](docs/Entrega_Obligatoria_2.md) (cierre del 13/11 en §9) · [Entrega 1](docs/Entrega_Obligatoria_1.md) |
| **Arquitectura y patrones** | [docs/arquitectura.md](docs/arquitectura.md) |
| **Guion de la defensa** | [docs/guion-defensa.md](docs/guion-defensa.md) |
| **Preguntas probables** | [docs/preguntas-defensa.md](docs/preguntas-defensa.md) |

---

## 1. Qué hace la aplicación

- **Iniciar sesión** con un **JWT** firmado que lleva los roles (PACIENTE, RECEPCION,
  MEDICO, ADMIN); la autorización la aplica el contenedor con `@RolesAllowed`.
- **Tablero** con turnos del día, pacientes activos, profesionales y turnos pendientes,
  todo calculado con datos reales de la base.
- **Turnos**: listar con buscador y filtros, ver el detalle, agendar, **cancelar**,
  **marcar atendido** y **archivar** (sacar de la agenda un turno cancelado sin borrar su
  historial). Las transiciones de estado siguen el patrón **State**: una inválida responde
  **409**.
- **Mis turnos**: un PACIENTE consulta y cancela solo sus propios turnos.
- **Pacientes**: padrón con buscador, alta, edición de ficha e historial de turnos.
- **Profesionales**: listado por especialidad, alta (solo ADMIN) y gestión de la
  disponibilidad semanal — RECEPCION y ADMIN sobre cualquier profesional, un MEDICO solo
  sobre la suya propia.
- **Reserva guiada en pasos**, cuyo estado intermedio vive en un EJB `@Stateful`
  del servidor y no en el navegador. En el primer paso, la opción **Prefiero escribirlo**
  abre un chat: la persona cuenta qué le pasa y un asistente de IA le sugiere la
  especialidad, con un botón para elegirla (ver [Asistente de IA](#asistente-de-ia-opcional)).
- Interfaz **responsive**: barra lateral en escritorio, navegación inferior en teléfono.

### Componentes implementados

| # | Componente | Tipo | Qué resuelve |
|---|---|---|---|
| C1 | `GestionTurnos` | EJB `@Stateless` | Reglas de la agenda: alta, consulta y cancelación de turnos |
| C2 | `GestionPacientes` | EJB `@Stateless` | Padrón de pacientes e historial de atenciones |
| C3 | `GestionProfesionales` | EJB `@Stateless` | Profesionales, especialidades, disponibilidad y horarios libres |
| C4 | `AsistenteReservaTurno` | EJB **`@Stateful`** | Conversación de reserva en cuatro pasos |

En la Entrega 2 se sumaron C5 (Notificaciones por **JMS**: cola + tópico) y C6
(Integración con obras sociales: **SOAP** legado + **REST** partner, patrón Adapter). El
detalle está en [docs/Entrega_Obligatoria_2.md](docs/Entrega_Obligatoria_2.md).

---

## 2. Requisitos

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | 17 o superior | El proyecto compila con `--release 17`. |
| Apache Maven | 3.9.x | `winget install Apache.Maven` si no está en el PATH. |
| WildFly | 36.0.1.Final o superior | Versión de referencia del proyecto. Ver la nota de compatibilidad más abajo. |
| PostgreSQL | 14 o superior | Puede ser una instalación local o el contenedor Docker que trae el proyecto. |

> **Nota de compatibilidad — importante y honesta.**
> El proyecto está escrito contra **Jakarta EE 10** (`jakarta.jakartaee-api:10.0.0`, scope
> `provided`), que es la plataforma de WildFly 36.0.1.Final. La Entrega 2 completa (JWT,
> patrón State, JMS, SOAP + REST) fue **verificada de punta a punta sobre WildFly
> 36.0.1.Final (`standalone-full.xml`) con JDK 24 y PostgreSQL 16**. La Entrega 1 se había
> verificado además sobre WildFly 41.0.1.Final (Jakarta EE 11, compatible hacia atrás); la
> Entrega 2 no se volvió a probar sobre la 41.

---

## 3. Puesta en marcha (copiar y pegar)

### Opción rápida — todo con Docker Compose

Con Docker instalado no hace falta nada más (ni JDK, ni Maven, ni WildFly):

```bash
cp .env.example .env        # completar las tres claves (openssl rand -base64 48)
docker compose up -d --build
docker compose ps            # "app" pasa a "healthy" cuando /api/salud responde
```

Levanta tres servicios en orden: **db** (PostgreSQL 16 con volumen persistente),
**migraciones** (Flyway aplica `db/migraciones` y termina) y **app** (WildFly 36 con el
WAR, arranca recién cuando las migraciones terminaron bien). La aplicación queda en
<http://localhost:8080/mediconecta/>. Los secretos salen de `.env`, que no se versiona: si
falta alguno, compose se niega a arrancar y dice cuál.

```bash
docker compose logs -f app   # log de WildFly
docker compose down          # apagar (los datos quedan en el volumen)
docker compose down -v       # apagar y borrar la base
```

La imagen (`Dockerfile`) se construye en dos etapas: Maven compila el WAR (con el linter)
y la imagen oficial de WildFly se configura con el CLI en modo embebido usando los
**mismos scripts** que la instalación local (`scripts/wildfly/configurar-mensajeria.cli` y
`configurar-limites.cli`). Tiene `HEALTHCHECK` contra `/api/salud`.

Los pasos 1 a 5 de abajo son la instalación manual sobre un WildFly local.

### Paso 1 — PostgreSQL

**Opción A — Docker (la más rápida):**

```powershell
.\scripts\db\levantar-postgres.ps1
```

Crea un contenedor `mediconecta-postgres` con la base `mediconecta`, el usuario
`mediconecta` y la contraseña `mediconecta`, escuchando en el puerto 5432. Volver a
ejecutarlo arranca el contenedor existente sin perder datos.

Equivalente a mano:

```bash
docker run -d --name mediconecta-postgres -e POSTGRES_DB=mediconecta -e POSTGRES_USER=mediconecta -e POSTGRES_PASSWORD=mediconecta -p 5432:5432 postgres:16-alpine
```

**Opción B — PostgreSQL instalado en la máquina:**

```powershell
psql -U postgres -f .\scripts\db\crear-base.sql
```

En los dos casos, las tablas las crean las **migraciones versionadas** de
[`db/migraciones`](db/migraciones) (Flyway). El paso 2 las aplica solo; a mano:

```powershell
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://127.0.0.1:5432/mediconecta -Dflyway.password=mediconecta
mvn flyway:info    -Dflyway.url=jdbc:postgresql://127.0.0.1:5432/mediconecta -Dflyway.password=mediconecta
```

Hibernate ya no toca el esquema: al desplegar solo lo **valida**
(`hbm2ddl.auto=validate`). Si falta una migración, el despliegue falla con un mensaje claro
("Schema-validation: missing table ...") en lugar de romper en la primera consulta. Una base
que ya tenía las tablas de las Entregas 1 y 2 (creadas por Hibernate) se toma como versión 1
y solo recibe las migraciones nuevas (`baselineOnMigrate`).

### Paso 2 — Configurar WildFly

Un solo script deja el servidor listo: instala el driver JDBC como módulo, crea el
datasource, aplica las migraciones, configura reintentos/DLQ de JMS y los timeouts del
servidor, guarda los secretos (JWT y contraseña demo) como propiedades del sistema de
WildFly y prepara la seguridad JWT (`integrated-jaspi=false`).

```powershell
.\scripts\wildfly\configurar-wildfly.ps1 -WildFly "C:\wildfly-36.0.1.Final"
```

El script tiene dos momentos:

1. **Con WildFly apagado** descarga el driver de PostgreSQL y lo instala como módulo
   `org.postgresql`.
2. **Con WildFly encendido** crea el datasource `java:/jdbc/MediConectaDS`, corre
   `mvn flyway:migrate`, aplica `configurar-mensajeria.cli`, `configurar-limites.cli`,
   `configurar-propiedades.cli` y `configurar-seguridad.cli`. Si el servidor todavía no
   está arriba, levantarlo (`bin\standalone.bat -c standalone-full.xml`) y volver a correr
   el script con `-SoloDatasource`.

Parámetros útiles: `-ClaveDemo` (contraseña de los usuarios de demostración, por omisión
`Demo.2026`), `-SecretoJwt` (si no se pasa se genera uno aleatorio) y `-SinMigraciones`.

Si PostgreSQL no está en `localhost:5432`, pasar la URL: `-UrlBase
"jdbc:postgresql://127.0.0.1:5433/mediconecta"`.

> **Mensajería asincrónica (Entrega 2).** A partir de la segunda entrega el WAR declara
> dos destinos JMS (`DestinosJms`, cola y tópico) que el contenedor crea solo al
> desplegar, pero **solo si el subsistema de mensajería está activo**. El perfil
> `standalone.xml` (el que se usó hasta la Entrega 1) **no lo trae**: hay que levantar
> WildFly con `bin\standalone.bat -c standalone-full.xml` (o
> `standalone-full-ha.xml`). Si se arranca con el perfil de siempre, el WAR no despliega:
> el log muestra que no encuentra el subsistema `messaging-activemq`. El detalle de la
> mensajería y de las integraciones SOAP/REST nuevas está en
> [`docs/Entrega_Obligatoria_2.md`](docs/Entrega_Obligatoria_2.md).

Si se prefiere hacerlo a mano, los pasos exactos son:

```powershell
# 1) Módulo del driver (servidor apagado)
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --command="module add --name=org.postgresql --resources=C:\ruta\postgresql-42.7.4.jar --dependencies=jakarta.transaction.api,java.sql,java.logging"

# 2) Datasource (servidor encendido)
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --connect --resolve-parameter-values --file=scripts\wildfly\configurar-datasource.cli

# 3) Migraciones
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://127.0.0.1:5432/mediconecta -Dflyway.password=mediconecta

# 4) Reintentos/DLQ de JMS y timeouts del servidor (servidor encendido)
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --connect --file=scripts\wildfly\configurar-mensajeria.cli
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --connect --file=scripts\wildfly\configurar-limites.cli

# 5) Secretos de la aplicacion (servidor encendido)
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --connect --resolve-parameter-values -Dmediconecta.cli.jwt=<al menos 32 caracteres> -Dmediconecta.cli.demo=Demo.2026 --file=scripts\wildfly\configurar-propiedades.cli

# 6) Seguridad JWT (servidor encendido)
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --connect --file=scripts\wildfly\configurar-seguridad.cli
```

`configurar-datasource.cli` toma la URL, el usuario y la contraseña de PostgreSQL como
propiedades del sistema (`-Dmediconecta.db.url=... -Dmediconecta.db.usuario=...
-Dmediconecta.db.clave=...`), **sin valores por omisión**. `--resolve-parameter-values` es
necesario para que esos `-D` se apliquen.

**No hay ningún secreto por defecto en el código.** El secreto de los JWT
(`mediconecta.jwt.secreto` / `MEDICONECTA_JWT_SECRETO`, al menos 32 caracteres) y la
contraseña de los usuarios de demostración (`mediconecta.demo.clave` /
`MEDICONECTA_DEMO_CLAVE`) se configuran en el servidor. Si falta el secreto JWT, la
aplicación genera uno aleatorio al arrancar (los tokens dejan de valer al reiniciar); si
falta la contraseña demo, **no crea ningún usuario**.

#### Asistente de IA (opcional)

El chat **Prefiero escribirlo** del primer paso de la reserva usa un modelo de lenguaje a
través de la API de [Groq](https://console.groq.com/keys). La clave **nunca** va en el
código ni en el navegador: la lee el servidor de la variable de entorno
`MEDICONECTA_IA_CLAVE` o de la propiedad del sistema `mediconecta.ia.clave`.

```bash
# Linux / macOS: agregar al final de <wildfly>/bin/standalone.conf y reiniciar WildFly
export MEDICONECTA_IA_CLAVE="gsk_..."
```

```powershell
# Windows: agregar al final de <wildfly>\bin\standalone.conf.bat y reiniciar WildFly
set "MEDICONECTA_IA_CLAVE=gsk_..."
```

Opcionales: `mediconecta.ia.modelo` (por omisión `openai/gpt-oss-120b`) y
`mediconecta.ia.url` (por omisión la API de Groq; cualquier API compatible con el formato
*chat completions* de OpenAI sirve). Sin clave, el resto de la aplicación funciona igual y
el chat responde que el asistente no está configurado.

### Paso 3 — Compilar

```powershell
mvn clean package
```

Corre los tests unitarios y genera `target\mediconecta.war`. El nombre del WAR (`<finalName>mediconecta</finalName>`)
es el que fija el context root, es decir la primera parte de todas las URLs.

### Paso 4 — Desplegar

Copiar el WAR a la carpeta de despliegues:

```powershell
copy target\mediconecta.war C:\wildfly-36.0.1.Final\standalone\deployments\
```

En el log tiene que aparecer:

```
WFLYSRV0010: Deployed "mediconecta.war"
```

y, la primera vez, la línea del juego de datos de ejemplo:

```
[DEMO] Juego de datos de ejemplo cargado: 4 profesionales, 4 pacientes, 3 turnos.
```

La carga de ejemplo es **idempotente**: si la base ya tiene datos no toca nada, así que
redesplegar no duplica registros.

Alternativa: consola de administración en <http://localhost:9990> →
**Deployments → Add → Upload Deployment**.

### Paso 5 — Entrar

Abrir <http://localhost:8080/mediconecta/> e iniciar sesión.

---

## 4. Usuarios de demostración

Son **usuarios ficticios**, creados únicamente para la defensa. Viven en la tabla
`usuarios` (contraseña guardada como hash PBKDF2, nunca en claro) y los crea la aplicación
la primera vez que despliega, **solo si se configuró su contraseña** (`MEDICONECTA_DEMO_CLAVE`
en `.env`, o `-ClaveDemo` de `configurar-wildfly.ps1`). En la defensa se usa `Demo.2026`.

| Usuario | Contraseña | Rol | Qué puede hacer |
|---|---|---|---|
| `paciente` | `Demo.2026` | PACIENTE | Ver su ficha y sus turnos, reservar con el asistente guiado |
| `recepcion` | `Demo.2026` | RECEPCION | Agenda completa, padrón de pacientes, agendar y cancelar turnos |
| `medico` | `Demo.2026` | MEDICO | Agenda completa, cancelar turnos, definir disponibilidad |
| `admin` | `Demo.2026` | ADMIN | Todo lo anterior más el alta de profesionales |

El usuario `paciente` está asociado a la ficha de **Ana Gomez (DNI 30111222)** por el campo
`usuario` de la tabla `pacientes`. Esa asociación es la que permite que, además de la
autorización declarativa, el sistema verifique que un paciente solo consulte **su propio**
historial.

---

## 5. API REST

Context root `/mediconecta`, prefijo `/api`. Todos los endpoints exigen un JWT en la
cabecera `Authorization: Bearer <token>` **salvo `/api/salud`, `/api/salud/vivo` y
`/api/auth/login`**.

El contrato completo está en **OpenAPI 3.1**: [`openapi.json`](src/main/webapp/openapi.json),
navegable en <http://localhost:8080/mediconecta/api-docs.html> (Swagger UI; si hay una
sesión abierta en la pestaña, "Try it out" usa ese token). Un test unitario
(`ContratoOpenApiTest`) compara el contrato con los recursos JAX-RS reales: si se agrega un
endpoint sin documentarlo, el build falla.

### Autenticación

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| POST | `/api/auth/login` | público | `{"token":"eyJ...","tipo":"Bearer","expiraEnSegundos":7200,"usuario":"recepcion","roles":["RECEPCION"]}`, o `401` |

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/mediconecta/api/auth/login \
  -H "Content-Type: application/json" -d '{"usuario":"recepcion","clave":"Demo.2026"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/mediconecta/api/turnos
```

### Salud

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/salud` | público | **Readiness**: verifica PostgreSQL y el broker JMS. `200` `{"estado":"UP","componentes":{"baseDeDatos":"UP","mensajeria":"UP"}}`, o `503` con `"DOWN"` y el componente caído. Lo usan el `HEALTHCHECK` de Docker y el CI |
| GET | `/api/salud/vivo` | público | **Liveness**: `200` `{"estado":"UP"}` si el WAR atiende, sin tocar dependencias |

### Sesión

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/sesion` | todos | `{"autenticado":true,"usuario":"recepcion","roles":["RECEPCION"]}` |
| POST | `/api/sesion/logout` | todos | `204`; libera la sesión HTTP del asistente. El token lo descarta el cliente |

### Turnos

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/turnos` | RECEPCION, MEDICO, ADMIN | Agenda operativa (sin archivados) ordenada por fecha |
| GET | `/api/turnos/{id}` | todos | El turno con su **`ETag`**, o `404` (funciona aunque esté archivado). Un PACIENTE solo puede ver los suyos (`403`). Con `If-None-Match` igual → `304` |
| POST | `/api/turnos` | PACIENTE, RECEPCION, ADMIN | `201` + `Location`, o `400` con la regla incumplida. Un PACIENTE solo puede reservar para sí mismo. Acepta **`Idempotency-Key`** (ver abajo) |
| GET | `/api/turnos/{id}/notificaciones` | todos | Avisos que generaron los consumidores JMS (confirmación, recordatorio, aviso al profesional). Un PACIENTE solo los de sus turnos |

Los `PUT .../cancelar`, `.../atender`, `.../archivar` y `PUT /api/pacientes/{documento}`
aceptan **`If-Match`** con el ETag: si otro usuario modificó el recurso en el medio
responden `412` (`precondicion-fallida`) y no pisan el cambio. Sin la cabecera funcionan
como antes. `GET /api/pacientes/{documento}` también devuelve ETag (y un PACIENTE solo
puede leer su propia ficha).
| PUT | `/api/turnos/{id}/cancelar` | PACIENTE, RECEPCION, MEDICO, ADMIN | El turno en estado `CANCELADO`. Ownership: un PACIENTE solo cancela los propios, un MEDICO solo los de su agenda (`403` si no corresponde). **`409`** si ya estaba cancelado o atendido |
| PUT | `/api/turnos/{id}/atender` | **MEDICO, ADMIN** | El turno en estado `ATENDIDO`. Un MEDICO solo sobre su agenda (`403`). **`409`** si no está `PROGRAMADO` |
| PUT | `/api/turnos/{id}/archivar` | **RECEPCION, ADMIN** | Saca de la agenda un turno ya `CANCELADO` sin borrarlo: sigue en el historial del paciente. **`409`** si no está cancelado o si ya estaba archivado |

### Pacientes

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/pacientes?q=texto` | RECEPCION, MEDICO, ADMIN | Padrón, filtrado por nombre o documento |
| GET | `/api/pacientes/mi-ficha` | todos | Ficha del paciente asociado al usuario de la sesión |
| GET | `/api/pacientes/{documento}` | todos | El paciente, o `404` |
| POST | `/api/pacientes` | PACIENTE, RECEPCION, ADMIN | `201`, o `400` si el documento se repite |
| PUT | `/api/pacientes/{documento}` | RECEPCION, ADMIN | El paciente actualizado |
| GET | `/api/pacientes/{documento}/turnos` | todos | Historial; un PACIENTE solo puede ver el suyo |
| GET | `/api/pacientes/{documento}/cobertura` | todos | Cobertura validada por SOAP (PAMI, OSDE, IOMA) o REST (el resto); `502` si el sistema externo no responde |
| GET | `/api/profesionales/mi-perfil` | **MEDICO** | Ficha de profesional asociada al usuario logueado |

### Profesionales

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/profesionales` | todos | Todos los profesionales con su disponibilidad |
| GET | `/api/profesionales?especialidad=Cardiologia` | todos | Los de esa especialidad |
| GET | `/api/profesionales/especialidades` | todos | Especialidades con profesionales activos |
| GET | `/api/profesionales/{id}` | todos | El profesional, o `404` |
| POST | `/api/profesionales` | **ADMIN** | `201`, o `403` si el rol no corresponde |
| PUT | `/api/profesionales/{id}/disponibilidad` | **RECEPCION, MEDICO, ADMIN** | Reemplaza la agenda semanal. RECEPCION y ADMIN pueden editar la de cualquiera; un MEDICO solo la propia (`403` si no corresponde) |
| GET | `/api/profesionales/{id}/horarios-libres?fecha=aaaa-MM-dd` | todos | Horarios disponibles ese día |

#### `Idempotency-Key` en `POST /api/turnos`

Un corte de red o un doble clic pueden hacer que el mismo alta llegue dos veces. Si el
cliente manda la cabecera `Idempotency-Key` (8 a 100 caracteres; se recomienda un UUID), el
servidor recuerda la clave durante 24 h, **por usuario**, junto con una huella SHA-256 del
pedido:

| Caso | Respuesta |
|---|---|
| Primera vez | `201`, se crea el turno |
| Misma clave y mismo pedido | `201` con **el mismo turno** y `Idempotent-Replayed: true`; no se crea otro ni se manda otro mensaje JMS |
| Misma clave y **otro** pedido | `422` (`idempotencia-reutilizada`) |
| Dos pedidos simultáneos con la misma clave | el segundo recibe `409` (`idempotencia-en-curso`) y su turno se deshace junto con sus mensajes JMS |

La interfaz la manda siempre: reutiliza la clave si se reintenta el mismo pedido y genera
una nueva si cambia algún dato. Las claves vencidas las borra un timer EJB (`@Schedule`).

### Reserva guiada (componente `@Stateful`)

| Método | Ruta | Roles | Qué hace |
|---|---|---|---|
| GET | `/api/reserva` | PACIENTE, RECEPCION, ADMIN | Estado actual **según lo recuerda el servidor** |
| POST | `/api/reserva/iniciar` | ídem | Paso 0: identifica al paciente |
| PUT | `/api/reserva/especialidad` | ídem | Paso 1 |
| POST | `/api/reserva/orientacion` | ídem | Paso 1 con IA: recibe `{"mensajes":[{"rol":"usuario","texto":"..."}]}` y devuelve `{"mensaje","especialidad","urgente"}`. Solo sugiere especialidades con profesionales activos; elegirla sigue siendo `PUT /especialidad`. `502` si el proveedor de IA no responde |
| PUT | `/api/reserva/profesional` | ídem | Paso 2 |
| GET | `/api/reserva/horarios?fecha=…` | ídem | Horarios libres del profesional **ya recordado** |
| PUT | `/api/reserva/horario` | ídem | Paso 3 |
| PUT | `/api/reserva/volver` | ídem | Retrocede un paso y olvida lo posterior |
| POST | `/api/reserva/confirmar` | ídem | Paso 4: crea el turno y cierra la conversación |
| DELETE | `/api/reserva` | ídem | Abandona la reserva en curso |

### Tablero

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/tablero` | RECEPCION, MEDICO, ADMIN | Métricas y próximos turnos, en una sola llamada |

### Mensajería (operación)

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/mensajeria/mensajes-fallidos` | **ADMIN** | Mensajes JMS que agotaron los reintentos y llegaron a la DLQ `MensajesFallidos` |
| POST | `/api/mensajeria/mensaje-defectuoso` | **ADMIN** | `202`: manda un mensaje que ningún consumidor entiende, para ver en vivo los 4 intentos (espera de 1, 2 y 4 s) y el pase a la DLQ |

### Formato de los errores — Problem Details (RFC 9457)

Todos los errores salen como `application/problem+json`:

```json
{
  "type": "urn:mediconecta:problema:regla-negocio",
  "title": "Regla de negocio incumplida",
  "status": 400,
  "detail": "No se puede agendar un turno en el pasado.",
  "instance": "/mediconecta/api/turnos"
}
```

El cliente decide con `type` (estable); `detail` es para personas.

| `type` (`urn:mediconecta:problema:...`) | HTTP | Cuándo |
|---|---|---|
| `regla-negocio` | 400 | Regla de negocio incumplida (fecha pasada, superposición, ownership) |
| `pedido-invalido` | 400 | El cuerpo no es un JSON válido para la operación |
| `no-autenticado` · `credenciales-invalidas` | 401 | Sin token, token inválido o vencido · usuario o contraseña incorrectos |
| `acceso-denegado` | 403 | El rol no tiene permiso (`@RolesAllowed`) |
| `no-encontrado` | 404 | La entidad no existe |
| `transicion-invalida` | 409 | Transición de estado inválida (patrón State) |
| `idempotencia-en-curso` · `idempotencia-reutilizada` | 409 · 422 | Ver `Idempotency-Key` |
| `modificacion-concurrente` · `precondicion-fallida` | 409 · 412 | Dos modificaciones a la vez · `If-Match` con una versión vieja |
| `demasiadas-solicitudes` | 429 | Fuerza bruta en el login (5 fallos por usuario o 30 por IP en 5 min) o más de 10 consultas por minuto al asistente de IA. Trae `Retry-After` |
| `integracion-externa` · `tiempo-agotado` | 502 · 504 | Un sistema externo falló · no respondió a tiempo. La cobertura y la IA **no** devuelven estos errores: tienen fallback (`contingencia: true`) |
| `error-interno` | 500 | Error no previsto: sin detalles internos; el `idError` de la respuesta es el mismo que queda en el log |

Los errores que genera JAX-RS (ruta inexistente `404`, método no permitido `405` con
cabecera `Allow`, `415`) usan `"type": "about:blank"`, como indica la RFC.

### Compatibilidad con la API de la Entrega 1

`POST /api/turnos` sigue aceptando el cuerpo original, con el paciente embebido y el
profesional por nombre:

```json
{
  "paciente": { "nombre": "Diego Sosa", "documento": "33222111" },
  "profesional": "Dr. Martin Ruiz",
  "especialidad": "Traumatologia",
  "fechaHora": "2026-12-15T10:30"
}
```

La interfaz nueva usa la forma corta, que es la recomendada:

```json
{ "documentoPaciente": "33222111", "profesionalId": 2, "fechaHora": "2026-12-15T10:30" }
```

Las dos terminan en el mismo objeto de negocio. El detalle está en `PedidoTurno`.

---

## 6. Estructura del proyecto

```
mediconecta/
├── pom.xml                                   WAR · Jakarta EE 10 (provided) · Java 17
│                                             + plugins: Checkstyle, JaCoCo, Flyway
├── Dockerfile · compose.yaml · .env.example  imagen de la app y entorno completo
├── .github/workflows/ci.yml                  CI: linter + tests + cobertura, migraciones, E2E
├── config/checkstyle/checkstyle.xml          reglas del linter
├── db/migraciones/                           V1__esquema_inicial.sql · V2__... (Flyway)
├── docker/configurar-wildfly-docker.cli      configuracion de WildFly dentro de la imagen
├── docs/
│   ├── Entrega_Obligatoria_1.md · _2.md      documentos de cada entrega
│   ├── Mejoras_Correccion_Entrega_2.md       respuesta punto por punto a la correccion
│   ├── arquitectura.md                       capas, componentes, patrones y decisiones
│   ├── guion-defensa.md                      recorrido de la demo, paso a paso
│   └── preguntas-defensa.md                  preguntas probables y respuestas cortas
├── scripts/
│   ├── smoke-test.sh                         123 pruebas de punta a punta (Linux/macOS, CI)
│   ├── smoke-test.ps1                        version PowerShell (Windows)
│   ├── ci/resumen-cobertura.py               tabla de cobertura para GitHub Actions
│   ├── db/
│   │   ├── levantar-postgres.ps1             PostgreSQL en Docker
│   │   └── crear-base.sql                    base y usuario en un PostgreSQL local
│   └── wildfly/
│       ├── configurar-wildfly.ps1            todo en un paso (driver ... seguridad)
│       ├── configurar-datasource.cli         datasource
│       ├── configurar-mensajeria.cli         reintentos con backoff y DLQ de JMS
│       ├── configurar-limites.cli            timeouts de JTA, HTTP y pool de conexiones
│       ├── configurar-propiedades.cli        secretos como propiedades del sistema
│       └── configurar-seguridad.cli          integrated-jaspi=false para JWT
└── src/main/
    ├── java/com/mediconecta/
    │   ├── modelo/          Paciente · Profesional · Disponibilidad · Turno · Usuario
    │   │                    Notificacion · MensajeFallido · ClaveIdempotencia
    │   │   └── estado/      patron State: TurnoProgramado · TurnoCancelado · TurnoAtendido
    │   ├── datos/           un Repositorio* por entidad + SondaBaseDeDatos
    │   │   ├── jpa/         implementaciones sobre JPA + PostgreSQL (las activas)
    │   │   └── memoria/     implementaciones en memoria (@Alternative, desactivadas)
    │   ├── negocio/         GestionTurnos · GestionPacientes · GestionProfesionales
    │   │                    AsistenteReservaTurno (@Stateful) · FachadaReservas · FabricaTurnos
    │   │                    GestionNotificaciones · MonitorSalud · LimpiezaIdempotencia (timer)
    │   ├── seguridad/       JWT · HashDeClaves · CabecerasSeguridad (cabeceras HTTP)
    │   ├── mensajeria/      EventoTurno (JSON v1) · productor · 3 MDB idempotentes · monitor DLQ
    │   ├── integraciones/   Adapter SOAP + REST de obras sociales (con timeouts) · IA
    │   └── presentacion/    recursos JAX-RS · Problem Details (Problemas, ExceptionMappers)
    ├── resources/
    │   ├── META-INF/persistence.xml          hbm2ddl=validate · timeout de consultas
    │   └── wsdl/                             contrato del sistema legado (SOAP)
    └── webapp/
        ├── index.html · login.html · api-docs.html (Swagger UI)
        ├── openapi.json                      contrato OpenAPI 3.1
        ├── css/styles.css                    design system del prototipo
        ├── js/                               modulos ES: api, ui, layout y uno por pantalla
        ├── app/                              pantallas
        └── WEB-INF/web.xml · jboss-web.xml · beans.xml · undertow-handlers.conf (SameSite)
src/test/java/                                171 tests unitarios JUnit 5
```

---

## 7. Calidad y pruebas

### Tests unitarios, cobertura y linter (sin servidor ni base de datos)

```powershell
./mvnw verify                                             # igual que mvn verify, con Maven 3.9.9 fijado
mvn verify                                                # enforcer + linter + 194 tests + cobertura minima
mvn test-compile org.pitest:pitest-maven:mutationCoverage # mutation testing (PIT, minimo 60 %)
mvn test                                                  # solo linter + tests (informe en target/site/jacoco/)
mvn test -Dtest=FachadaReservasTest                       # una clase
mvn test -Dtest=GestionTurnosTest#cancelarLiberaElHorario # un test
```

- **Linter (Checkstyle 10).** Corre en la fase `validate`, antes de compilar: cualquier
  build falla si el código no respeta [`config/checkstyle/checkstyle.xml`](config/checkstyle/checkstyle.xml)
  (imports sin usar, `==` entre Strings, `equals` sin `hashCode`, bloques vacíos, nombres,
  modificadores, etc.).
- **Cobertura (JaCoCo).** `mvn test` deja el informe en `target/site/jacoco/index.html`;
  `mvn verify` exige **80 % de líneas** sobre la lógica (hoy ~84 %). Las clases que solo
  tienen sentido dentro del contenedor (recursos REST, repositorios JPA, sistemas
  simulados, productor JMS) quedan fuera del mínimo y las cubre la prueba de punta a punta.
- Los EJB se prueban como objetos comunes con los repositorios en memoria; los MDB, con
  mensajes JMS simulados. Hay un test que compara `openapi.json` con los recursos reales.
- **Capa de negocio:** el build exige además 80 % de líneas y 70 % de ramas en
  `negocio` (hoy 95,0 % y 82,5 %). **Mutation testing (PIT):** 85 % de mutantes
  detectados (mínimo 60 %).
- **Build reproducible:** `./mvnw` usa siempre Maven 3.9.9 y el enforcer exige JDK ≥ 17 y
  versión fija en todos los plugins.

### Carga, seguridad e IA

| Prueba | Comando | Último resultado (19/11/2026) |
|---|---|---|
| Carga (k6, p95 < 500 ms) | `docker run --rm -i --network host -e BASE_URL=... grafana/k6:0.54.0 run - < pruebas/carga/carga.js` | p95 35,6 ms, 0 % errores, ~200 req/s |
| OWASP ZAP baseline | ver [plan de pruebas](docs/Plan_de_Pruebas.md) §5 | 0 High, 0 Medium, 0 Low |
| Secretos en el historial | `docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:v8.21.2 git /repo` | 0 hallazgos |
| IA (12 casos) | `pruebas/ia/evaluar-ia.sh http://localhost:8080/mediconecta` | 12/12 |

El [plan de pruebas](docs/Plan_de_Pruebas.md) §6 tiene el guion para demostrar en vivo el
timeout con fallback, el broker caído sin pérdida de mensajes, la DLQ, `Idempotency-Key` y
ETag.

### Prueba de punta a punta (contra la aplicación desplegada)

```bash
scripts/smoke-test.sh                                     # http://localhost:8080/mediconecta
scripts/smoke-test.sh http://localhost:8180/mediconecta   # otro puerto
```

```powershell
.\scripts\smoke-test.ps1 -BaseUrl http://localhost:8080/mediconecta   # Windows
```

Recorre: health check, cabeceras de seguridad, contrato OpenAPI, login JWT (credenciales
malas, token adulterado), roles, Problem Details, pacientes, profesionales, turnos (fecha
pasada, superposición, ownership, 409 del patrón State), `Idempotency-Key`, cobertura
SOAP/REST, los cuatro pasos del asistente stateful, los avisos de los tres consumidores JMS,
el pase de un mensaje defectuoso a la DLQ, **seguridad en vivo** (BOLA/IDOR, BFLA, JWT
`alg:none`/firma ajena/mal formado, inyección SQL y XSS, 429 ante fuerza bruta), **ETag**
(304 y 412) y el **gateway de IA** (orientación, emergencia, fallback). Devuelve 0 si todas
pasan. La versión PowerShell cubre un subconjunto.

Última ejecución de `smoke-test.sh` (19/11/2026): **148 pruebas OK, 0 fallidas** sobre
WildFly 36.0.1.Final local (`standalone-full.xml`, JDK 17, PostgreSQL 16).

### Integración continua (GitHub Actions)

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) corre en cada push y pull request:

1. **calidad**: `./mvnw verify` (enforcer + linter + tests + cobertura mínima total y de
   negocio); publica el informe de cobertura, el WAR y una tabla de cobertura por paquete.
2. **mutacion**: PIT sobre la capa de negocio (falla con menos de 60 %).
3. **secretos**: gitleaks sobre todo el historial.
4. **sonar**: análisis y Quality Gate de SonarCloud (necesita el secreto `SONAR_TOKEN`;
   ver plan de pruebas §5).
5. **migraciones**: aplica `db/migraciones` sobre un PostgreSQL vacío y las vuelve a
   correr para comprobar que no rompen nada.
6. **punta-a-punta**: `docker compose up --build`, espera al health check, corre
   `scripts/smoke-test.sh`, la prueba de carga k6 (p95 < 500 ms) y el escaneo OWASP ZAP
   (falla con alertas High). Si falla, vuelca los logs de los contenedores.

---

## 8. Ejecutar sin base de datos (plan B)

Si en la máquina donde se va a mostrar el TP no hay forma de levantar PostgreSQL, el
proyecto puede correr con los repositorios en memoria. Son dos cambios y **no se toca ni
una línea de la capa de negocio ni de la de presentación**, que es justamente la prueba de
que el patrón DAO funciona:

1. En `src/main/webapp/WEB-INF/beans.xml`, descomentar el bloque `<alternatives>`
   (incluye los usuarios en memoria).
2. Borrar `src/main/resources/META-INF/persistence.xml` antes de empaquetar (si queda,
   WildFly intenta crear la unidad de persistencia y el despliegue falla al no encontrar el
   datasource).

Los datos se pierden al reiniciar el servidor. Es un plan de contingencia, no el modo
normal de la entrega.

---

## 9. Limitaciones conocidas

- La carga de datos de ejemplo corre una sola vez, cuando la base está vacía. Para volver
  al estado inicial: `TRUNCATE claves_idempotencia, notificaciones, turnos, disponibilidades, profesionales, pacientes RESTART IDENTITY CASCADE;`
  y redesplegar (o, con Docker, `docker compose down -v`).
- Un turno ocupa un único horario de inicio; no se modela la duración variable de la
  atención ni el solapamiento parcial entre turnos de distinta duración.
- Marcar un turno como atendido no registra todavía el detalle de la atención (diagnóstico,
  indicaciones).
- El JWT no se puede revocar antes de que venza (no hay lista de tokens revocados): cerrar
  sesión lo descarta en el navegador. Vence a las 2 horas por omisión
  (`mediconecta.jwt.minutos`).
- Los consumidores JMS registran los avisos (mail, SMS, agenda) en la tabla
  `notificaciones` pero no los envían a un proveedor real: ese envío sería otro proceso que
  lee esa tabla.
- La documentación interactiva (`api-docs.html`) carga Swagger UI desde jsDelivr (con
  Subresource Integrity); sin internet se puede leer `openapi.json` igual.
- Cuando el contenedor rechaza una invocación por rol, WildFly escribe un `EJBAccessException`
  con stack trace completo en el log. Es ruidoso pero correcto, y de hecho resulta útil
  durante la defensa para mostrar que quien rechaza es el contenedor y no la aplicación.
