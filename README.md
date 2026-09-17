# MediConecta

**Trabajo Práctico Integrador — Desarrollo de Aplicaciones II — UADE**
**Entrega Obligatoria N.º 1 (1.er parcial)**

MediConecta es un sistema de gestión de turnos médicos construido sobre **Jakarta EE 10**
y desplegado en **WildFly**. Esta entrega contiene cuatro componentes de negocio reales
(tres *stateless* y uno *stateful*), arquitectura estricta en tres capas, tres patrones de
diseño, seguridad declarativa con cuatro roles, persistencia con **JPA + PostgreSQL** y una
interfaz web completa que sigue el prototipo de Figma.

| | |
|---|---|
| **Aplicación** | <http://localhost:8080/mediconecta/> |
| **Prueba de vida** | <http://localhost:8080/mediconecta/api/salud> |
| **Documento de la entrega** | [docs/Entrega_Obligatoria_1.md](docs/Entrega_Obligatoria_1.md) |
| **Arquitectura y patrones** | [docs/arquitectura.md](docs/arquitectura.md) |
| **Guion de la defensa** | [docs/guion-defensa.md](docs/guion-defensa.md) |
| **Preguntas probables** | [docs/preguntas-defensa.md](docs/preguntas-defensa.md) |

---

## 1. Qué hace la aplicación

- **Iniciar sesión** con usuarios y roles administrados por el contenedor (PACIENTE,
  RECEPCION, MEDICO, ADMIN).
- **Tablero** con turnos del día, pacientes activos, profesionales y turnos pendientes,
  todo calculado con datos reales de la base.
- **Turnos**: listar con buscador y filtros, ver el detalle, agendar, **cancelar** y
  **archivar** (sacar de la agenda un turno cancelado sin borrar su historial).
- **Mis turnos**: un PACIENTE consulta y cancela solo sus propios turnos.
- **Pacientes**: padrón con buscador, alta, edición de ficha e historial de turnos.
- **Profesionales**: listado por especialidad, alta (solo ADMIN) y gestión de la
  disponibilidad semanal — RECEPCION y ADMIN sobre cualquier profesional, un MEDICO solo
  sobre la suya propia.
- **Reserva guiada en pasos**, cuyo estado intermedio vive en un EJB `@Stateful`
  del servidor y no en el navegador.
- Interfaz **responsive**: barra lateral en escritorio, navegación inferior en teléfono.

### Componentes implementados

| # | Componente | Tipo | Qué resuelve |
|---|---|---|---|
| C1 | `GestionTurnos` | EJB `@Stateless` | Reglas de la agenda: alta, consulta y cancelación de turnos |
| C2 | `GestionPacientes` | EJB `@Stateless` | Padrón de pacientes e historial de atenciones |
| C3 | `GestionProfesionales` | EJB `@Stateless` | Profesionales, especialidades, disponibilidad y horarios libres |
| C4 | `AsistenteReservaTurno` | EJB **`@Stateful`** | Conversación de reserva en cuatro pasos |

Los componentes C5 (Notificaciones / JMS) y C6 (Integración con obras sociales / SOAP)
están identificados y documentados en [docs/arquitectura.md](docs/arquitectura.md), pero
**no** se implementan en esta entrega: corresponden a las Entregas 3 y 4.

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
> `provided`), que es la plataforma de WildFly 36.0.1.Final. Todo lo que se describe en
> este README fue **verificado de punta a punta sobre WildFly 41.0.1.Final con JDK 21 y
> PostgreSQL 16.14**, que es el entorno disponible en la máquina de desarrollo. WildFly 41
> implementa Jakarta EE 11, que mantiene compatibilidad hacia atrás con las APIs que usa el
> proyecto (EJB, CDI, JAX-RS, JPA, Servlet y seguridad declarativa). **No se ejecutó sobre
> WildFly 36.0.1.Final**, así que no se afirma haberlo probado ahí; los pasos son idénticos
> para las dos versiones.

---

## 3. Puesta en marcha (copiar y pegar)

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

En los dos casos, **las tablas no hay que crearlas**: Hibernate las crea o actualiza sola
al desplegar el WAR. El esquema equivalente, por si se quiere revisar o crear a mano, está
en [`scripts/db/esquema.sql`](scripts/db/esquema.sql).

### Paso 2 — Configurar WildFly

Un solo script deja el servidor listo: instala el driver JDBC como módulo, crea el
datasource y da de alta los cuatro usuarios de demostración.

```powershell
.\scripts\wildfly\configurar-wildfly.ps1 -WildFly "C:\wildfly-36.0.1.Final"
```

El script tiene dos momentos:

1. **Con WildFly apagado** descarga el driver de PostgreSQL y lo instala como módulo
   `org.postgresql`.
2. **Con WildFly encendido** crea el datasource `java:/jdbc/MediConectaDS`. Si el servidor
   todavía no está arriba, levantarlo (`bin\standalone.bat`) y volver a correr el script
   con `-SoloDatasource`.

