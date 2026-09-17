# MediConecta — Contenido para armar la presentación

**Instrucciones para quien arme la presentación (Claude u otra persona):**
Este documento es la fuente de contenido para una presentación profesional (PPT) de
MediConecta, para la Entrega Obligatoria N.º 1 de Desarrollo de Aplicaciones II (UADE).
Las 11 capturas de pantalla reales están en `docs/presentacion/capturas/`, numeradas en el
orden sugerido de uso. Usalas como evidencia visual de cada slide relevante — son capturas
reales de la aplicación corriendo en WildFly + PostgreSQL, no mockups.

**Estilo sugerido:** limpio, profesional, pocas palabras por slide, una idea por slide.
Paleta del propio sistema: fondo `#F6F8FB`, sidebar `#0E2F47`, acento `#2563EB`, texto
`#172033`. Tipografía Inter. Evitar plantillas genéricas con gradientes o iconografía
corporativa que no coincida con el producto real.

---

## Mapa de capturas disponibles

| Archivo | Qué muestra | Rol logueado |
|---|---|---|
| `01-login.png` | Pantalla de login, dos columnas | — |
| `02-dashboard-recepcion.png` | Tablero de inicio con métricas reales | Recepción |
| `03-turnos-listado.png` | Agenda completa con buscador y filtros | Recepción |
| `04-turno-detalle.png` | Detalle de un turno con botón Cancelar | Recepción |
| `05-profesionales.png` | Equipo médico en tarjetas, con disponibilidad | Recepción |
| `06-pacientes.png` | Padrón de pacientes | Recepción |
| `07-reserva-elegir-paciente.png` | Paso previo de la reserva (selección de paciente) | Recepción |
| `08-reserva-especialidad.png` | Paso 1 de 4 del asistente de reserva, con stepper | Recepción |
| `09-dashboard-paciente.png` | Tablero adaptado al rol Paciente (sus propios datos) | Paciente |
| `10-mis-turnos.png` | Pantalla "Mis turnos" exclusiva del rol Paciente | Paciente |
| `11-mobile-mis-turnos.png` | La misma pantalla en formato mobile (420px) | Paciente |

---

## 1. Portada

**MediConecta**
Sistema de gestión de turnos médicos

Entrega Obligatoria N.º 1 — Desarrollo de Aplicaciones II — UADE
[Nombres de los integrantes del grupo]

---

## 2. El problema / la propuesta

Una clínica necesita coordinar turnos entre pacientes, profesionales y recepción, con
reglas claras (no superponer horarios, no agendar en el pasado) y con cada persona viendo
solo lo que le corresponde.

MediConecta resuelve esto con:
- una agenda centralizada;
- un padrón de pacientes con historial;
- profesionales con su propia disponibilidad semanal;
- una reserva guiada paso a paso;
- seguridad real por rol, no solo una pantalla distinta.

*(Sugerencia visual: usar `01-login.png` como imagen de apertura de esta sección — es la
puerta de entrada al sistema.)*

---

## 3. Arquitectura tecnológica

Aplicación Java Enterprise clásica, en capas, sobre un contenedor real — no una app de
juguete.

| Capa | Tecnología |
|---|---|
| Contenedor de aplicaciones | WildFly 41 (Jakarta EE 10) |
| Lógica de negocio | EJB (`@Stateless` y `@Stateful`) + CDI |
| API | JAX-RS (REST) |
| Persistencia | JPA (Hibernate) sobre PostgreSQL |
| Seguridad | Declarativa del contenedor (FORM + `@RolesAllowed`) |
| Frontend | HTML + CSS + JavaScript modular, sin frameworks |

**Arquitectura en tres capas**, con una regla estricta: cada capa solo conoce a la que
tiene debajo.

```
Presentación (JAX-RS, HTML/CSS/JS)
        ↓
Negocio (EJB: reglas del dominio, seguridad)
        ↓
Datos (JPA: acceso a PostgreSQL)
```

*(Diagrama sugerido: un esquema de tres cajas apiladas con flechas hacia abajo, en los
colores del sistema. No hace falta captura de pantalla acá — es un slide conceptual.)*

---

## 4. Los cuatro componentes de negocio

