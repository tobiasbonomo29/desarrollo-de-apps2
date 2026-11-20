# Guion final actualizado — MediConecta

> **Aviso:** las notas del PPTX remiten a `Guion_Exposicion_Final.md`, pero ese archivo no estaba
> en el repositorio ni en la carpeta de descargas. Este guion se armó a partir del contenido de las
> diapositivas. Donde una diapositiva no cambió, el texto sigue lo que ya dice la diapositiva, para
> que coincida con lo que cada uno estudió. Lo que hay que aprender de nuevo está marcado con
> **[NUEVO]** y resumido en `Cambios_En_Los_Guiones.md`.

Duración objetivo: ~2–3 min por integrante (10–15 min en total) + demo.

---

## Facundo Sánchez — diapositivas 1 a 4 (≈ 2 min 30 s) · sin cambios

**Diap. 1 — Portada.** "Buenas, somos el grupo 6 y les vamos a presentar MediConecta: la agenda médica de una clínica, ordenada. Es un sistema de gestión de turnos hecho sobre Jakarta EE 10 y WildFly. Lo presentamos Mateo, Facundo Morán, Tobías, Laureano y yo, Facundo Sánchez. Lo que ven de fondo es una captura real del tablero de recepción."

**Diap. 2 — El problema.** "Hoy en muchas clínicas los turnos se siguen dando por teléfono o en planillas. Eso trae cuatro problemas: la línea ocupada y horarios que solo conoce la recepción; agendas separadas que generan superposiciones y turnos duplicados; el paciente no recibe confirmación y el médico no sabe quién viene; y muchos pacientes ni siquiera saben qué especialista necesitan. Lo que buscamos es centralizar todo en un solo sistema, donde cada usuario vea su propia información."

**Diap. 3 — La solución.** "MediConecta es una aplicación web con cuatro roles y una sola agenda compartida. El paciente reserva su turno en cuatro pasos y ve sus turnos. La recepción administra la agenda y los pacientes. El médico consulta sus turnos y define su disponibilidad. Y la administración puede hacer todo eso y además dar de alta profesionales. Cada uno entra con su rol y ve solo lo que le corresponde."

**Diap. 4 — Reservar un turno.** "Así se ve la reserva desde el paciente: elige la especialidad —o le cuenta al asistente qué le pasa—, elige el profesional, que solo aparece si atiende esa especialidad, elige un horario libre según la agenda real del médico y confirma. Detrás de cada clic el servidor recuerda cada paso, controla que no haya superposición, guarda el turno y manda los avisos al paciente y al médico. Cómo está construido eso por dentro se los cuenta Mateo."

---

## Mateo Bellinzona — diapositivas 5 a 8 (≈ 3 min)

**Diap. 5 — Arquitectura.** "La aplicación está en tres capas y cada una conoce solo a la de abajo. Arriba, la presentación: la interfaz web y la API REST, con errores en formato Problem Details y filtros que agregan cabeceras de seguridad **[NUEVO: CSP, nosniff y no-store]**. En el medio, el negocio, con EJB: los stateless de turnos, pacientes y profesionales, la fachada de reservas, el asistente stateful y la fábrica con el patrón State. Abajo, los datos con el patrón Repository: JPA contra PostgreSQL y una implementación en memoria como plan B. Al costado, los servicios transversales: seguridad con JWT, mensajería JMS, integraciones SOAP y REST, y la IA con timeout y fallback."

**Diap. 6 — Tecnologías.** "Todo es estándar Jakarta EE: el WAR tiene una sola dependencia, la API de Jakarta, provista por WildFly. Usamos PostgreSQL con JPA **[NUEVO: y el esquema versionado con Flyway]**, JAX-RS y JAX-WS, ActiveMQ Artemis para cola, tópico y DLQ, JWT con PBKDF2 para las contraseñas, y una interfaz sin framework. Para la calidad: Maven Wrapper, JUnit, JaCoCo y PIT, y GitHub Actions **[NUEVO: que además levanta todo con Docker Compose]** y busca secretos con gitleaks."