Si se prefiere hacerlo a mano, los pasos exactos son:

```powershell
# 1) Módulo del driver (servidor apagado)
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --command="module add --name=org.postgresql --resources=C:\ruta\postgresql-42.7.4.jar --dependencies=jakarta.transaction.api,java.sql,java.logging"

# 2) Datasource (servidor encendido)
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --connect --file=scripts\wildfly\configurar-datasource.cli

# 3) Usuarios de demostración
C:\wildfly-36.0.1.Final\bin\add-user.bat -a -u paciente  -p Demo.2026 -g PACIENTE
C:\wildfly-36.0.1.Final\bin\add-user.bat -a -u recepcion -p Demo.2026 -g RECEPCION
C:\wildfly-36.0.1.Final\bin\add-user.bat -a -u medico    -p Demo.2026 -g MEDICO
C:\wildfly-36.0.1.Final\bin\add-user.bat -a -u admin     -p Demo.2026 -g ADMIN
```

El archivo `.cli` toma la URL, el usuario y la contraseña de PostgreSQL como propiedades
del sistema (`-Dmediconecta.db.usuario=...`), con valores por omisión de desarrollo.
**No hay ninguna credencial de base de datos dentro del código Java versionado.**

### Paso 3 — Compilar

```powershell
mvn clean package
```

Genera `target\mediconecta.war`. El nombre del WAR (`<finalName>mediconecta</finalName>`)
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

Son **usuarios locales ficticios**, creados únicamente para la defensa. Viven en
`standalone/configuration/application-users.properties` y
`application-roles.properties` de WildFly, no en el código del proyecto.

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

Context root `/mediconecta`, prefijo `/api`. Todos los endpoints exigen sesión iniciada
**salvo `/api/salud`**, que es público a propósito.

### Salud

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/salud` | público | `200` y el texto **¡Hola desde el contenedor!** |

### Sesión

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/sesion` | todos | `{"autenticado":true,"usuario":"recepcion","roles":["RECEPCION"]}` |
| POST | `/api/sesion/logout` | todos | `204` y la sesión invalidada |

### Turnos

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/turnos` | RECEPCION, MEDICO, ADMIN | Agenda operativa (sin archivados) ordenada por fecha |
| GET | `/api/turnos/{id}` | todos | El turno, o `404` (funciona aunque esté archivado) |
| POST | `/api/turnos` | PACIENTE, RECEPCION, ADMIN | `201` + `Location`, o `400` con la regla incumplida. Un PACIENTE solo puede reservar para sí mismo |
| PUT | `/api/turnos/{id}/cancelar` | PACIENTE, RECEPCION, MEDICO, ADMIN | El turno en estado `CANCELADO`. Ownership: un PACIENTE solo cancela los propios, un MEDICO solo los de su agenda; `400` si no corresponde |
| PUT | `/api/turnos/{id}/archivar` | **RECEPCION, ADMIN** | Saca de la agenda un turno ya `CANCELADO` sin borrarlo: sigue en el historial del paciente. `400` si no está cancelado o si ya estaba archivado |

### Pacientes

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/pacientes?q=texto` | RECEPCION, MEDICO, ADMIN | Padrón, filtrado por nombre o documento |
| GET | `/api/pacientes/mi-ficha` | todos | Ficha del paciente asociado al usuario de la sesión |
| GET | `/api/pacientes/{documento}` | todos | El paciente, o `404` |
| POST | `/api/pacientes` | PACIENTE, RECEPCION, ADMIN | `201`, o `400` si el documento se repite |
| PUT | `/api/pacientes/{documento}` | RECEPCION, ADMIN | El paciente actualizado |
| GET | `/api/pacientes/{documento}/turnos` | todos | Historial; un PACIENTE solo puede ver el suyo |
| GET | `/api/profesionales/mi-perfil` | **MEDICO** | Ficha de profesional asociada al usuario logueado |

