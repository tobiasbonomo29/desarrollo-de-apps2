# Preguntas probables de la cátedra y respuestas

Respuestas cortas, para contestar de memoria. Cada una está pensada para durar menos de un
minuto; si el docente quiere profundizar, el detalle está en
[arquitectura.md](arquitectura.md).

---

## Componentes: stateless y stateful

**¿Por qué `GestionTurnos` es stateless?**
Porque no conserva nada entre una invocación y la siguiente. Cada método recibe en sus
parámetros todo lo que necesita, resuelve y termina. El único atributo de la clase es la
referencia inyectada al repositorio, que es una colaboración y no estado del cliente. Por
eso dos requests del mismo usuario pueden caer en instancias distintas del pool sin ninguna
diferencia observable.

**¿Por qué `AsistenteReservaTurno` es stateful?**
Porque reservar guiado es **una sola operación de negocio repartida en cuatro
invocaciones**. Entre una y otra el servidor tiene que recordar qué eligió *ese* usuario. La
prueba está en el contrato: `PUT /api/reserva/profesional` manda únicamente
`{"profesionalId": 1}` —no lleva el paciente ni la especialidad— y el servidor igual los
conoce. El navegador sólo envía la cookie de sesión.

**Pero los turnos se guardan en algún lado, ¿eso no es estado?**
Sí, pero es **estado de aplicación**: vive en la base de datos, detrás de la capa de datos,
y es compartido por todos los clientes. Lo que define a un componente como stateless es que
no conserve estado *de conversación con un cliente particular*.

**¿Cómo se asocia el EJB stateful con el usuario?**
Con `ConversacionDeReserva`, un bean CDI `@SessionScoped` que guarda la referencia al
asistente. CDI asocia ese bean a la sesión HTTP, es decir a la cookie `JSESSIONID`.
Inyectamos `Instance<AsistenteReservaTurno>` en lugar del EJB directo porque necesitamos
poder destruir la instancia cuando la conversación termina y pedir una nueva para la
siguiente reserva.

**¿Qué pasa si el usuario abandona la reserva a la mitad?**
El bean tiene `@StatefulTimeout(30, MINUTES)`: el contenedor lo descarta solo. Y si se cierra
la sesión HTTP antes, el `@PreDestroy` del bean de sesión cierra la conversación.

**¿Cuántas instancias hay de cada componente?**
De los stateless, las que el contenedor decida tener en el pool, compartidas entre todos los
usuarios. Del stateful, **una por conversación de usuario**. Por eso los stateful escalan
peor y hay que usarlos sólo cuando de verdad hay una conversación.

---

## Contenedor y ciclo de vida

**¿Qué administra WildFly y qué escribieron ustedes?**
WildFly administra el ciclo de vida de los EJB, el pool de instancias, las transacciones
JTA, la autenticación, la evaluación de `@RolesAllowed`, el pool de conexiones a PostgreSQL,
la serialización JSON y la inyección de dependencias. Nosotros escribimos las reglas del
dominio, el modelo, las consultas del repositorio y la interfaz. En todo el proyecto no hay
un solo `new` de un EJB.

**¿Para qué sirve `@PostConstruct`?**
Es el método que el contenedor invoca después de crear la instancia y de inyectarle sus
dependencias. No se puede usar el constructor para eso, porque cuando el constructor corre
las inyecciones todavía no están hechas. En el asistente lo usamos para abrir la
conversación y asignarle identificador; en `CargaDeDatosDemo`, para cargar el juego de datos
de ejemplo al desplegar.

**¿Para qué sirve `@Remove`?**
Es la forma que tiene la aplicación de decirle al contenedor que la conversación con ese
cliente terminó y que puede descartar el bean stateful. Sin `@Remove`, la instancia quedaría
ocupando memoria hasta que venciera el `@StatefulTimeout`. En MediConecta se invoca al
confirmar o al abandonar una reserva, y justo después corre el `@PreDestroy`. Se ve en el
log.

**¿Y `@PreDestroy`?**
Lo invoca el contenedor justo antes de descartar la instancia, sea por `@Remove`, por
*timeout* o porque terminó la sesión. Lo usamos para dejar registro en el log, que es lo que
permite mostrar el ciclo de vida completo durante la defensa.

---

## Capas

**¿Qué separa cada capa?**
Presentación habla HTTP y JSON: traduce el pedido, delega y arma la respuesta; no valida
reglas. Negocio tiene las reglas del dominio y decide qué es válido; no sabe que existe
HTTP. Datos sabe cómo se guarda y se busca; no conoce las reglas.

**¿Qué pasaría si la capa de presentación accediera directamente a la base de datos?**
Tres cosas, todas malas. Primero, las reglas quedarían fuera del negocio: la validación de
"no agendar en el pasado" estaría en el recurso REST, y si mañana exponemos el mismo caso de
uso por SOAP o desde una cola de mensajes habría que reescribirla. Segundo, se perdería la
transacción: hoy el contenedor la abre al entrar al método del EJB, y una operación que
toca dos tablas es atómica. Tercero, cambiar de motor de base de datos obligaría a tocar la
interfaz de usuario.

