# MediConecta — Componente "Gestión de Turnos"

**Trabajo Práctico Integrador — Desarrollo de Aplicaciones II — Entrega 1**

## 1. Descripción

MediConecta es un sistema de salud para la gestión de turnos médicos, pacientes y
profesionales. El TP completo crece entrega a entrega hasta integrar patrones de diseño,
seguridad, mensajería asincrónica, SOAP, REST y transacciones.

Esta **Entrega 1** contiene el **primer componente real del dominio: `GestionTurnos`**,
implementado como EJB **stateless**, separado en tres capas (presentación / negocio / datos)
y desplegado en un contenedor real (WildFly).

Qué se puede hacer hoy:

- verificar que el componente está vivo dentro del contenedor;
- listar los turnos de la agenda;
- consultar un turno por su identificador;
- solicitar un turno nuevo, con las reglas de negocio aplicadas (no se agenda en el pasado
  ni se pisa un horario ya ocupado del mismo profesional).

La documentación de arquitectura está en [docs/arquitectura.md](docs/arquitectura.md).

## 2. Requisitos

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | 17 o superior | El proyecto compila con `--release 17`. WildFly se recomienda ejecutarlo con JDK 17 o 21. |
| Apache Maven | 3.9.x | Ver la nota de instalación más abajo si `mvn` no está en el PATH. |
| WildFly | **36.0.1.Final** (`wildfly-36.0.1.Final.zip`) | Versión sobre la que este proyecto está probado de punta a punta. Descarga: <https://www.wildfly.org/downloads/> |

> **Cuál archivo bajar.** WildFly publica dos ZIP por versión: el estándar
> (`wildfly-36.0.1.Final.zip`, Jakarta EE 10) y `wildfly-preview-…zip`, que adelanta
> Jakarta EE 11. **Hay que bajar el estándar**, que es el que corresponde a la consigna.
> Existen versiones más nuevas (41.0.1.Final al momento de escribir esto), pero se fijó
> 36.0.1.Final para que todo el grupo pruebe sobre lo mismo y porque es la última de la
> línea Jakarta EE 10.

> **Si `mvn` no está instalado (Windows):** `winget install Apache.Maven` y reabrir la
> terminal. Alternativa sin instalar nada: IntelliJ IDEA e Eclipse traen Maven embebido y
> pueden ejecutar el objetivo `clean package` desde el panel de Maven del IDE.

> **Consejo de la cátedra:** descomprimir WildFly en una ruta simple, sin espacios, sin
> tildes y fuera de OneDrive. Por ejemplo `C:\wildfly-36.0.1.Final`.

## 3. Estructura del proyecto

```
mediconecta-turnos/
├── pom.xml                              WAR, Jakarta EE 10 (provided), Java 17
├── README.md
├── docs/
│   └── arquitectura.md                  capas, stateless, diagrama y decisiones
└── src/main/
    ├── java/com/mediconecta/turnos/
    │   ├── modelo/                      Turno, Paciente, EstadoTurno
    │   ├── datos/                       RepositorioTurnos (interfaz)
    │   │                                RepositorioTurnosEnMemoria (implementación)
    │   ├── negocio/                     GestionTurnos (@Stateless) + excepciones
    │   └── presentacion/                AplicacionRest, RecursoSalud, RecursoTurnos,
    │                                    ExceptionMappers, ErrorRespuesta
    └── webapp/
        ├── index.html                   página de demostración que consume la API
        └── WEB-INF/beans.xml            activa CDI
```

No hay `web.xml`: la configuración de JAX-RS está en la clase `AplicacionRest` con
`@ApplicationPath("/api")`.

## 4. Pasos para compilar

1. Abrir una terminal en la raíz del proyecto (donde está `pom.xml`).
2. Verificar la versión de Java:
   ```
   java -version
   ```
   Debe informar 17 o superior.
3. Compilar y empaquetar:
   ```
   mvn clean package
   ```
4. Verificar que se generó el artefacto:
   ```
   target/mediconecta.war
   ```
   El nombre del WAR (`mediconecta`) es el que define el **context root**, es decir la
   primera parte de todas las URLs.

