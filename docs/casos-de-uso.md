# MediConecta — Casos de uso

Catálogo de los casos de uso implementados, con su actor principal, flujo normal y flujos
alternativos. No es un documento aparte de la arquitectura: cada caso de uso referencia el
endpoint y el componente de negocio que lo resuelve, para que se pueda ir del caso de uso al
código y viceversa.

Actores: **PACIENTE**, **RECEPCION**, **MEDICO**, **ADMIN** (ver matriz de permisos completa
en [`arquitectura.md`](arquitectura.md), §5.3).

---

## CU-01 — Reservar un turno (alta directa)

- **Actor:** PACIENTE (para sí mismo), RECEPCION, ADMIN.
- **Endpoint:** `POST /api/turnos`
- **Componente:** `FachadaReservas.reservar` (Facade) → `GestionTurnos.agendar`

**Flujo normal**
1. El actor envía documento del paciente, profesional, especialidad, fecha/hora y motivo.
2. El sistema resuelve el paciente (si no existe y se envió nombre, lo da de alta en el
   mismo paso) y el profesional.
3. El sistema valida que la fecha no esté en el pasado y que el profesional no tenga ya un
   turno vigente en ese horario.
4. El sistema crea el turno en estado `PROGRAMADO` y envía el aviso de confirmación y el
   recordatorio por JMS (ver [`Entrega_Obligatoria_2.md`](Entrega_Obligatoria_2.md), §3).
5. El sistema devuelve el turno creado con `201 Created`.

**Flujos alternativos**
- *A1 — Paciente nuevo sin nombre:* si el documento no existe en el padrón y no se envió
  nombre, el sistema rechaza con `400` pidiendo registrar al paciente primero.
- *A2 — Horario ocupado:* si el profesional ya tiene un turno vigente en ese momento, `400`.
- *A3 — Fecha pasada:* `400`.
- *A4 — Un PACIENTE intenta reservar para otro documento:* `403`, por la comprobación de
  ownership de `GestionPacientes.exigirPropioDocumentoSiEsPaciente`.

---

## CU-02 — Reservar un turno (asistente guiado)

- **Actor:** PACIENTE (para sí mismo), RECEPCION, ADMIN.
- **Endpoints:** `POST /api/reserva/iniciar`, `PUT /api/reserva/especialidad`,
  `PUT /api/reserva/profesional`, `PUT /api/reserva/horario`, `POST /api/reserva/confirmar`
- **Componente:** `AsistenteReservaTurno` (EJB `@Stateful`) → `FachadaReservas.reservar`

**Flujo normal**
1. El actor inicia la conversación con el documento del paciente.
2. En pasos sucesivos elige especialidad, profesional y horario; el servidor recuerda lo
   elegido entre un paso y el siguiente (estado de conversación atado a la sesión HTTP, ver
   `arquitectura.md` §3.3–3.4).
3. Al confirmar, el asistente delega en `FachadaReservas.reservar` — el mismo camino que
   CU-01 — y cierra la conversación.

**Flujos alternativos**
- *A1 — Abandono:* si el usuario no confirma, `AsistenteReservaTurno` se descarta solo a los
  30 minutos (`@StatefulTimeout`).
- *A2 — Retroceder un paso:* `PUT /api/reserva/volver` permite corregir una elección anterior
  sin perder las demás.
- *A3 — Cancelar la reserva en curso:* `DELETE /api/reserva`.

---

## CU-03 — Cancelar un turno

- **Actor:** PACIENTE (solo el propio), RECEPCION, MEDICO (solo de su agenda), ADMIN.
- **Endpoint:** `PUT /api/turnos/{id}/cancelar`
- **Componente:** `GestionTurnos.cancelarTurno`

**Flujo normal**
1. El actor pide cancelar un turno `PROGRAMADO`.
2. El sistema verifica ownership según el rol (ver `arquitectura.md` §5.4) y marca el turno
   como `CANCELADO`, liberando el horario.

**Flujos alternativos**
- *A1 — Turno ya cancelado o ya atendido:* `400`.
- *A2 — Sin ownership:* `403`.

---

## CU-04 — Archivar un turno cancelado

- **Actor:** RECEPCION, ADMIN.
- **Endpoint:** `PUT /api/turnos/{id}/archivar`
- **Componente:** `GestionTurnos.archivarTurno`

