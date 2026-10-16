# La API REST de MediConecta

Base: `/api` (WAR `mediconecta.war`, servidor WildFly). Todas las respuestas son JSON.
Autenticación FORM a cargo del contenedor; autorización por rol (`PACIENTE`, `RECEPCION`,
`MEDICO`, `ADMIN`) declarada con `@RolesAllowed` sobre el negocio.

## 1. Sesión — quién soy
| Método | Recurso | Para qué sirve |
|---|---|---|
| GET | `/api/sesion` | Devuelve usuario autenticado y roles, para que el frontend decida qué mostrar |
| POST | `/api/sesion/logout` | Cierra la sesión del contenedor |

## 2. Turnos — la agenda
| Método | Recurso | Para qué sirve |
|---|---|---|
| GET | `/api/turnos` | Lista todos los turnos |
| GET | `/api/turnos/{id}` | Trae un turno puntual |
| POST | `/api/turnos` | Da de alta un turno directo (recepción/admin) |
| PUT | `/api/turnos/{id}/cancelar` | Cancela un turno vigente |
| PUT | `/api/turnos/{id}/archivar` | Saca un turno cancelado de la vista operativa, sin borrarlo |

## 3. Pacientes — el padrón
| Método | Recurso | Para qué sirve |
|---|---|---|
| GET | `/api/pacientes` | Lista el padrón completo |
| GET | `/api/pacientes/mi-ficha` | El propio paciente ve su ficha |
| GET | `/api/pacientes/{documento}` | Ficha de un paciente puntual |
| POST | `/api/pacientes` | Registra un paciente nuevo |
| PUT | `/api/pacientes/{documento}` | Actualiza datos de contacto/cobertura |
| GET | `/api/pacientes/{documento}/turnos` | Historial de turnos (un paciente solo ve el propio) |

## 4. Profesionales — quién atiende
| Método | Recurso | Para qué sirve |
|---|---|---|
| GET | `/api/profesionales` | Lista profesionales |
| GET | `/api/profesionales/especialidades` | Especialidades disponibles, para filtrar |
| GET | `/api/profesionales/mi-perfil` | El profesional ve su propia ficha |
| GET | `/api/profesionales/{id}` | Ficha de un profesional |
| POST | `/api/profesionales` | Registra un profesional (solo ADMIN) |
| PUT | `/api/profesionales/{id}/disponibilidad` | Define las franjas horarias en las que atiende |
| GET | `/api/profesionales/{id}/horarios-libres` | Horarios disponibles para agendar |

## 5. Reserva guiada — EJB `@Stateful`
Una sola operación de negocio repartida en varios pasos; el servidor recuerda la
conversación del usuario entre llamada y llamada (atada a `JSESSIONID`).

| Método | Recurso | Para qué sirve |
|---|---|---|
| GET | `/api/reserva` | Estado actual del asistente (en qué paso está) |
| POST | `/api/reserva/iniciar` | Arranca la reserva con el documento del paciente |
| PUT | `/api/reserva/especialidad` | Elige especialidad |
| PUT | `/api/reserva/profesional` | Elige profesional |
| GET | `/api/reserva/horarios` | Horarios libres del profesional elegido |
| PUT | `/api/reserva/horario` | Elige fecha y hora |
| PUT | `/api/reserva/volver` | Retrocede un paso |
| POST | `/api/reserva/confirmar` | Confirma y crea el turno |
| DELETE | `/api/reserva` | Cancela la reserva en curso |

## 6. Tablero y salud
| Método | Recurso | Para qué sirve |
|---|---|---|
| GET | `/api/tablero` | Resumen del día (próximos turnos, ocupación) |
| GET | `/api/salud` | Prueba de vida del servicio, sin autenticación |

---

## 7. Videollamadas (propuesta, integración de fantasía)

Para que el turno se pueda atender a distancia, MediConecta consumiría la API de una
plataforma externa de videollamadas (p. ej. tipo Zoom/Meet) a través de un adapter REST,
igual que el que la Entrega 4 prevé para obras sociales.

| Método | Recurso | Para qué sirve |
|---|---|---|
| POST | `/api/turnos/{id}/videollamada` | Crea la sala de videollamada en la plataforma externa y guarda el link contra el turno |
| GET | `/api/turnos/{id}/videollamada` | Devuelve el link de la sala para compartirlo con paciente y profesional |
| DELETE | `/api/turnos/{id}/videollamada` | Cierra la sala cuando el turno se cancela o finaliza |

Flujo: al confirmar un turno marcado como "a distancia", `GestionTurnos` le pide a un
`AdaptadorVideollamadas` que cree la sala en la plataforma externa; esta devuelve un
`salaId` y una URL pública. Esa URL se guarda en el turno y queda disponible por
`GET /api/turnos/{id}/videollamada` para que tanto el paciente como el profesional la
obtengan y se sumen a la misma sala.
