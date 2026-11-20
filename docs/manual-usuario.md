# MediConecta — Manual de usuario

Guía breve de uso de MediConecta por rol. Para el detalle técnico de cada operación ver
[`casos-de-uso.md`](casos-de-uso.md); para capturas reales de las pantallas, ver
`docs/presentacion/capturas/`.

## Ingresar al sistema

1. Entrar a `http://localhost:8080/mediconecta/` (o la URL que indique quien administra el
   servidor).
2. Si no hay sesión abierta, el sistema redirige solo a la pantalla de login.
3. Ingresar usuario y contraseña. Las credenciales de demostración están en el `README.md`,
   sección "Usuarios de demostración" (un usuario por rol: PACIENTE, RECEPCION, MEDICO,
   ADMIN).
4. Al confirmar, MediConecta muestra el tablero de inicio adaptado al rol de ese usuario.

Para salir, usar la opción de cerrar sesión del menú lateral: invalida la sesión en el
servidor, no solo borra datos en el navegador.

---

## Rol PACIENTE

Un paciente ve únicamente su propia información.

**Tablero de inicio.** Resumen personal: próximos turnos y accesos rápidos.

**Mis turnos.** Pantalla exclusiva de este rol: lista los turnos propios, pasados y futuros,
con su estado (programado, cancelado, atendido). Desde acá se puede cancelar un turno propio
todavía vigente.

**Reservar un turno.**
1. Ir a "Reservar turno".
2. Elegir especialidad.
3. Elegir profesional (el sistema solo ofrece los de esa especialidad).
4. Elegir fecha y horario (solo se muestran los horarios realmente libres de ese
   profesional).
5. Confirmar. El turno queda `PROGRAMADO` y el sistema dispara, en segundo plano, el aviso de
   confirmación (no requiere ninguna acción adicional del paciente).

Un paciente **no puede** reservar ni cancelar turnos de otra persona, ni ver la agenda
completa de la clínica, ni el padrón de pacientes: el sistema lo impide aunque se intente
forzar la URL.

---

## Rol RECEPCION

Administra la operación diaria de la clínica.

**Tablero de inicio.** Métricas del día: turnos de hoy, pacientes y profesionales activos,
próximos turnos pendientes.

**Agenda (listado de turnos).** Vista completa de todos los turnos, con buscador y filtros.
Desde el detalle de un turno se puede cancelarlo o, si ya está cancelado, archivarlo (lo saca
de la vista operativa sin borrar el registro).

**Padrón de pacientes.** Alta de pacientes nuevos, búsqueda, edición de datos de contacto y
cobertura, y consulta del historial de turnos de cualquier paciente.

**Profesionales.** Consulta del equipo médico y su disponibilidad. RECEPCION puede definir la
disponibilidad semanal de cualquier profesional (dar de alta un profesional nuevo requiere rol
ADMIN).

**Reservar un turno para un paciente.** Mismo asistente de 4 pasos que usa el rol PACIENTE,
con la diferencia de que RECEPCION elige primero para qué paciente es la reserva.

---

## Rol MEDICO

Ve y administra su propia agenda.

**Agenda.** Ve la agenda completa de la clínica (de solo lectura para turnos de otros
profesionales) y puede cancelar únicamente los turnos de su propia agenda.

**Disponibilidad.** Puede definir su propia disponibilidad semanal (franjas horarias); no
puede definir la de otro profesional.

Un MEDICO no puede reservar turnos directamente ni dar de alta pacientes o profesionales.

---

## Rol ADMIN

Tiene acceso a todo lo anterior (agenda completa, padrón, profesionales) más:

**Alta de profesionales.** Es el único rol habilitado para registrar un profesional nuevo en
el sistema (nombre, especialidad, matrícula).

**Archivado de turnos.** Igual que RECEPCION, puede archivar turnos cancelados.

---

## Validar la cobertura de un paciente (nuevo, Entrega 2)

Esta operación valida contra la obra social del paciente si su cobertura está vigente, qué
plan tiene y el copago correspondiente, consultando automáticamente el sistema que
corresponda (ver [`Entrega_Obligatoria_2.md`](Entrega_Obligatoria_2.md), §4). **Por ahora está
disponible solo por API** (`GET /api/pacientes/{documento}/cobertura`), todavía no tiene una
pantalla propia en el frontend; queda identificado como trabajo pendiente (ver
`Entrega_Obligatoria_2.md`, §8).

---

## Qué hacer si algo falla

- **"Su rol no tiene permiso..." (403).** La operación que se intentó no está habilitada para
  ese rol. No es un error del sistema: es la seguridad por roles funcionando.
- **Un mensaje de error en rojo al reservar o cancelar.** Es una regla de negocio incumplida
  (por ejemplo, un horario ya tomado, un turno ya cancelado): el mensaje explica cuál.
- **"El sistema externo no respondió" al consultar cobertura.** El sistema legado o el
  partner de obras sociales no están disponibles en ese momento; reintentar más tarde.
