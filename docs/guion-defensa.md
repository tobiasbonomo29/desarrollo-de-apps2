# Guion de la defensa — MediConecta, Entrega 1

**Duración prevista:** 15 a 20 minutos.
**Regla de oro:** mostrar primero que funciona y después explicar por qué está hecho así.
Cada punto tiene entre paréntesis el tiempo aproximado.

---

## Antes de compartir pantalla (0 min, preparación)

Esto se hace **antes** de empezar, no delante del docente.

1. PostgreSQL arriba:
   ```powershell
   .\scripts\db\levantar-postgres.ps1
   ```
2. Dos ventanas listas:
   - una terminal en la raíz del proyecto;
   - una terminal con el **log de WildFly a la vista** (es la que se usa para el punto 9).
3. Navegador con las pestañas precargadas:
   - `http://localhost:8080/mediconecta/`
   - `http://localhost:8080/mediconecta/api/salud`
4. El IDE abierto en `AsistenteReservaTurno.java` y en `web.xml`.
5. Base en estado inicial, si se hicieron pruebas antes:
   ```powershell
   docker exec mediconecta-postgres psql -U mediconecta -d mediconecta -c "TRUNCATE turnos, disponibilidades, profesionales, pacientes RESTART IDENTITY CASCADE;"
   ```
   y redesplegar para que se recargue el juego de datos.

---

## 1. Compilar y levantar el contenedor (2 min)

```powershell
mvn clean package
```

Mostrar que aparece `target\mediconecta.war` y señalar que pesa unos 120 kB: la API de
Jakarta EE está en scope `provided` porque la aporta el servidor.

Levantar WildFly:

```
C:\wildfly-36.0.1.Final\bin\standalone.bat
```

Esperar `WildFly ... started in ...ms`.

---

## 2. Desplegar (1 min)

```powershell
copy target\mediconecta.war C:\wildfly-36.0.1.Final\standalone\deployments\
```

En el log señalar **dos** líneas:

```
WFLYSRV0010: Deployed "mediconecta.war"
[DEMO] Juego de datos de ejemplo cargado: 4 profesionales, 4 pacientes, 3 turnos.
```

> La segunda es un `@Singleton @Startup`: el contenedor lo instancia solo al desplegar. Es
> la primera aparición del ciclo de vida administrado.

Abrir <http://localhost:8080/mediconecta/api/salud> → **¡Hola desde el contenedor!**

> Aclarar: es el único endpoint público del sistema, declarado así en `web.xml` con una
> `security-constraint` sin `auth-constraint`.

---

## 3. Login y seguridad por roles (3 min)

Abrir <http://localhost:8080/mediconecta/>.

> El contenedor intercepta: la pantalla de inicio está protegida, así que antes de dejarnos
> pasar muestra el login. **No es una pantalla nuestra que decide**: es el mecanismo FORM de
> Jakarta EE.

Entrar como **`paciente` / `Demo.2026`**.

- Señalar el menú: **no** tiene Turnos ni Pacientes.
- Mostrar "Mis turnos" y "Mis datos".

> Aclarar en voz alta: **ocultar un ítem del menú no es seguridad**. Es comodidad. Ahora lo
> probamos.

Sin cerrar sesión, desde otra terminal:

```powershell
curl.exe -i -H "Accept: application/json" http://localhost:8080/mediconecta/api/turnos
```

→ **401**. Sin sesión no se pasa.

Cerrar sesión y entrar como **`recepcion` / `Demo.2026`**. Menú completo.

Intentar dar de alta un profesional con ese rol (se puede hacer desde el script de pruebas o
con `curl`) → **403**:

```json
{ "error": "Su rol no tiene permiso para realizar esta operacion.", "codigo": 403 }
```

> Mostrar en el log el `EJBAccessException`: **quien rechazó fue el contenedor**, al evaluar
> la anotación `@RolesAllowed` del método de negocio. La aplicación no escribió ningún `if`.

Abrir `GestionProfesionales.java` y mostrar:

```java
@RolesAllowed(Roles.ADMIN)
public Profesional registrarProfesional(Profesional profesional) { ... }
```

Entrar como **`admin`** y hacer el alta desde la interfaz → funciona.

---

## 4. Tablero (1 min)

Como `recepcion`, mostrar la pantalla de inicio.