| # | Componente | Tipo EJB | Qué hace |
|---|---|---|---|
| 1 | `GestionTurnos` | `@Stateless` | Agenda, alta, cancelación y archivado de turnos |
| 2 | `GestionPacientes` | `@Stateless` | Padrón de pacientes e historial de atenciones |
| 3 | `GestionProfesionales` | `@Stateless` | Profesionales, especialidades y disponibilidad |
| 4 | `AsistenteReservaTurno` | **`@Stateful`** | Conversación de reserva guiada en 4 pasos |

Más `FachadaReservas`, que coordina los tres primeros para el caso de uso completo de
reservar un turno.

---

## 5. Stateless vs. Stateful — el corazón técnico de la entrega

**Stateless (`GestionTurnos`, `GestionPacientes`, `GestionProfesionales`):**
No recuerdan nada entre una llamada y la siguiente. Cada método recibe todo lo que
necesita, resuelve y termina. El contenedor reutiliza instancias libremente entre
distintos usuarios.

**Stateful (`AsistenteReservaTurno`):**
Mantiene el estado de una conversación de reserva — especialidad, profesional, horario
elegidos — **en el servidor**, entre pasos sucesivos. No es un truco de JavaScript ni de
`localStorage`: es memoria real del contenedor, asociada a la sesión del usuario mediante
un puente CDI `@SessionScoped`.

*(Capturas: `08-reserva-especialidad.png` muestra el stepper de 4 pasos con el progreso ya
guardado — "Ana Gomez" aparece en el resumen sin que el navegador la haya reenviado.)*

**Dato para remarcar en la demo:** en cada paso, el navegador manda un solo dato nuevo (por
ejemplo, solo el id del profesional elegido). El servidor ya sabe todo lo anterior.

---

## 6. Ciclo de vida administrado por el contenedor

El EJB `@Stateful` no lo crea ni lo destruye la aplicación: lo administra WildFly.

- `@PostConstruct`: el contenedor crea la instancia al iniciar una conversación.
- `@Remove`: la aplicación le avisa al contenedor que la conversación terminó.
- `@PreDestroy`: el contenedor descarta la instancia justo antes de eliminarla.
- `@StatefulTimeout`: si el usuario abandona a mitad de camino, se libera solo a los 30
  minutos.

*(Slide de apoyo, sin captura — mostrar el log real de WildFly en la demo en vivo es más
efectivo que una captura estática acá.)*

---

## 7. Patrones de diseño aplicados

Tres patrones reales, cada uno resolviendo un problema concreto del sistema (no clases
vacías para completar una lista).

**DAO / Repository.**
La capa de negocio nunca habla con la base de datos directamente: depende de interfaces
(`RepositorioTurnos`, `RepositorioPacientes`, `RepositorioProfesionales`). Prueba de que
funciona: el sistema migró de un almacenamiento en memoria a PostgreSQL sin cambiar una
sola línea del negocio.

**Facade.**
`FachadaReservas` concentra la coordinación de "reservar un turno" (buscar paciente,
buscar profesional, validar y agendar). La usan dos clientes distintos — el alta directa
por REST y el asistente `@Stateful` — sin duplicar la secuencia.

**Factory.**
`FabricaTurnos` es el único lugar donde se construye un `Turno`, garantizando que nazca
siempre en estado `PROGRAMADO`, con la fecha en formato correcto y la especialidad
coherente con el profesional.

---

## 8. Seguridad declarativa real

No es una pantalla de login que oculta botones en JavaScript: es el **contenedor**
(WildFly) el que autentica y autoriza.

- **Autenticación:** FORM administrada por el contenedor. Las credenciales nunca las ve el
  código de la aplicación.
- **Autorización:** `@RolesAllowed` sobre los métodos de negocio, más verificación de
  *ownership* cuando hace falta (un paciente solo cancela sus propios turnos; un médico
  solo edita su propia disponibilidad).

**Cuatro roles**, cada uno con una experiencia distinta:

| Rol | Puede |
|---|---|
| Paciente | Reservar y cancelar sus propios turnos, ver su ficha |
| Recepción | Gestionar la agenda completa, pacientes y disponibilidad de cualquiera |
| Médico | Ver la agenda, cancelar sus turnos, editar solo su propia disponibilidad |
| Administrador | Todo lo anterior, más dar de alta profesionales |

*(Capturas: comparar `02-dashboard-recepcion.png` contra `09-dashboard-paciente.png` — el
mismo sistema, dos experiencias completamente distintas según el rol logueado. Es una
buena dupla para un slide de "antes/después" o lado a lado.)*

