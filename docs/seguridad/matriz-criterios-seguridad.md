# MediConecta — Matriz de criterios de seguridad

**Taller — Clase 11 — Seguridad de aplicaciones — Desarrollo de Aplicaciones II (UADE)**

Este documento es la fuente versionada de la matriz. El PDF de entrega
(`Clase11_Grupo6_MediConecta_Seguridad.pdf`) se generó a partir de este archivo.

- **Distribución:** 6 criterios web/API, 2 de integración (SOAP/mensajería), 2 mobile.
- **Datos sensibles:** SEC-03, SEC-08 y SEC-10 están etiquetados como `datos-sensibles`
  (cumple el mínimo de 2).
- **Verificación:** los 10 se revisaron contra el código real del repositorio (commit
  `650c2c6`, rama `facu`). No se pudo correr contra un servidor desplegado (no hay un WildFly
  levantado en este entorno de trabajo), así que la evidencia es de **revisión de código**,
  no de pruebas HTTP en caliente. Donde la consigna pide "capturas de los tests", este
  documento deja el comando exacto para producirlas una vez que el equipo despliegue
  MediConecta — ver la columna *Cómo capturarlo en vivo*.

---

## Resumen de veredictos

| ID | Criterio | Categoría | Datos sensibles | Veredicto |
|---|---|---|:--:|---|
| SEC-01 | Autenticación obligatoria en la API | web/API | | ✅ PASA |
| SEC-02 | Autorización por rol en la capa de negocio | web/API | | ✅ PASA |
| SEC-03 | Protección contra IDOR en datos de un paciente | web/API | ✔ | ✅ PASA |
| SEC-04 | Manejo seguro de errores inesperados | web/API | | ⚠️ PARCIAL |
| SEC-05 | Las credenciales nunca pasan por código propio | web/API | | ✅ PASA |
| SEC-06 | Validación de datos de entrada antes de persistir | web/API | | ✅ PASA |
| SEC-07 | Menor privilegio en la integración SOAP | integración | | ✅ PASA |
| SEC-08 | Minimización de datos sensibles en los mensajes JMS | integración | ✔ | ✅ PASA |
| SEC-09 | Cookie de sesión protegida (HttpOnly) | mobile | | ✅ PASA |
| SEC-10 | Sin datos sensibles en almacenamiento local del navegador | mobile | ✔ | ✅ PASA |

**9 de 10 pasan. SEC-04 queda en PARCIAL con una recomendación concreta (ver abajo):
agregar un `ExceptionMapper<Throwable>` genérico, porque hoy el comportamiento ante un
error no previsto no está garantizado por la aplicación sino por la configuración por
defecto de WildFly.**

---

## SEC-01 — Autenticación obligatoria en la API

- **Categoría:** web/API
- **Dado** un cliente HTTP sin sesión autenticada
- **Cuando** solicita cualquier recurso bajo `/api/*` (excepto `/api/salud`)
- **Entonces** el servidor exige autenticación: `401` con cuerpo JSON si el cliente pidió
  JSON, o redirección a `/login` si es un navegador. Ninguna operación de negocio se ejecuta.
- **Referencia:** OWASP ASVS v4.0 — V1.2 (Authentication Architecture), V2.1 (Password
  Security, delegada al contenedor).
- **Veredicto: ✅ PASA**
- **Evidencia:**
  - `src/main/webapp/WEB-INF/web.xml` — `security-constraint` sobre `/api/*` con
    `auth-constraint` para los 4 roles; `/api/salud` tiene su propio `security-constraint`
    **sin** `auth-constraint`, público a propósito.
  - `ServletLogin.java:65-71` — si el pedido es de un cliente JSON (`X-Requested-With` o
    `Accept: application/json`), responde `401` con
    `{"error":"Debe iniciar sesion para usar esta operacion.","codigo":401}` en lugar de
    redirigir a una página HTML, que rompería a un cliente que espera JSON.
- **Cómo capturarlo en vivo:** `curl -i http://localhost:8080/mediconecta/api/turnos` (sin
  cookie de sesión) → capturar el `401` y el cuerpo JSON.

---

## SEC-02 — Autorización por rol en la capa de negocio, no en el endpoint REST

- **Categoría:** web/API
- **Dado** un usuario autenticado con un rol sin permiso para una operación
- **Cuando** invoca el método de negocio correspondiente (no solo el endpoint REST que lo
  expone)