> Los cuatro números salen de la base, no están escritos a mano. Y salen de **una sola
> llamada**: `GET /api/tablero`, que resuelve `FachadaReservas.resumenDelDia()` consultando
> a los tres componentes stateless. Sin la fachada, esta pantalla necesitaría cuatro
> requests y la capa de presentación tendría que saber a quién preguntarle cada cosa.

---

## 5. Pacientes (1,5 min)

- Buscador: escribir un nombre. El filtro lo resuelve el servidor
  (`GET /api/pacientes?q=`), porque buscar es una consulta de la capa de datos.
- **Registrar un paciente** nuevo.
- Volver a intentarlo con el **mismo documento** → error inline: *"Ya existe un paciente con
  documento …"*.

> Esa regla vive en `GestionPacientes`, no en el navegador ni en el recurso REST. La
> presentación sólo muestra el mensaje que el negocio le devolvió.

- Entrar a una ficha y mostrar el **historial de turnos**.

---

## 6. Profesionales y disponibilidad (1,5 min)

- Mostrar las tarjetas con especialidad, matrícula y agenda semanal.
- Filtrar por especialidad.
- Como `admin` o `medico`, abrir **Disponibilidad** de un profesional y cambiar una franja.

> Esta pantalla es la que alimenta los horarios libres. En el paso siguiente se ve el efecto.

---

## 7. Alta de turno y reglas de negocio (2,5 min)

Ir a **Nuevo turno**.

> Los tres selectores traen datos reales: el paciente sale de Gestión de Pacientes, la
> especialidad y el profesional de Gestión de Profesionales, y **los horarios salen de la
> disponibilidad del profesional menos los turnos que ya tiene tomados**.

- Elegir paciente, especialidad, profesional y fecha → aparece la grilla de horarios.
- Elegir uno → mensaje verde *"Horario disponible"* y el resumen de la derecha se completa.
- **Confirmar** → lleva al detalle del turno, en estado `PROGRAMADO`.

Ahora mostrar las reglas fallando, con `curl` (es más contundente que la interfaz porque
prueba que la validación es del servidor):

```powershell
# Turno en el pasado
curl.exe -i -X POST http://localhost:8080/mediconecta/api/turnos -H "Content-Type: application/json" -d "{\"documentoPaciente\":\"30111222\",\"profesionalId\":1,\"fechaHora\":\"2020-01-06T10:00\"}"
```
→ **400** *"No se puede agendar un turno en el pasado."*

```powershell
# Mismo profesional, mismo horario que el turno recién creado
```
→ **400** *"El profesional … ya tiene un turno en ese horario."*

> Las dos reglas están en `GestionTurnos.agendar`. El recurso REST no tiene ni un `if`.

---

## 8. Cancelación (1 min)

En el detalle del turno recién creado, pulsar **Cancelar turno**.

- El estado pasa a `CANCELADO`.
- Volver a **Nuevo turno** y elegir el mismo profesional y la misma fecha: **el horario
  volvió a aparecer libre**.

> Es una decisión de dominio explícita: `existeTurnoVigente` ignora los turnos cancelados.

Intentar cancelarlo de nuevo → **400** *"El turno N ya estaba cancelado."*

Como `paciente`, intentar cancelar → **403**.

---

## 9. El componente stateful (4 min — el punto más importante)

Ir a **Reserva** con el usuario `paciente`.

Antes de tocar nada, mostrar el log de WildFly:

```
[STATEFUL] @PostConstruct - el contenedor creo el asistente a3d3c55b (instancia 6cb82941)
```

> El contenedor creó la instancia. Nosotros no hicimos `new`.

Recorrer los pasos, **narrando lo que viaja en cada request**:

1. **Paciente** → el servidor guarda a quién le estamos reservando.
2. **Especialidad**: elegir Cardiología.
   ```
   [STATEFUL] conversacion a3d3c55b recuerda especialidad=Cardiologia
   ```
3. **Profesional**: elegir uno.
   > Abrir las herramientas de desarrollo del navegador y mostrar el cuerpo del request:
   > **`{"profesionalId": 1}` y nada más**. No lleva el paciente ni la especialidad. Y sin
   > embargo el resumen de la derecha los sigue mostrando: **los recordó el servidor**.