**¿Cómo demuestran que la separación es real y no sólo nombres de paquetes?**
Buscando los imports. `jakarta.persistence` sólo aparece en `datos` y en `modelo` (por las
anotaciones de entidad); `jakarta.ws.rs` sólo aparece en `presentacion`. Y la prueba fuerte:
migramos de un `ConcurrentHashMap` a PostgreSQL sin tocar la capa de negocio.

---

## Patrones

**¿Por qué DAO / Repository?**
Porque el enunciado nos obligaba a cambiar de almacenamiento entre entregas. El negocio
inyecta la **interfaz** `RepositorioTurnos`, nunca la implementación. Migrar a JPA consistió
en escribir tres clases nuevas; la capa de negocio y la de presentación no cambiaron. Las
implementaciones en memoria siguen en el repositorio, anotadas `@Alternative`, y se
reactivan descomentando tres líneas de `beans.xml`.

**¿Por qué Facade?**
Porque reservar un turno involucra a tres componentes —resolver el paciente, resolver el
profesional, agendar— y hay **dos** clientes que hacen exactamente esa secuencia: el alta
directa por REST y el asistente stateful. Sin la fachada, la coreografía estaría duplicada
en dos lugares que se irían desincronizando. Además evita que la capa de presentación tenga
que orquestar componentes de negocio.

**¿La fachada no termina siendo un cajón de sastre?**
No, porque **coordina pero no decide**. Cada regla la sigue aplicando su dueño:
`GestionPacientes` valida el padrón, `GestionTurnos` valida la fecha y la superposición,
`FabricaTurnos` valida la coherencia de la especialidad. La fachada sólo ordena las
llamadas.

**¿Qué evita el Factory?**
Un bug concreto que ya tuvimos. Un turno bien formado necesita estado inicial `PROGRAMADO`,
fecha truncada al minuto y especialidad coherente con el profesional. El truncado importa
porque la regla de superposición compara momentos: cuando la fecha era texto, `09:30` y
`09:30:00` eran el mismo instante pero dos cadenas distintas, y el sistema dejaba de
detectar el choque. Hoy hay **tres** caminos que crean turnos —REST, el asistente y la carga
de ejemplo— y los tres pasan por `FabricaTurnos`, así que la invariante está garantizada por
construcción.

**¿Por qué no implementaron Adapter?**
Porque Adapter resuelve hablar con sistemas externos heterogéneos detrás de una misma
interfaz, y eso es la Entrega 4: una obra social por SOAP y un partner por REST.
Implementarlo ahora, sin ningún sistema externo del otro lado, sería crear una clase vacía
para poder decir que el patrón existe.

---

## Seguridad

**¿Cuál es la diferencia entre autenticación y autorización?**
Autenticación responde *quién sos*: validar las credenciales y establecer una identidad. En
MediConecta la hace WildFly con el mecanismo FORM contra el dominio `ApplicationDomain`.
Autorización responde *qué podés hacer*: comprobar si esa identidad tiene permiso para una
operación concreta. La declaramos con `@RolesAllowed` sobre los métodos de negocio.

**¿Cómo funciona `@RolesAllowed`?**
Es una anotación declarativa: no ejecuta código nuestro. Cuando alguien invoca un método de
un EJB, el contenedor intercepta la llamada, compara los roles del `Principal` autenticado
con los de la anotación y, si no coincide ninguno, lanza `EJBAccessException` sin llegar a
ejecutar el método. Nosotros sólo la traducimos a un 403 con un `ExceptionMapper`.

**¿Por qué la anotación está en el negocio y no en el recurso REST?**
Porque puesta sobre el EJB vale para **cualquier** forma de invocación. Hoy el mismo método
`agendar` se llama desde el recurso REST y desde el asistente stateful; mañana podría
llamarse desde un consumidor de mensajería o un servicio SOAP. Si la restricción estuviera
en el recurso JAX-RS, valdría sólo para HTTP.

**Ocultan botones según el rol. ¿Eso no es seguridad falsa?**
Ocultar botones es comodidad, no seguridad, y lo decimos explícitamente en el código. La
seguridad real está en el servidor: si alguien llama al endpoint a mano igual recibe 403. Lo
podemos demostrar con `curl` en la defensa.

**¿Por qué FORM y no BASIC?**
Porque FORM permite usar la pantalla de login del prototipo; BASIC obliga al diálogo nativo
del navegador, que no se puede maquetar. El costo de FORM es que ante una petición sin
sesión el contenedor devuelve la página de login en vez de un 401, lo que confundiría a un
cliente JSON. Lo resolvimos sin salirnos del estándar: `<form-login-page>` apunta a un
servlet que responde 401 con JSON si el cliente pidió JSON, y redirige a la pantalla de
login si es un navegador.