**Diap. 7 — Componentes.** "Tenemos siete componentes. **[NUEVO] Uno es stateful, el resto stateless, y los avisos los consumen MDB, es decir beans por mensajes.** El asistente de reserva es stateful porque la reserva es una conversación: el servidor recuerda paciente, especialidad, profesional y horario entre pasos, y la descarta a los 30 minutos. Los demás son stateless porque aplican reglas sin memoria: cualquier instancia del pool atiende cualquier pedido y escala mejor."

**Diap. 8 — Patrones.** "Cada patrón resuelve un problema concreto. State maneja el ciclo de vida del turno: programado, atendido o cancelado; una transición inválida, como cancelar dos veces, devuelve 409. Facade: FachadaReservas es la única puerta para reservar. Factory: todo turno nace programado y coherente. Adapter: el sistema SOAP legado y el partner REST quedan detrás de la misma interfaz. Y Repository nos deja cambiar JPA por memoria sin tocar el negocio. Ahora Facundo les muestra cómo se usa."

---

## Facundo Morán — diapositivas 9 a 12 (≈ 3 min)

**Diap. 9 — Funcionalidades por rol.** "La interfaz solo muestra las acciones que el backend le permite a cada rol: la recepción ve la agenda con buscador y filtros, el paciente sus turnos desde el teléfono, el médico su disponibilidad, y desde el detalle de un turno se cancela, atiende o archiva."

**Diap. 10 — El flujo crítico.** "Reservar involucra varios componentes en una sola transacción: o se hace todo o no se hace nada. La fachada coordina: GestionPacientes resuelve al paciente y verifica que sea el propio, GestionTurnos con la fábrica valida la fecha y la superposición, y el notificador publica los avisos por JMS dentro de la misma transacción. Si un paso falla, el contenedor deshace todo. Y si el cliente reintenta con la misma Idempotency-Key, recibe el mismo turno."

**Diap. 11 — Integraciones.** "La cobertura de obra social usa Adapter: PAMI, OSDE e IOMA van por SOAP al sistema legado y el resto por REST al partner. Los avisos van por JMS: una cola para confirmaciones y un tópico para recordatorios al paciente y al profesional. **[NUEVO] Si un mensaje falla, se intenta cuatro veces —la entrega más tres reintentos— y después pasa a la DLQ, donde lo registra MonitorMensajesFallidos.** **[NUEVO] Todo esto lo verifica el CI levantando el sistema completo con Docker Compose.**"

**Diap. 12 — Resiliencia.** "El sistema falla bien. **[NUEVO] Si una obra social o la IA no responde, se corta a los 5 segundos y responde el respaldo:** el último dato bueno de cobertura o una sugerencia por palabras clave. **[NUEVO] Tenemos un endpoint de salud, /api/salud, que dice si la base y el broker están arriba y devuelve 503 si se caen.** **[NUEVO] Un mensaje inválido se reintenta con esperas de 1, 2 y 4 segundos y termina en MensajesFallidos.** Cada consumidor aplica el efecto una sola vez aunque el mensaje llegue repetido, y los mensajes son persistentes: sobreviven a un reinicio del broker, lo probamos el 19/11. Tobías les cuenta cómo lo probamos."
*Si preguntan por circuit breaker:* "La versión final tiene timeout y fallback, pero no circuit breaker ni reintento acotado hacia los externos; está identificado como pendiente."

---

## Tobías Bonomo — diapositivas 13 a 16 (≈ 3 min)

**Diap. 13 — Estrategia.** "Muchas pruebas rápidas abajo y pocas caras arriba. **[NUEVO] Abajo, 195 tests de JUnit** que prueban los EJB como objetos comunes con repositorios en memoria. **[NUEVO] Arriba, la prueba de punta a punta smoke-test.sh con 148 verificaciones, carga con k6 de 20 usuarios durante 45 segundos, y seguridad con el humo de ataques y ZAP baseline.** **[NUEVO] El contrato de la API se controla en cada build con ContratoOpenApiTest.** **[NUEVO] La IA se prueba de dos formas: en los tests con un proveedor simulado, y con 12 casos contra Groq real, que se corren a mano porque necesitan la clave.** **[NUEVO] Registramos 10 defectos, todos corregidos, ninguno crítico abierto.**"