4. Señalar la nota del pie del resumen:
   *Conversación `a3d3c55b` · instancia del EJB `6cb82941` · paso `ELEGIR_HORARIO`*.
5. Pulsar **Atrás**.
   > Incluso "volver atrás" es una operación del servidor (`PUT /api/reserva/volver`): el
   > navegador no reconstruye nada, sólo vuelve a preguntar en qué paso quedó.
6. **Horario**: elegir uno.
7. **Confirmar**.

En el log aparece la secuencia completa:

```
[STATEFUL] conversacion a3d3c55b confirmada: turno 16
[STATEFUL] @Remove - fin de la conversacion a3d3c55b en el paso 5
[STATEFUL] @PreDestroy - el contenedor descarta el asistente a3d3c55b (instancia 6cb82941)
[STATEFUL] @PostConstruct - el contenedor creo el asistente 91f2c4de (instancia 2d81a007)
```

> Tres cosas para señalar:
> - `@Remove` es la aplicación diciéndole al contenedor que la conversación terminó;
> - `@PreDestroy` es el contenedor descartando la instancia;
> - el **identificador de instancia cambió**: la próxima reserva usa un objeto distinto.

Cerrar el punto abriendo `AsistenteReservaTurno.java` y contrastando:

> Este componente tiene campos que representan una conversación en curso. `GestionTurnos`
> no tiene **ninguno**: ese es exactamente el criterio que separa stateful de stateless.

Y mostrar `ConversacionDeReserva.java`:

> Un EJB stateful no se ata solo a una sesión web. Este bean CDI `@SessionScoped` es el
> puente: guarda la referencia al asistente y CDI lo asocia a la cookie `JSESSIONID`.

---

## 10. Capas y patrones en el código (2 min)

Recorrer el camino de un request de alta, en orden:

```
RecursoTurnos  →  FachadaReservas  →  GestionTurnos  →  RepositorioTurnos (interfaz)
                                                              ↓
                                                    RepositorioTurnosJpa
```

Tres cosas para señalar:

1. **`GestionTurnos` inyecta la interfaz, no la implementación.** Ese es el patrón DAO. Por
   eso migrar de memoria a PostgreSQL no cambió una línea del negocio; las
   implementaciones en memoria siguen en el proyecto, anotadas `@Alternative`.
2. **`FachadaReservas` es el Facade.** Tiene dos clientes —el alta directa y el asistente
   stateful— que harían la misma secuencia de tres componentes. Sin la fachada, esa
   coreografía estaría duplicada.
3. **`FabricaTurnos` es el Factory.** Concentra las invariantes de un turno bien formado.
   Contar el caso real: cuando la fecha era texto, una cadena con segundos y otra sin ellos
   representaban el mismo instante pero no coincidían, y la regla de superposición dejaba de
   detectar choques. Hoy hay tres caminos que crean turnos y los tres pasan por la fábrica.

Abrir `persistence.xml` y señalar que el datasource se llama por su nombre JNDI: **el usuario
y la contraseña de PostgreSQL están en WildFly, no en el repositorio**.

---

## 11. Cierre: persistencia real (1 min)

Si queda tiempo, el remate más contundente:

- Reiniciar WildFly.
- Volver a entrar: **los turnos siguen ahí**.

> En la versión anterior los datos estaban en memoria y se perdían al reiniciar. Ahora están
> en PostgreSQL. Y el juego de datos de ejemplo no se volvió a cargar, porque la carga es
> idempotente.

Cerrar contando qué sigue: `Notificaciones` con JMS en la Entrega 3 e
`IntegracionObrasSociales` con SOAP y REST, aplicando el patrón Adapter, en la Entrega 4.

---

## Plan B si algo falla

| Si falla | Qué hacer |
|---|---|
| PostgreSQL no levanta | Activar las alternativas en `beans.xml`, borrar `persistence.xml`, recompilar. Está documentado en el README, sección 8. Contarlo como demostración del patrón DAO en lugar de esconderlo. |
| El despliegue falla | Mirar `standalone\deployments\mediconecta.war.failed`: el archivo dice el motivo. |
| La interfaz no carga | `GET /api/salud` y el script `smoke-test.ps1` prueban toda la lógica sin navegador. |
| Se acaba el tiempo | Los puntos irrenunciables son el **9** (stateful), el **3** (seguridad) y el **10** (capas y patrones). |