---

## 9. Persistencia con JPA + PostgreSQL

- Entidades: `Paciente`, `Profesional`, `Disponibilidad`, `Turno`.
- Cada turno referencia a un paciente y a un profesional (relación `@ManyToOne`).
- El datasource se administra en WildFly (`java:/jdbc/MediConectaDS`), nunca hay
  credenciales de base de datos en el código.
- Los datos sobreviven a un reinicio del servidor — a diferencia de una implementación en
  memoria.

*(Diagrama sugerido: un esquema simple de 4 cajas — Paciente, Profesional, Disponibilidad,
Turno — con las relaciones. No requiere captura de pantalla.)*

---

## 10. Funcionalidad: gestión de turnos

- Alta de turno con validación de superposición y de fecha futura.
- Cancelación con reglas de *ownership* por rol.
- **Archivado lógico**: un turno cancelado se puede sacar de la agenda operativa sin
  borrar el registro — sigue disponible en el historial del paciente.

*(Capturas: `03-turnos-listado.png` para la agenda completa; `04-turno-detalle.png` para
el detalle con la acción de cancelar.)*

---

## 11. Funcionalidad: profesionales y disponibilidad

Cada profesional define su disponibilidad semanal (días y franjas horarias). El sistema
cruza esa disponibilidad con los turnos ya tomados para calcular, en tiempo real, los
horarios efectivamente libres — ni el paciente ni la recepción ven un horario que en
realidad ya está ocupado.

*(Captura: `05-profesionales.png`.)*

---

## 12. Funcionalidad: la reserva guiada

Un flujo de 4 pasos (Especialidad → Profesional → Horario → Confirmar), con un resumen
lateral que se completa a medida que se avanza. Diseñado para que reservar un turno sea
tan simple como en cualquier app de uso cotidiano — la complejidad técnica (el `@Stateful`
por debajo) queda invisible para quien usa el sistema.

*(Capturas: `07-reserva-elegir-paciente.png` y `08-reserva-especialidad.png`.)*

---

## 13. Experiencia por rol: el paciente

Desde el lado del paciente, el sistema es todavía más simple: solo ve sus propios datos,
su propia agenda, y puede reservar y cancelar sin fricción.

*(Capturas: `09-dashboard-paciente.png` y `10-mis-turnos.png`.)*

---

## 14. Diseño responsive

La interfaz se adapta a escritorio y a mobile sin usar ningún framework de CSS: media
queries propias, con las tablas convirtiéndose en tarjetas apiladas y la navegación
lateral reemplazada por una barra inferior.

*(Captura: `11-mobile-mis-turnos.png` — buen cierre visual de la sección de frontend.)*

---

## 15. Stack técnico — resumen

- Java 17
- Jakarta EE 10 (EJB, CDI, JAX-RS, JPA)
- WildFly 41 como contenedor de aplicaciones
- PostgreSQL como base de datos
- Maven como build tool, empaquetado WAR
- HTML + CSS + JavaScript modular en el frontend — sin React, sin Angular, sin jQuery

---

## 16. Cierre / próximos pasos

Componentes planificados para entregas futuras (no implementados todavía, fuera del
alcance de esta entrega):
- Notificaciones asincrónicas vía JMS.
- Integración con obras sociales externas vía SOAP/REST.

**MediConecta hoy:** cuatro componentes de negocio reales, tres patrones de diseño
aplicados con un propósito concreto, seguridad declarativa de punta a punta, persistencia
real y una interfaz completa y responsive — listo para desplegar y defender.

---

## Notas para quien arme el PPT

- Las capturas están recortadas para mostrar solo el contenido de la app (sin barra de
  navegador ni barra de tareas de Windows), a resolución real de escritorio (excepto la
  mobile, que está en ~420px de ancho).
- Si hace falta una captura adicional que no está en la carpeta (por ejemplo, el paso de
  "Confirmar" de la reserva, o la vista de un médico editando su propia disponibilidad),
  se puede pedir que se genere: el proceso para tomarlas está documentado y es repetible.
- Evitar inventar métricas o datos que no aparezcan en las capturas — los números que se
  ven (4 pacientes, 4 profesionales, etc.) son datos reales de la base de demostración, no
  hay que exagerarlos para la presentación.