## 5. Pasos para desplegar en WildFly

### Opción A — carpeta `deployments` (la más rápida para la demo)

1. Levantar el servidor:
   - Windows: `C:\wildfly-36.0.1.Final\bin\standalone.bat`
   - Linux / macOS: `./bin/standalone.sh`
2. Esperar en la consola la línea `WildFly ... started in ...ms`.
3. Comprobar que el servidor responde: <http://localhost:8080>
4. Copiar `target/mediconecta.war` dentro de `C:\wildfly-36.0.1.Final\standalone\deployments\`.
5. WildFly detecta el archivo y lo despliega solo. En el log tiene que aparecer:
   ```
   Deployed "mediconecta.war"
   ```
   y en la carpeta `deployments` se crea el archivo marcador `mediconecta.war.deployed`.
   Si en cambio aparece `mediconecta.war.failed`, ese archivo contiene el motivo del error.

### Opción B — consola de administración (la que muestra la guía de la cátedra)

1. Crear el usuario administrador (una sola vez):
   - ejecutar `bin\add-user.bat`
   - elegir `a` (Management User), poner usuario y contraseña, y responder `no` cuando
     pregunta si es para un proceso remoto.
2. Levantar el servidor con `bin\standalone.bat`.
3. Abrir <http://localhost:9990> e iniciar sesión con ese usuario.
4. Ir a **Deployments → Add → Upload Deployment**, elegir `target/mediconecta.war`,
   **Next → Finish**.
5. El despliegue queda listado y habilitado.

### Redesplegar después de un cambio

```
mvn clean package
```
y volver a copiar el WAR a `deployments` (sobrescribiendo), o usar **Deployments → Replace**
en la consola de administración.

## 6. URLs de prueba

Context root: `mediconecta`.

| # | Método | URL | Qué devuelve |
|---|---|---|---|
| 1 | GET | <http://localhost:8080/mediconecta/> | Página de demostración que consume la API. |
| 2 | GET | <http://localhost:8080/mediconecta/api/salud> | `200` y el texto exacto **¡Hola desde el contenedor!** |
| 3 | GET | <http://localhost:8080/mediconecta/api/turnos> | `200` y el arreglo JSON con los 3 turnos de ejemplo, ordenados por fecha. |
| 4 | GET | <http://localhost:8080/mediconecta/api/turnos/1> | `200` y el turno de id 1 en JSON. |
| 5 | GET | <http://localhost:8080/mediconecta/api/turnos/999> | `404` y `{"error":"No existe un turno con id 999","codigo":404}` |
| 6 | POST | `http://localhost:8080/mediconecta/api/turnos` | `201 Created`, cabecera `Location: .../api/turnos/{id}` y el turno creado con su `id` y estado `PROGRAMADO`. |
| 7 | POST | `http://localhost:8080/mediconecta/api/turnos` con fecha pasada | `400` y `{"error":"No se puede agendar un turno en el pasado.","codigo":400}` |
| 8 | POST | `http://localhost:8080/mediconecta/api/turnos` en un horario ya tomado | `400` y `{"error":"El profesional ... ya tiene un turno en ese horario.","codigo":400}` |
| 9 | POST | `http://localhost:8080/mediconecta/api/turnos` sin paciente | `400` y `{"error":"El paciente debe tener nombre y documento.","codigo":400}` |

Los puntos 1 a 5 se prueban directamente desde la barra del navegador. Para los POST se usa
el formulario de la página de demostración, o alguno de estos comandos.

**PowerShell:**

```powershell
$cuerpo = @{
    paciente     = @{ nombre = "Diego Sosa"; documento = "33222111" }
    profesional  = "Dr. Martin Ruiz"
    especialidad = "Traumatologia"
    fechaHora    = "2026-12-15T10:30"
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8080/mediconecta/api/turnos `
                  -Method Post -ContentType "application/json" -Body $cuerpo
