# Actualizaciones sugeridas para el tablero de Trello

**Tablero:** <https://trello.com/b/brZqkjJl/mediconecta-tpi-desarrollo-de-aplicaciones-ii>

> **Nota.** Durante esta sesión de trabajo **no hubo integración con Trello disponible**, así
> que el tablero **no se modificó**. Este documento lista lo que conviene mover, cerrar o
> crear para que el tablero refleje el estado real del repositorio. Cada punto está redactado
> para poder copiarse y pegarse en la tarjeta.

---

## 1. Tarjetas a mover a **Hecho**

Corresponden a trabajo terminado, desplegado y verificado.

| Tarjeta | Comentario sugerido al cerrarla |
|---|---|
| **C1 · GestionTurnos (stateless)** | Completada y ampliada: se agregó `cancelarTurno`, consulta por paciente, por día y por profesional. Endpoint `PUT /api/turnos/{id}/cancelar`. |
| **C2 · GestionPacientes (stateless)** | Implementada: alta, búsqueda, actualización e historial. Documento único obligatorio. API en `/api/pacientes`. |
| **C3 · GestionProfesionales (stateless)** | Implementada: alta (solo ADMIN), especialidades, disponibilidad semanal y cálculo de horarios libres colaborando con C1. |
| **C4 · AsistenteReservaTurno (stateful)** | Implementado como EJB `@Stateful` real, con `@PostConstruct`, `@Remove`, `@PreDestroy` y `@StatefulTimeout`. Atado a la sesión HTTP con un bean CDI `@SessionScoped`. |
| **Migrar de memoria a JPA + PostgreSQL** | Unidad `mediconectaPU` sobre `java:/jdbc/MediConectaDS`. Las implementaciones en memoria quedaron como `@Alternative`, sin tocar negocio ni presentación. |
| **Patrón DAO / Repository** | Tres interfaces en `com.mediconecta.datos`, con implementaciones JPA y en memoria conviviendo. |
| **Patrón Facade** | `FachadaReservas`: dos clientes (REST y asistente stateful) comparten la misma coreografía. |
| **Patrón Factory** | `FabricaTurnos`: único punto de creación de turnos; garantiza estado inicial, fecha canónica y coherencia de especialidad. |
| **Seguridad declarativa con roles** | FORM del contenedor + `@RolesAllowed` sobre los métodos de negocio. Roles PACIENTE, RECEPCION, MEDICO y ADMIN. |
| **Cancelación de turnos** | Implementada con sus reglas: no se cancela dos veces, no se cancela un turno atendido, el horario vuelve a quedar libre. |
| **Interfaz según el prototipo de Figma** | Ocho pantallas implementadas en HTML + CSS + módulos ES, responsive con breakpoints en 768 y 1100 px. |
| **Documentación de la Entrega 1** | README, `docs/arquitectura.md`, `docs/Entrega_Obligatoria_1.md`, `docs/guion-defensa.md` y `docs/preguntas-defensa.md`. |

---

## 2. Tarjetas a crear

### Para cerrar la entrega

- **Verificar el despliegue sobre WildFly 36.0.1.Final**
  *Descripción:* todo fue verificado sobre WildFly 41.0.1.Final con JDK 21 y PostgreSQL
  16.14. El proyecto apunta a Jakarta EE 10, que es la plataforma de WildFly 36, pero no se
  ejecutó sobre esa versión. Repetir el despliegue y el `smoke-test.ps1` sobre 36.0.1.Final
  antes de la defensa y anotar el resultado en el README.

- **Ensayo completo de la defensa**
  *Descripción:* recorrer `docs/guion-defensa.md` de punta a punta cronometrando, con la
  base en estado inicial. Verificar que los cuatro usuarios entran y que el log del stateful
  se ve en pantalla.

- **Repartir los puntos de la defensa entre los cinco integrantes**
  *Descripción:* asignar secciones del guion. Sugerencia: contenedor y despliegue ·
  seguridad y roles · capas y patrones · componente stateful · persistencia y cierre.

- **Convertir `docs/Entrega_Obligatoria_1.md` al formato pedido por la cátedra**
  *Descripción:* pasar a PDF/Word de 5 a 8 páginas, con carátula, integrantes y los
  diagramas Mermaid renderizados como imagen.

### Deuda técnica registrada

- **Estado `ATENDIDO` sin operación que lo asigne**
  *Descripción:* el enum lo contempla pero no hay endpoint. Definir si corresponde a esta
  materia o si queda para el registro de la atención.

- **Duración variable del turno**
  *Descripción:* hoy la superposición se detecta por coincidencia exacta del horario de
  inicio. Si dos profesionales o especialidades tuvieran duraciones distintas en la misma
  franja, habría que comparar intervalos.

- **Ruido en el log ante un 403**
  *Descripción:* WildFly escribe un stack trace completo de `EJBAccessException` cada vez
  que el contenedor rechaza una invocación por rol. Evaluar si conviene bajar el nivel del
  logger `org.jboss.as.ejb3.invocation` en la configuración del servidor.

### Próximas entregas (dejar en el backlog, sin empezar)

- **C5 · Notificaciones con JMS** — Entrega 3. Cola y tópico, avisos de confirmación y
  recordatorio.
- **C6 · IntegracionObrasSociales** — Entrega 4. SOAP contra el sistema legado y REST contra
  un partner, detrás de una misma interfaz con el patrón **Adapter**.
- **Transacciones en un flujo crítico** — Entrega 4, según la consigna de la materia.

---

## 3. Tarjetas a revisar o corregir

| Situación | Acción sugerida |
|---|---|
| Tarjetas que digan *"se implementa en la Entrega 2"* sobre C2, C3 o C4 | Ya están implementadas. Corregir la descripción o cerrarlas. |
| Tarjetas que mencionen *almacenamiento en memoria* como estado actual | La persistencia es JPA + PostgreSQL. La versión en memoria es un plan B documentado. |
| Tarjeta de *"login"* que suponga una pantalla de JavaScript | La autenticación la administra el contenedor con FORM. Actualizar la descripción. |
| Cualquier tarjeta que mencione **Adapter** dentro de esta entrega | Adapter corresponde a la Entrega 4. Moverla al backlog. |

---

## 4. Etiquetas sugeridas

Para que el tablero sea legible de un vistazo en la defensa:

- `Entrega 1` · `Entrega 2` · `Entrega 3` · `Entrega 4`
- `componente` · `patrón` · `seguridad` · `persistencia` · `frontend` · `documentación`
- `deuda técnica`