- **Entonces** el contenedor EJB rechaza la invocación (`EJBAccessException` → `403`),
  incluso si se invocara desde otro canal (el mismo SOAP o la misma cola que se agregaron en
  la Entrega 2).
- **Referencia:** OWASP ASVS V4.1 (General Access Control Design).
- **Veredicto: ✅ PASA**
- **Evidencia:**
  - `GestionTurnos.java`, `GestionPacientes.java`, `GestionProfesionales.java` — cada método
    de negocio lleva su propio `@RolesAllowed`.
  - `RecursoTurnos.java`, `RecursoPacientes.java` (presentación) — **ningún** `@RolesAllowed`
    ahí: la autorización no depende de HTTP, como ya documenta `arquitectura.md` §5.2.
- **Cómo capturarlo en vivo:** loguearse como `paciente` e intentar
  `POST /api/profesionales` → capturar el `403` `{"error":"Su rol no tiene permiso..."}`.

---

## SEC-03 — Protección contra IDOR (Broken Object Level Authorization)

- **Categoría:** web/API · **`datos-sensibles`**
- **Dado** un usuario autenticado con **únicamente** rol PACIENTE
- **Cuando** solicita `GET /api/pacientes/{documento}/turnos` o
  `GET /api/pacientes/{documento}/cobertura` con un documento que **no** es el suyo
- **Entonces** el sistema rechaza la operación y no devuelve el historial clínico ni la
  cobertura de otra persona.
- **Referencia:** OWASP API Security Top 10 2023 — API1 Broken Object Level Authorization;
  ASVS V4.2.
- **Veredicto: ✅ PASA**
- **Evidencia:**
  - `GestionPacientes.java:146-157` — `exigirPropioDocumentoSiEsPaciente(documento)`: si
    quien llama tiene únicamente rol PACIENTE, exige que el documento pedido sea el de su
    propia ficha (comprobación programática sobre `SessionContext.getCallerPrincipal()`,
    arriba de la autorización declarativa).
  - `historialDeTurnos` (`GestionPacientes.java:123-128`) la invoca.
  - `GestorCobertura.validarCobertura` (`GestorCobertura.java`) **también** la invoca, antes
    de elegir el adapter SOAP o REST — se verificó específicamente que el endpoint nuevo de
    cobertura (agregado en la Entrega 2) no sea un agujero: no lo es, pasa por el mismo
    control.
- **Cómo capturarlo en vivo:** loguearse como `paciente` (documento propio distinto del
  pedido) y hacer `GET /api/pacientes/{otroDocumento}/turnos` → capturar el `400`
  `"Solo puede operar sobre sus propios turnos."`.

---

## SEC-04 — Manejo seguro de errores inesperados

- **Categoría:** web/API
- **Dado** un error **no previsto** por el negocio (una excepción de sistema, no una
  `ReglaNegocioException`/`NoEncontradoException`/`EJBAccessException`/
  `IntegracionExternaException`)
- **Cuando** ese error llega a la capa de presentación JAX-RS
- **Entonces** el cliente debería recibir una respuesta genérica, sin stack trace ni nombres
  internos de clases o de EJB.
- **Referencia:** OWASP ASVS V7.4 (Error Handling); OWASP Top 10 2021 — A09 (Security Logging
  and Monitoring Failures, en su aspecto de no exponer detalle interno).
- **Veredicto: ⚠️ PARCIAL — hallazgo real, no solo un ejercicio formal**
- **Evidencia:**
  - En `src/main/java/com/mediconecta/presentacion/` hay exactamente 4
    `ExceptionMapper`: `ManejadorReglaNegocio` (`ReglaNegocioException` → 400),
    `ManejadorNoEncontrado` (`NoEncontradoException` → 404), `ManejadorAccesoDenegado`
    (`EJBAccessException` → 403) y `ManejadorIntegracionExterna`
    (`IntegracionExternaException` → 502).
  - **No existe** un `ExceptionMapper<Throwable>` genérico. Si una excepción de sistema no
    contemplada llega a JAX-RS (por ejemplo una `NullPointerException` o un error de
    PostgreSQL que no se traduce a ninguna de las cuatro anteriores), el comportamiento queda
    librado a la configuración por defecto de RESTEasy/WildFly — que en modo desarrollo
    puede incluir el stack trace en la respuesta. La aplicación no lo garantiza por sí misma.
  - Esto conecta con el commit `8e168b3` ("la reserva guiada deja de mostrar detalles
    internos del servidor"), que resolvió un problema de **copy de la interfaz** (texto como
    "instancia del EJB" visible al usuario), pero no es lo mismo que este criterio: ese
    commit no tocó el manejo de excepciones de la API.