```

**curl (Git Bash / Linux / macOS):**

```bash
curl -i -X POST http://localhost:8080/mediconecta/api/turnos \
     -H "Content-Type: application/json" \
     -d '{"paciente":{"nombre":"Diego Sosa","documento":"33222111"},
          "profesional":"Dr. Martin Ruiz",
          "especialidad":"Traumatologia",
          "fechaHora":"2026-12-15T10:30"}'
```

Ejemplo de respuesta de `GET /api/turnos`:

```json
[
  {
    "id": 1,
    "paciente": { "nombre": "Ana Gomez", "documento": "30111222" },
    "profesional": "Dra. Laura Fernandez",
    "especialidad": "Cardiologia",
    "fechaHora": "2026-09-03T09:30",
    "estado": "PROGRAMADO"
  }
]
```

> **Estado de verificación.** Los 9 casos de esta tabla, más las validaciones de
> especialidad faltante y de fecha mal formada, fueron ejecutados contra
> WildFly 36.0.1.Final con JDK 17: **12 de 12 dan el resultado esperado** y el log del
> servidor queda sin errores.

## 7. Cómo demostrarlo en vivo (guion para la defensa)

1. **Antes de empezar** (con la pantalla todavía sin compartir): tener WildFly descomprimido,
   el proyecto abierto en el IDE y el navegador con las pestañas de las URLs precargadas.
2. **Compilar delante del docente**: `mvn clean package` y mostrar que aparece
   `target/mediconecta.war`. Es la prueba de que el WAR se genera en el momento.
3. **Levantar WildFly**: `standalone.bat` y señalar en el log la línea `started in ...ms`.
4. **Desplegar**: copiar el WAR a `standalone\deployments\` y mostrar en el log la línea
   `Deployed "mediconecta.war"`. Alternativa: hacerlo desde <http://localhost:9990>.
5. **Prueba de vida**: abrir <http://localhost:8080/mediconecta/api/salud> y mostrar
   **¡Hola desde el contenedor!** Esta es la prueba que pide la guía de la cátedra.
6. **Mostrar el componente real**: abrir <http://localhost:8080/mediconecta/api/turnos> y
   mostrar los turnos precargados. Aclarar que ya no es un "hola mundo": los datos vienen del
   repositorio, pasan por la capa de negocio que los ordena, y la capa de presentación los
   convierte a JSON.
7. **Recorrer las capas en el código**, en el orden en que viaja un request:
   `RecursoTurnos` → `GestionTurnos` → `RepositorioTurnos` (interfaz) →
   `RepositorioTurnosEnMemoria`. Señalar que el negocio inyecta la **interfaz**, no la
   implementación.
8. **Alta correcta**: en <http://localhost:8080/mediconecta/> completar el formulario y
   solicitar un turno. Mostrar que aparece en la tabla con estado `PROGRAMADO`.
9. **Mostrar una regla de negocio fallando**: pedir un turno con una fecha del año pasado.
   La API responde `400` con el mensaje de la regla. Esto demuestra que la validación está
   en el negocio y no en el navegador.
10. **Mostrar el 404**: abrir <http://localhost:8080/mediconecta/api/turnos/999>.
11. **Cerrar** contando qué sigue: 6 componentes identificados en `docs/arquitectura.md`,
    un componente stateful en la Entrega 2 y JPA reemplazando el repositorio en memoria.

## 8. Preguntas probables de la cátedra y sus respuestas

**¿Por qué el componente es stateless?**
Porque `GestionTurnos` no guarda nada entre una invocación y la siguiente: cada llamada
recibe en sus parámetros todo lo que necesita, resuelve y termina. No hay conversación en
curso asociada a un cliente. Por eso dos requests del mismo usuario pueden ser atendidos por
instancias distintas del pool sin ninguna diferencia observable, y el contenedor puede
reutilizar un pool acotado de instancias en lugar de dedicar una por cliente.

**Pero los turnos se guardan en un Map, ¿eso no es estado?**
Sí, pero es **estado de aplicación** y vive en la capa de datos (`RepositorioTurnosEnMemoria`,
que sí es `@ApplicationScoped`), igual que mañana viviría en una base de datos. Lo que define
a un componente como stateless es que no conserve **estado de conversación con un cliente
particular**, y `GestionTurnos` no conserva ninguno.

**¿Cuándo tendrían un componente stateful?**
En la Entrega 2, con el asistente de reserva de turno en varios pasos: elegir especialidad,
después profesional, después horario y recién al final confirmar. Ahí el servidor necesita
recordar las elecciones parciales *de ese usuario* entre request y request, y eso es
exactamente lo que resuelve un `@Stateful`.

**¿Qué separa cada capa?**
Presentación (`presentacion`) habla HTTP y JSON: traduce el pedido, delega y arma la
respuesta; no valida reglas. Negocio (`negocio`) tiene las reglas del dominio y es el único
que decide si un turno es válido; no sabe que existe HTTP. Datos (`datos`) sabe cómo se
guardan y se buscan los turnos; no conoce las reglas. La prueba de que la separación es real
es que cada capa se puede reemplazar sin tocar las otras dos.

**¿Dónde cambiarían para usar una base de datos?**
En un solo lugar: se escribe una clase nueva, `RepositorioTurnosJpa`, que implemente la
interfaz `RepositorioTurnos` usando `EntityManager`, se anota `Turno` con `@Entity` y se
agregan el `persistence.xml` y el datasource en WildFly. `GestionTurnos` y `RecursoTurnos` no
cambian ni una línea, porque el negocio depende de la interfaz y no de la implementación.

**¿Por qué eligieron un EJB `@Stateless` y no un bean CDI común?**
Porque el contenedor le agrega gratis dos cosas que el TP va a necesitar: el pool de
instancias y, sobre todo, las **transacciones declarativas** (cada método público corre en su
propia transacción, `REQUIRED` por defecto). En la Entrega 4 eso ya está resuelto sin
reescribir la clase.

**¿Qué patrón de diseño aplicaron?**
DAO / Repository: `RepositorioTurnos` es la interfaz que aísla el acceso a datos de la lógica
de negocio. Se eligió porque el enunciado nos obliga a poder cambiar de almacenamiento en la
próxima entrega. Los otros dos patrones exigidos (Facade y Strategy o Factory) se aplican en
la Entrega 2, cuando haya varios componentes que coordinar.

**¿Por qué la dependencia de Jakarta EE está en scope `provided`?**
Porque la implementación la aporta WildFly (RESTEasy, Weld, el contenedor de EJB). Si la
empaquetáramos dentro del WAR habría clases duplicadas en el classpath y el despliegue
fallaría.

**¿Por qué las excepciones de negocio llevan `@ApplicationException`?**
Porque sin esa anotación el contenedor de EJB trata cualquier `RuntimeException` como un
fallo *de sistema*: la envuelve en `EJBException`, escribe un stack trace completo en el log,
marca la transacción para rollback y descarta la instancia del pool. Pero que alguien pida un
turno en el pasado no es una falla del sistema, es un resultado de negocio previsto. Con
`@ApplicationException(rollback = false)` el contenedor la deja pasar tal cual: sin ruido en
el log y, lo que importa de cara a la Entrega 4, sin rollback de la transacción.

**¿Por qué no hay `web.xml`?**
Porque no hace falta: la clase `AplicacionRest` con `@ApplicationPath("/api")` es la que
activa y configura JAX-RS. Un `web.xml` recién sería necesario para configurar seguridad
declarativa, filtros o páginas de error, cosa que llega en la Entrega 2.

**¿Qué pasa si reinician WildFly?**
Se pierden los turnos creados y vuelven a cargarse los 3 de ejemplo, porque el
almacenamiento es en memoria. Es una limitación conocida y aceptada de esta entrega: se
decidió no depender de una base de datos externa para que el despliegue no se bloqueara.

## 9. Limitaciones conocidas de esta entrega

- Los datos viven en memoria: se pierden al reiniciar o redesplegar.
- No hay autenticación ni autorización todavía (llega en la Entrega 2).
- Sólo se implementa un componente de los seis identificados en `docs/arquitectura.md`.
- No hay operaciones de modificación ni cancelación de turnos: el alcance de la Entrega 1 es
  listar, consultar y crear.