**Diap. 14 — Cobertura y mutación.** "**[NUEVO] Medimos sobre el commit final de la integración. En la capa de negocio tenemos 95 % de líneas y 82,6 % de ramas, contra mínimos de 80 y 70. El mutation score de PIT sobre negocio y el patrón State es 85 %: 338 de 396 mutantes eliminados, contra un mínimo de 60. Para ser claros: sobre todo el proyecto la cobertura es 72,5 %, y ahí la cátedra no fija umbral.** Si bajamos de los mínimos, el build falla. **[NUEVO] 195 tests sin fallas y 148 de 148 verificaciones de humo en Docker Compose.**"

**Diap. 15 — CI.** "**[NUEVO] Cada push compila, pasa Checkstyle, corre los 195 tests con cobertura, mide mutación, busca secretos en todo el historial —cero hallazgos— y levanta el sistema completo con Docker Compose para el humo, la carga y ZAP. La última corrida, sobre la rama de integración, dio seis jobs en verde en 4 minutos.** Siendo honestos: SonarCloud está omitido porque falta configurar el token, así que no tenemos Quality Gate aprobado. **[NUEVO] Falta mergear el PR a facu y main y crear el tag.**"

**Diap. 16 — Seguridad.** "Defensas en capas: JWT con firma, emisor y vencimiento; contraseñas con PBKDF2; roles en cada EJB contra BFLA; cada paciente ve solo lo suyo contra BOLA; 429 después de cinco intentos fallidos de login; y cabeceras como CSP y nosniff. **[NUEVO] Estas respuestas son las que verifica el humo en CI: 401 con token adulterado, 403 si un paciente pide la agenda o un turno ajeno, 409, 412, 422 y 429. Y ZAP baseline en CI no dio alertas High.** Le paso a Laureano."

---

## Laureano Gómez Moreno — diapositivas 17 a 20 (≈ 3 min)

**Diap. 17 — IA.** "En el primer paso de la reserva, el paciente puede contar qué le pasa —por ejemplo, 'me torcí el tobillo jugando al fútbol'— y el asistente sugiere Traumatología con un botón para elegirla. La persona decide. Le pusimos límites: solo sugiere, no diagnostica; solo especialidades con médicos activos; ante una urgencia indica llamar al 107 o al 911; y no enviamos nombre ni DNI."

**Diap. 18 — Gateway.** "**[NUEVO] La IA es un sistema externo más, con timeout y respaldo como SOAP y REST.** El chat pasa por GestorOrientacion, que valida, limita a 10 consultas por minuto y filtra especialidades; **[NUEVO] el adaptador corta a los 2 segundos de conexión o 5 de lectura**, y si el proveedor falla, tarda o no hay clave, **[NUEVO] responde OrientacionDeRespaldo con contingencia en true.** **[NUEVO] La tabla muestra lo que verifican los tests y el CI: con el proveedor bien responde la IA; si tarda, da error o falta la clave, responde el respaldo.** **[NUEVO] Lo que no verificamos sobre esta versión: la evaluación de 12 casos con Groq real dio 12 de 12 el 19/11, pero no guardamos la salida y no la repetimos.**"

**Diap. 19 — Resultados.** "**[NUEVO] Lo que podemos demostrar: 195 tests sin fallas, 95 % de líneas en la capa de negocio —72,5 en todo el proyecto—, 85 % de mutation score, 148 de 148 pruebas de punta a punta en Docker Compose, un p95 de 22 milisegundos con 20 usuarios y cero errores, ya con PostgreSQL, y cero alertas High en ZAP. Todo eso es del CI del 20/11 sobre la versión integrada.** Y cubrimos los requisitos del TP: siete componentes, stateful y stateless, cinco patrones, SOAP y REST, cola y tópico, JWT con roles y transacción declarativa."

**Diap. 20 — Cierre.** "Nos llevamos tres cosas: diseñar para que el sistema falle bien, poner la seguridad en el negocio y no en la pantalla, y medir en lugar de suponer. Ahora vamos a la demo: el paciente reserva con el asistente de IA, recepción y médico gestionan el turno, mostramos lo que no se puede hacer, y el admin manda un mensaje inválido a la DLQ. **[NUEVO] Después de la demo, preguntas. ¡Gracias!**"