- **Recomendación concreta:** agregar un `ExceptionMapper<Throwable>` que loguee el detalle
  completo del lado del servidor y devuelva al cliente un `500` genérico
  (`{"error":"Ocurrio un error interno.","codigo":500}`), sin stack trace ni nombres de
  clase — siguiendo el mismo patrón que los otros cuatro *manejadores*.
- **Cómo capturarlo en vivo:** forzar una excepción no contemplada (por ejemplo, apagar la
  base de datos y hacer un `GET /api/turnos`) y capturar la respuesta completa.

---

## SEC-05 — Las credenciales nunca pasan por código propio

- **Categoría:** web/API
- **Dado** el formulario de login
- **Cuando** el usuario envía usuario y contraseña
- **Entonces** la validación la hace el contenedor (`j_security_check` contra
  `ApplicationDomain`) y ninguna línea de código de MediConecta lee ni compara la contraseña.
- **Referencia:** OWASP ASVS V2.1 (Password Security), V2.4 (Credential Storage — delegado
  al contenedor).
- **Veredicto: ✅ PASA**
- **Evidencia:**
  - `web.xml` — `<login-config><auth-method>FORM</auth-method>...`.
  - `ServletLogin.java` — no tiene ningún campo ni lógica que lea `j_password`; solo decide
    qué página/respuesta mostrar según si el login falló y según el tipo de cliente.
- **Cómo capturarlo en vivo:** `grep -ri "password\|contrasena" src/main/java` → no debería
  haber ningún resultado que compare contraseñas en código propio.

---

## SEC-06 — Validación de datos de entrada antes de persistir

- **Categoría:** web/API
- **Dado** un pedido de alta de turno con una fecha pasada, o un alta de paciente sin
  documento o sin nombre
- **Cuando** se procesa en `GestionTurnos.agendar` / `GestionPacientes.registrarPaciente`
- **Entonces** se rechaza con `ReglaNegocioException` **antes** de tocar la base de datos.
- **Referencia:** OWASP ASVS V5.1 (Input Validation).
- **Veredicto: ✅ PASA**
- **Evidencia:**
  - `GestionTurnos.java:104-106` — `if (momento.isBefore(LocalDateTime.now())) throw new ReglaNegocioException(...)`, antes de `repositorio.guardar(turno)`.
  - `GestionPacientes.java:67-72` — valida documento y nombre no vacíos antes de
    `repositorio.guardar(paciente)`.
- **Cómo capturarlo en vivo:** `POST /api/turnos` con una `fechaHora` del año pasado →
  capturar el `400`.

---

## SEC-07 — Menor privilegio en la integración SOAP

- **Categoría:** integración (SOAP)
- **Dado** el servicio SOAP que integra el sistema legado de obras sociales
- **Cuando** se publica su WSDL
- **Entonces** solo expone operaciones de **consulta**; ninguna operación SOAP puede
  modificar datos de MediConecta.
- **Referencia:** OWASP ASVS V1.1 (Secure Software Development — superficie mínima en
  integraciones externas).
- **Veredicto: ✅ PASA**
- **Evidencia:**
  - `ServicioObraSocialLegado.java` (SEI) declara únicamente
    `consultarVigencia(String, String): boolean` y `consultarPlan(String, String): String` —
    dos lecturas, cero escrituras.
  - `ServicioObraSocialLegadoImpl.java` resuelve contra una tabla fija en memoria, sin tocar
    el `EntityManager` ni ningún repositorio de MediConecta.
- **Cómo capturarlo en vivo:** `curl http://localhost:8080/mediconecta/ServicioObraSocialLegado?wsdl`
  → capturar el WSDL y confirmar que solo lista las dos operaciones.

---

## SEC-08 — Minimización de datos sensibles en los mensajes JMS

- **Categoría:** integración (mensajería) · **`datos-sensibles`**
- **Dado** que se confirma un turno
- **Cuando** `NotificadorTurnos` arma el mensaje para la cola y el tópico
- **Entonces** el mensaje incluye nombre del paciente, nombre del profesional, especialidad y
  fecha — **no** incluye documento, email ni teléfono, aunque el productor tiene acceso a
  esos datos.
- **Referencia:** OWASP ASVS V8.3 (Sensitive Data Minimization).
- **Veredicto: ✅ PASA**
- **Evidencia:**
  - `NotificadorTurnos.java` — el `String resumen` se arma con
    `turno.getId()`, `paciente.getNombre()`, `profesional.getNombre()`,
    `turno.getEspecialidad()`, `turno.getFechaHora()`. No referencia
    `paciente.getDocumento()`, `getEmail()` ni `getTelefono()`.