**Flujo normal**
1. El actor archiva un turno que ya está `CANCELADO`.
2. El turno deja de listarse en la agenda operativa, sin borrarse: sigue disponible en el
   historial del paciente y por consulta directa por id.

**Flujos alternativos**
- *A1 — Turno no cancelado:* `400`.
- *A2 — Ya archivado:* `400`.

---

## CU-05 — Consultar historial de turnos de un paciente

- **Actor:** PACIENTE (solo el propio), RECEPCION, MEDICO, ADMIN.
- **Endpoint:** `GET /api/pacientes/{documento}/turnos`
- **Componente:** `GestionPacientes.historialDeTurnos`

**Flujo normal**
1. El actor pide el historial de un documento.
2. Si el actor es PACIENTE, el sistema comprueba que el documento sea el de su propia ficha
   (autorización programática sobre la declarativa, `arquitectura.md` §5.4); si es otro rol,
   no hay restricción adicional.

**Flujos alternativos**
- *A1 — Paciente consultando el historial de otro:* `403`.

---

## CU-06 — Registrar y gestionar profesionales y su disponibilidad

- **Actor:** ADMIN (alta), el propio MEDICO o RECEPCION/ADMIN (disponibilidad).
- **Endpoints:** `POST /api/profesionales`, `PUT /api/profesionales/{id}/disponibilidad`,
  `GET /api/profesionales/{id}/horarios-libres`
- **Componente:** `GestionProfesionales`

**Flujo normal**
1. ADMIN da de alta un profesional con su especialidad y matrícula.
2. El propio profesional (o RECEPCION/ADMIN) define sus franjas horarias semanales.
3. Cualquier rol puede consultar los horarios libres resultantes, cruzando disponibilidad
   declarada contra los turnos ya ocupados (`GestionTurnos.horariosOcupados`).

---

## CU-07 — Validar la cobertura de un paciente (obra social)

- **Actor:** PACIENTE (solo el propio), RECEPCION, MEDICO, ADMIN.
- **Endpoint:** `GET /api/pacientes/{documento}/cobertura`
- **Componente:** `GestorCobertura` (patrón Adapter) →
  `AdaptadorObraSocialLegado` (SOAP) o `AdaptadorObraSocialPartner` (REST)

**Flujo normal**
1. El actor pide la cobertura de un paciente.
2. El sistema busca al paciente y aplica la misma comprobación de ownership que CU-05.
3. Según la obra social del paciente, el sistema consulta el sistema legado por SOAP o el
   partner moderno por REST (ver [`Entrega_Obligatoria_2.md`](Entrega_Obligatoria_2.md), §4)
   y devuelve si está vigente, el plan y el copago.

**Flujos alternativos**
- *A1 — Paciente sin obra social cargada:* el sistema responde `vigente: false`,
  `origen: "NINGUNA"`, sin consultar a nadie.
- *A2 — El sistema externo no responde:* `502 Bad Gateway`
  (`IntegracionExternaException`).

---

## CU-08 — Ver el tablero de la clínica

- **Actor:** RECEPCION, MEDICO, ADMIN.
- **Endpoint:** `GET /api/tablero`
- **Componente:** `FachadaReservas.resumenDelDia`

**Flujo normal**
1. El actor abre la pantalla de inicio.
2. El sistema resuelve, en una sola llamada, turnos de hoy, pacientes y profesionales
   activos, y los próximos turnos pendientes.

---

## CU-09 — Recibir avisos de confirmación y recordatorio (asincrónico)

- **Actor:** el propio sistema (no hay un actor humano que lo dispare directamente: es
  consecuencia de CU-01/CU-02).
- **Componentes:** `NotificadorTurnos` (productor) → `ReceptorConfirmacion` (cola),
  `SuscriptorAvisoPaciente` + `SuscriptorAvisoProfesional` (tópico)

**Flujo normal**
1. Al confirmarse un turno (CU-01 o CU-02), el sistema envía un mensaje a la cola de
   confirmaciones y publica un mensaje en el tópico de recordatorios, dentro de la misma
   transacción que crea el turno.
2. `ReceptorConfirmacion` procesa la confirmación una única vez.
3. `SuscriptorAvisoPaciente` y `SuscriptorAvisoProfesional` procesan, cada uno por su lado,
   su propia copia del recordatorio.

**Flujos alternativos**
- *A1 — Falla el envío o el broker no está disponible:* la transacción completa se deshace;
  el turno tampoco queda creado (ver `Entrega_Obligatoria_2.md`, §5).