### Profesionales

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/profesionales` | todos | Todos los profesionales con su disponibilidad |
| GET | `/api/profesionales?especialidad=Cardiologia` | todos | Los de esa especialidad |
| GET | `/api/profesionales/especialidades` | todos | Especialidades con profesionales activos |
| GET | `/api/profesionales/{id}` | todos | El profesional, o `404` |
| POST | `/api/profesionales` | **ADMIN** | `201`, o `403` si el rol no corresponde |
| PUT | `/api/profesionales/{id}/disponibilidad` | **RECEPCION, MEDICO, ADMIN** | Reemplaza la agenda semanal. RECEPCION y ADMIN pueden editar la de cualquiera; un MEDICO solo la propia (`400` si no corresponde) |
| GET | `/api/profesionales/{id}/horarios-libres?fecha=aaaa-MM-dd` | todos | Horarios disponibles ese día |

### Reserva guiada (componente `@Stateful`)

| Método | Ruta | Roles | Qué hace |
|---|---|---|---|
| GET | `/api/reserva` | PACIENTE, RECEPCION, ADMIN | Estado actual **según lo recuerda el servidor** |
| POST | `/api/reserva/iniciar` | ídem | Paso 0: identifica al paciente |
| PUT | `/api/reserva/especialidad` | ídem | Paso 1 |
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

### Formato de los errores

Todos los errores devuelven el mismo cuerpo:

```json
{ "error": "No se puede agendar un turno en el pasado.", "codigo": 400 }
```

`400` regla de negocio incumplida · `401` sin sesión · `403` rol sin permiso · `404` no existe.

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
├── README.md
├── docs/
│   ├── Entrega_Obligatoria_1.md              documento técnico de la entrega
│   ├── arquitectura.md                       capas, componentes, patrones y decisiones
│   ├── guion-defensa.md                      recorrido de la demo, paso a paso
│   ├── preguntas-defensa.md                  preguntas probables y respuestas cortas
│   └── trello-actualizaciones.md             estado del tablero
├── scripts/
│   ├── smoke-test.ps1                        61 pruebas de la API de punta a punta
│   ├── db/
│   │   ├── levantar-postgres.ps1             PostgreSQL en Docker
│   │   ├── crear-base.sql                    base y usuario en un PostgreSQL local
│   │   └── esquema.sql                       modelo relacional documentado
│   └── wildfly/
│       ├── configurar-wildfly.ps1            driver + datasource + usuarios, en un paso
│       └── configurar-datasource.cli         script de jboss-cli del datasource
└── src/main/
    ├── java/com/mediconecta/
    │   ├── modelo/          Paciente · Profesional · Disponibilidad · Turno · EstadoTurno
    │   ├── datos/           RepositorioTurnos · RepositorioPacientes · RepositorioProfesionales
    │   │   ├── jpa/         implementaciones sobre JPA + PostgreSQL (las activas)
    │   │   └── memoria/     implementaciones en memoria (@Alternative, desactivadas)
    │   ├── negocio/         GestionTurnos · GestionPacientes · GestionProfesionales
    │   │                    AsistenteReservaTurno (@Stateful) · FachadaReservas · FabricaTurnos
    │   │                    Roles · excepciones · carga de datos de ejemplo
    │   └── presentacion/    recursos JAX-RS · ExceptionMappers · ServletLogin
    │                        ConversacionDeReserva (puente sesión HTTP ↔ EJB stateful)
    ├── resources/META-INF/persistence.xml
    └── webapp/
        ├── index.html · login.html
        ├── css/styles.css                    design system del prototipo
        ├── js/                               módulos ES: api, ui, layout y uno por pantalla
        ├── app/                              pantallas protegidas
        └── WEB-INF/web.xml · beans.xml       seguridad declarativa · CDI
```

---

## 7. Pruebas

```powershell
.\scripts\smoke-test.ps1
```

Recorre **61 verificaciones** contra la aplicación desplegada: salud, seguridad por roles,
turnos (incluyendo fecha pasada, superposición y doble cancelación), pacientes,
profesionales, horarios libres y los cuatro pasos del asistente stateful. Devuelve código
de salida 0 si todas pasan.

Resultado de la última ejecución sobre WildFly 41.0.1.Final + PostgreSQL 16.14:
**61 pruebas OK, 0 fallidas**.

---

## 8. Ejecutar sin base de datos (plan B)

Si en la máquina donde se va a mostrar el TP no hay forma de levantar PostgreSQL, el
proyecto puede correr con los repositorios en memoria. Son dos cambios y **no se toca ni
una línea de la capa de negocio ni de la de presentación**, que es justamente la prueba de
que el patrón DAO funciona:

1. En `src/main/webapp/WEB-INF/beans.xml`, descomentar el bloque `<alternatives>`.
2. Borrar `src/main/resources/META-INF/persistence.xml` antes de empaquetar (si queda,
   WildFly intenta crear la unidad de persistencia y el despliegue falla al no encontrar el
   datasource).

Los datos se pierden al reiniciar el servidor. Es un plan de contingencia, no el modo
normal de la entrega.

---

## 9. Limitaciones conocidas

- La carga de datos de ejemplo corre una sola vez, cuando la base está vacía. Para volver
  al estado inicial: `TRUNCATE turnos, disponibilidades, profesionales, pacientes RESTART IDENTITY CASCADE;`
  y redesplegar.
- Un turno ocupa un único horario de inicio; no se modela la duración variable de la
  atención ni el solapamiento parcial entre turnos de distinta duración.
- El estado `ATENDIDO` existe en el modelo pero todavía no hay una operación que lo asigne:
  llega junto con el registro de la atención en una entrega posterior.
- No hay mensajería (JMS), SOAP ni integración con obras sociales: son las Entregas 3 y 4.
- Cuando el contenedor rechaza una invocación por rol, WildFly escribe un `EJBAccessException`
  con stack trace completo en el log. Es ruidoso pero correcto, y de hecho resulta útil
  durante la defensa para mostrar que quien rechaza es el contenedor y no la aplicación.