- **Cómo capturarlo en vivo:** reservar un turno y capturar la línea de log
  `[JMS-COLA] confirmacion enviada -> ...` en la consola de WildFly.

---

## SEC-09 — Cookie de sesión protegida (HttpOnly), relevante para un cliente mobile

- **Categoría:** mobile — *MediConecta no tiene app nativa (`sin app`, según la consigna);
  el criterio se aplica al navegador del teléfono, que es el cliente mobile de facto, y por
  analogía a cómo debería comportarse un futuro cliente embebido (WebView).*
- **Dado** que se abre una sesión desde cualquier cliente, incluido un navegador mobile
- **Cuando** el contenedor emite la cookie de sesión
- **Entonces** la cookie se marca `HttpOnly` (no accesible por JavaScript) y viaja por el
  mismo mecanismo FORM que cualquier otro cliente.
- **Referencia:** OWASP MASVS-STORAGE-1 / MASVS-NETWORK-1 (aplicado por analogía, dado que no
  hay app nativa que evaluar directamente con la metodología MASVS completa).
- **Veredicto: ✅ PASA**
- **Evidencia:**
  - `web.xml` — `<session-config><cookie-config><http-only>true</http-only></cookie-config>`.
- **Cómo capturarlo en vivo:** loguearse desde un navegador mobile (o con el emulador de
  dispositivo de las devtools) y, en la pestaña Application/Cookies, capturar que el flag
  `HttpOnly` esté marcado en `JSESSIONID`.

---

## SEC-10 — Sin datos sensibles en almacenamiento local del navegador

- **Categoría:** mobile — mismo alcance que SEC-09: se evalúa el frontend web responsive
  (ya probado en mobile, ver `docs/presentacion/capturas/11-mobile-mis-turnos.png`) por
  analogía a MASVS, a falta de app nativa.
- **Datos sensibles:** ✔
- **Dado** un paciente que usa MediConecta desde el navegador de su teléfono
- **Cuando** navega la pantalla "Mis turnos" u otra pantalla con datos propios
- **Entonces** ningún dato de su historial queda en `localStorage`/`sessionStorage`, donde
  otra app en el mismo dispositivo, o un dispositivo compartido, podría leerlo después de
  cerrar sesión.
- **Referencia:** OWASP MASVS-STORAGE-2 (aplicado por analogía).
- **Veredicto: ✅ PASA**
- **Evidencia:**
  - `grep -r "localStorage\|sessionStorage" src/main/webapp` → sin resultados: el frontend
    no usa almacenamiento local del navegador para nada (la sesión la administra
    exclusivamente la cookie `HttpOnly` del contenedor).
- **Cómo capturarlo en vivo:** devtools → Application → Local/Session Storage con sesión de
  paciente abierta → capturar que ambos aparecen vacíos.

---

## Tickets listos para cargar en Jira

Formato sugerido por fila (etiqueta `seguridad`, ID `SEC-xx`; completar *Responsable* según
cómo se reparta el equipo):

| ID | Título | Etiqueta | Responsable |
|---|---|---|---|
| SEC-01 | Autenticación obligatoria en la API | seguridad | _(asignar)_ |
| SEC-02 | Autorización por rol en la capa de negocio | seguridad | _(asignar)_ |
| SEC-03 | Protección contra IDOR en datos de un paciente | seguridad, datos-sensibles | _(asignar)_ |
| SEC-04 | Manejo seguro de errores inesperados | seguridad | _(asignar)_ |
| SEC-05 | Las credenciales nunca pasan por código propio | seguridad | _(asignar)_ |
| SEC-06 | Validación de datos de entrada antes de persistir | seguridad | _(asignar)_ |
| SEC-07 | Menor privilegio en la integración SOAP | seguridad | _(asignar)_ |
| SEC-08 | Minimización de datos sensibles en los mensajes JMS | seguridad, datos-sensibles | _(asignar)_ |
| SEC-09 | Cookie de sesión protegida (HttpOnly) | seguridad | _(asignar)_ |
| SEC-10 | Sin datos sensibles en almacenamiento local del navegador | seguridad, datos-sensibles | _(asignar)_ |

La descripción de cada ticket es el bloque Dado/Cuando/Entonces + evidencia + referencia de
la sección correspondiente de este documento.
