# MediConecta — Manual de usuario (v1)

MediConecta es el sistema de turnos de la clínica. Este manual explica qué puede hacer cada
tipo de usuario y cómo. No hace falta instalar nada: se usa desde el navegador.

**Dirección:** <http://localhost:8080/mediconecta/> (o la que indique el administrador).

---

## 1. Ingresar

1. Abrir la dirección de MediConecta. Si no hay sesión, aparece la pantalla de ingreso.
2. Escribir el **usuario** y la **contraseña** y apretar **Ingresar**.
3. La sesión dura 2 horas. Al vencer, el sistema vuelve solo a la pantalla de ingreso.

> **Seguridad.** Después de 5 intentos fallidos seguidos, el usuario queda bloqueado unos
> minutos ("Demasiados intentos fallidos…"). Es una protección contra quien intenta
> adivinar contraseñas: esperar y volver a intentar.

Usuarios de demostración (contraseña de la defensa: `Demo.2026`):

| Usuario | Rol | Para qué sirve |
|---|---|---|
| `paciente` | Paciente (Ana Gomez) | Sacar y cancelar sus propios turnos |
| `recepcion` | Recepción | Agenda completa, pacientes, turnos de cualquier paciente |
| `medico` | Médico (Dra. Laura Fernandez) | Su agenda, registrar atenciones, su disponibilidad |
| `admin` | Administrador | Todo lo anterior, alta de profesionales y monitoreo de la mensajería |

Para salir: botón **Salir** de la barra superior.

## 2. La pantalla de inicio

Muestra un resumen según el rol: para recepción y médicos, los turnos de hoy, los
pendientes y los próximos turnos; para el paciente, accesos directos a sus turnos y a la
reserva. El menú lateral (o la barra inferior en el celular) lleva a cada sección.

## 3. Paciente

### 3.1 Sacar un turno con la reserva guiada

**Reserva** en el menú. Son cuatro pasos; el sistema recuerda lo elegido aunque se recargue
la página.

1. **Especialidad.** Elegirla de la lista, o tocar **Prefiero escribirlo** y contar con
   palabras propias qué pasa (por ejemplo, "me caí y me duele la rodilla"). El asistente
   sugiere una especialidad; se acepta con un toque o se elige otra de la lista.
   - El asistente **no diagnostica ni receta**: solo orienta.
   - Si lo que se cuenta parece una **emergencia** (dolor fuerte en el pecho, falta de aire,
     desmayo…), indica llamar al **107 (SAME) o al 911** o ir a la guardia.
   - Si el asistente no está disponible, igual da una orientación básica o pide elegir de
     la lista.
2. **Profesional** de esa especialidad.
3. **Día y horario**: se muestran solo los horarios libres.
4. **Confirmar.** El turno queda reservado y se registran los avisos: confirmación por
   mail, recordatorio por SMS 24 h antes y aviso al profesional.

**Volver** retrocede un paso; **Cancelar** abandona la reserva sin crear nada.

### 3.2 Mis turnos

**Mis turnos** muestra los turnos propios con su estado: *Programado*, *Atendido* o
*Cancelado*. **Ver detalle** abre el turno con los **avisos enviados**.
Un turno programado se puede **cancelar** desde el detalle; el horario vuelve a quedar libre.

## 4. Recepción

- **Turnos:** la agenda completa. **Nuevo turno** permite elegir paciente, profesional,
  fecha y horario. Si por un corte de conexión se aprieta dos veces **Confirmar**, no se
  crean dos turnos.
- **Detalle de un turno:** cancelar, o **Eliminar de agenda** (archivar) un turno ya
  cancelado. Archivar no borra: el turno sigue en el historial del paciente.
- **Pacientes:** buscar por nombre o documento, registrar uno nuevo, editar sus datos y ver
  su historial y su **cobertura** (obra social). Si el sistema de la obra social no
  responde, se muestra el último dato verificado o "a verificar en la atención".
- **Profesionales:** ver profesionales y editar la disponibilidad semanal de cualquiera.
- **Reserva:** la misma reserva guiada del paciente, eligiendo para quién es.

> Si dos personas editan el mismo turno o la misma ficha a la vez, la segunda recibe un
> aviso de que el dato cambió: hay que volver a cargarlo y repetir el cambio.

## 5. Médico

- **Turnos:** la agenda. En el detalle de un turno **de su propia agenda**:
  **Marcar atendido** (solo si está programado) o **Cancelar**.
- **Profesionales → Disponibilidad** (de su propia ficha): definir los días, horarios y la
  duración de cada turno. Solo puede modificar la propia.

## 6. Administrador

Todo lo de recepción, más:

- **Profesionales → Nuevo profesional:** alta con nombre, especialidad y matrícula.
- **API (OpenAPI)** en el menú: documentación interactiva de la API.
- **Mensajería:** consulta de los mensajes que no se pudieron procesar
  (`GET /api/mensajeria/mensajes-fallidos`), para avisar al equipo técnico.

## 7. Mensajes frecuentes

| Mensaje | Qué significa | Qué hacer |
|---|---|---|
| "Su sesión venció" | Pasaron 2 horas o se reinició el servidor | Volver a ingresar |
| "Su rol no tiene permiso…" | La acción no corresponde al rol | Pedirla a recepción o al administrador |
| "El profesional ya tiene un turno en ese horario" | Otro usuario tomó ese horario | Elegir otro horario |
| "No se puede agendar un turno en el pasado" | La fecha ya pasó | Elegir una fecha futura |
| "El turno ya estaba cancelado" | El turno cambió de estado antes | Recargar el detalle |
| "El turno cambió desde que lo consultó" | Otro usuario lo modificó al mismo tiempo | Recargar y repetir |
| "Hiciste muchas consultas seguidas al asistente" | Límite de 10 consultas por minuto | Esperar un momento o elegir de la lista |
| "Demasiados intentos fallidos de inicio de sesión" | Bloqueo temporal por seguridad | Esperar unos minutos |

## 8. Ayuda técnica

Para el equipo técnico: instalación en el [README](../README.md), API en
`/mediconecta/api-docs.html` y estado del sistema en `/mediconecta/api/salud`
(`"estado": "UP"` si la base y la mensajería responden).