**¿Dónde están las contraseñas de los usuarios?**
En `application-users.properties` de WildFly, gestionadas con `add-user.bat`. La aplicación
nunca las ve: el formulario hace POST a `j_security_check`, que es un endpoint del
contenedor. Son cuatro usuarios ficticios creados para la defensa.

**Un paciente podría pedir el historial de otro cambiando el documento en la URL. ¿Cómo lo
evitan?**
Ese caso lo declarativo no lo cubre, porque el método está habilitado para los cuatro roles.
Lo resolvemos combinando: la autorización declarativa deja pasar al método, y dentro
`GestionPacientes` hace una comprobación **programática** con
`SessionContext.getCallerPrincipal()` que verifica que, si el llamador sólo tiene rol
PACIENTE, el documento pedido sea el de su propia ficha. La asociación entre el usuario de
WildFly y la ficha está en la columna `usuario` de la tabla `pacientes`.

---

## Persistencia y transacciones

**¿Por qué JPA y no JDBC directo?**
Porque trabajamos con objetos del dominio y no con `ResultSet`. JPA nos da el mapeo
objeto-relacional, la gestión del ciclo de vida de las entidades y la integración con las
transacciones JTA del contenedor. Además, cambiar de motor de base de datos es cambiar el
dialecto, no reescribir consultas.

**¿Dónde vive la transacción?**
En el EJB. Cada método público de un `@Stateless` corre en su propia transacción JTA, con
`REQUIRED` por omisión: el contenedor la abre al entrar y hace commit al salir. Por eso los
repositorios nunca llaman a `begin()` ni a `commit()`. Y por eso
`FachadaReservas.reservar()` —que puede dar de alta un paciente y después agendar el turno—
hace las dos cosas en **una sola transacción**: si el turno falla, el paciente tampoco queda
registrado.

**¿Por qué las excepciones de negocio llevan `@ApplicationException(rollback = false)`?**
Porque sin esa anotación el contenedor trata cualquier `RuntimeException` que escape de un
EJB como un fallo *de sistema*: la envuelve en `EJBException`, escribe un stack trace
completo en el log, marca la transacción para rollback y descarta la instancia del pool.
Pero que alguien pida un turno en el pasado no es una falla del sistema, es un resultado de
negocio previsto. Con la anotación el contenedor la deja pasar tal cual.

**¿Qué pasaría si reemplazan PostgreSQL por otro motor?**
Prácticamente nada en el código. Habría que instalar el driver como módulo de WildFly,
cambiar la URL del datasource y, si hiciera falta, el dialecto de Hibernate. Ni el negocio
ni la presentación se enteran, porque dependen de las interfaces del repositorio. Lo mismo
vale al revés: ya tenemos dos implementaciones del mismo contrato conviviendo, JPA y
memoria.

**¿Por qué el datasource se define en WildFly y no en el WAR?**
Porque el usuario y la contraseña de la base son configuración del entorno, no del código.
Definiéndolo en el servidor no hay ninguna credencial de PostgreSQL en el repositorio, y
cada integrante puede apuntar a su propia base sin tocar el WAR.

**¿Quién crea las tablas?**
Hibernate, con `hbm2ddl.auto=update`, al desplegar. Para la cátedra dejamos también el DDL
equivalente documentado en `scripts/db/esquema.sql`, por si se prefiere crearlo a mano y
poner Hibernate en modo `validate`.

---

## Proyecto y herramientas

**¿Por qué la dependencia de Jakarta EE está en scope `provided`?**
Porque la implementación la aporta WildFly: RESTEasy para JAX-RS, Weld para CDI, Hibernate
para JPA, el contenedor de EJB. Si la empaquetáramos dentro del WAR habría clases duplicadas
en el classpath y el despliegue fallaría. El WAR pesa unos 120 kB, casi todo interfaz.

**¿Por qué no usaron Spring Boot?**
Porque la materia evalúa Jakarta EE y el contenedor de aplicaciones. Spring Boot ocultaría
justamente lo que hay que entender: el pool de EJB, las transacciones declarativas del
contenedor y la seguridad administrada por el servidor.

**¿Por qué el frontend no usa un framework?**
Porque lo que se evalúa es el backend. Un framework de frontend agregaría un paso de
compilación y cientos de dependencias sin aportar a la defensa. Usamos HTML semántico, CSS
propio y módulos ES nativos: una página y un módulo por pantalla, más tres módulos
compartidos.

**¿Cómo probaron que funciona?**
Con `scripts/smoke-test.ps1`, que ejecuta **61 verificaciones** contra la aplicación
desplegada: salud, seguridad por roles, turnos con sus reglas, pacientes, profesionales,
horarios libres y los cuatro pasos del asistente stateful. Además recorrimos manualmente las
ocho pantallas en escritorio y en teléfono.

**¿Usaron inteligencia artificial?**
Sí, y está documentado en la sección 12 del documento de la entrega, con el detalle de en
qué se usó, qué verificaciones hicimos y qué errores encontró esa verificación. El código no
se aceptó sin compilar, desplegar y probar.
