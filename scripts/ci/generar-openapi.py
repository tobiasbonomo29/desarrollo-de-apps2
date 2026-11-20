"""Genera src/main/webapp/openapi.json (contrato OpenAPI 3.1 de la API de MediConecta).

Uso:  python3 scripts/ci/generar-openapi.py src/main/webapp/openapi.json

Al agregar o cambiar un endpoint, editar este archivo y regenerar el contrato:
ContratoOpenApiTest falla si el contrato y los recursos JAX-RS no coinciden.
"""
import json, sys
TODOS = ["PACIENTE","RECEPCION","MEDICO","ADMIN"]
RESERVA = ["PACIENTE","RECEPCION","ADMIN"]
def ref(n): return {"$ref": "#/components/schemas/"+n}
def lista(n): return {"type":"array","items":ref(n)}
def json_ok(schema, desc="OK", headers=None):
    r={"description":desc,"content":{"application/json":{"schema":schema}}}
    if headers: r["headers"]=headers
    return r
def problemas(*codigos):
    return {str(c):{"$ref":"#/components/responses/Problema%d"%c} for c in codigos}
def op(resumen, roles, respuestas, descripcion=None, cuerpo=None, parametros=None, publico=False, tag=None, opid=None):
    o={"tags":[tag],"summary":resumen,"operationId":opid}
    d=(descripcion+"\n\n" if descripcion else "")
    if publico:
        o["security"]=[]
        d+="**Publico**: no requiere token."
    else:
        d+="**Roles:** "+", ".join(roles)+"."
        o["x-roles"]=roles
    o["description"]=d
    if parametros: o["parameters"]=parametros
    if cuerpo: o["requestBody"]={"required":True,"content":{"application/json":{"schema":cuerpo}}}
    resp=dict(respuestas)
    if not publico:
        resp.update(problemas(401,403))
    resp.update(problemas(500))
    o["responses"]=dict(sorted(resp.items()))
    return o
P_ID_TURNO={"$ref":"#/components/parameters/IdTurno"}
P_IF_MATCH={"$ref":"#/components/parameters/IfMatch"}
P_IF_NONE={"$ref":"#/components/parameters/IfNoneMatch"}
H_ETAG={"ETag":{"$ref":"#/components/headers/ETag"}}
R304={"304":{"description":"No cambio desde el ETag de If-None-Match (sin cuerpo)","headers":H_ETAG}}
P_DOC={"$ref":"#/components/parameters/Documento"}
P_ID_PROF={"$ref":"#/components/parameters/IdProfesional"}
def q(nombre, desc, requerido=False, formato=None, ejemplo=None):
    s={"type":"string"}
    if formato: s["format"]=formato
    p={"name":nombre,"in":"query","required":requerido,"description":desc,"schema":s}
    if ejemplo: p["example"]=ejemplo
    return p
paths={}
def agregar(ruta, metodo, o): paths.setdefault(ruta,{})[metodo]=o

# ---------- salud / auth / sesion ----------
agregar("/salud","get",op("Health check (readiness)",[],{"200":json_ok(ref("EstadoSalud"),"Base de datos y broker JMS responden"),"503":json_ok(ref("EstadoSalud"),"Alguna dependencia no responde")},
    "Verifica PostgreSQL y el broker JMS. Lo usan el HEALTHCHECK de Docker y el pipeline de CI.",publico=True,tag="Salud",opid="salud"))
agregar("/salud/vivo","get",op("Health check (liveness)",[],{"200":json_ok({"type":"object","properties":{"estado":{"type":"string","example":"UP"}}},"El WAR esta desplegado")},
    "No toca dependencias: solo confirma que la aplicacion atiende.",publico=True,tag="Salud",opid="saludVivo"))
agregar("/auth/login","post",op("Iniciar sesion (emite un JWT)",[],{"200":json_ok(ref("RespuestaLogin")),**problemas(400,401,429)},
    "Valida usuario y contrasena contra la tabla usuarios (hash PBKDF2) y devuelve un JWT HS256. "
    "**Fuerza bruta:** tras 5 intentos fallidos para un usuario (o 30 desde una IP) en 5 minutos responde `429` con `Retry-After`.",cuerpo=ref("PedidoLogin"),publico=True,tag="Sesion",opid="login"))
agregar("/sesion","get",op("Usuario de la sesion",TODOS,{"200":json_ok(ref("Sesion"))},tag="Sesion",opid="sesion"))
agregar("/sesion/logout","post",op("Cerrar sesion",TODOS,{"204":{"description":"Sesion HTTP del asistente liberada; el token lo descarta el cliente"}},tag="Sesion",opid="logout"))
agregar("/tablero","get",op("Resumen del dia",["RECEPCION","MEDICO","ADMIN"],{"200":json_ok(ref("ResumenAgenda"))},tag="Turnos",opid="tablero"))

# ---------- turnos ----------
agregar("/turnos","get",op("Agenda operativa",["RECEPCION","MEDICO","ADMIN"],{"200":json_ok(lista("Turno"))},"Turnos no archivados, ordenados por fecha.",tag="Turnos",opid="listarTurnos"))
agregar("/turnos","post",op("Reservar un turno",RESERVA,{
        "201":json_ok(ref("Turno"),"Turno creado (o el ya creado con la misma Idempotency-Key)",
            {"Location":{"description":"URL del turno","schema":{"type":"string","format":"uri"}},
             "Idempotent-Replayed":{"description":"`true` si no se creo nada: es el turno de un pedido anterior con la misma clave","schema":{"type":"string","enum":["true"]}}}),
        **problemas(400,404,409,422)},
    "Acepta el cuerpo corto (`documentoPaciente`, `profesionalId`, `fechaHora`) y el de la Entrega 1 (paciente embebido y nombre del profesional). "
    "Un PACIENTE solo puede reservar para si mismo.\n\n"
    "**Idempotencia:** con la cabecera `Idempotency-Key`, repetir el mismo pedido devuelve el mismo turno en lugar de crear otro (vigencia 24 h). "
    "Misma clave con otro cuerpo: `422`. Dos pedidos simultaneos con la misma clave: el segundo recibe `409`.",
    cuerpo=ref("PedidoTurno"),parametros=[{"$ref":"#/components/parameters/IdempotencyKey"}],tag="Turnos",opid="crearTurno"))
agregar("/turnos/{id}","get",op("Un turno",TODOS,{"200":json_ok(ref("Turno"),"El turno, con su ETag",H_ETAG),**R304,**problemas(400,404)},
    "Un PACIENTE solo puede consultar los suyos (`400`, BOLA). Devuelve `ETag`; con `If-None-Match` igual responde `304`.",parametros=[P_ID_TURNO,P_IF_NONE],tag="Turnos",opid="obtenerTurno"))
agregar("/turnos/{id}/notificaciones","get",op("Avisos generados por JMS para el turno",TODOS,{"200":json_ok(lista("Notificacion")),**problemas(400,404)},
    "Confirmacion (cola), recordatorio y aviso al profesional (topico). Los generan los consumidores JMS de forma asincronica: pueden tardar unos segundos en aparecer.",
    parametros=[P_ID_TURNO],tag="Turnos",opid="notificacionesDeTurno"))
for accion,roles,desc,opid in [("cancelar",TODOS,"PROGRAMADO -> CANCELADO. Un PACIENTE solo cancela los propios; un MEDICO solo los de su agenda.","cancelarTurno"),
                          ("atender",["MEDICO","ADMIN"],"PROGRAMADO -> ATENDIDO. Un MEDICO solo sobre su agenda.","atenderTurno"),
                          ("archivar",["RECEPCION","ADMIN"],"Saca de la agenda un turno CANCELADO sin borrarlo.","archivarTurno")]:
    agregar("/turnos/{id}/"+accion,"put",op(accion.capitalize()+" un turno",roles,{"200":json_ok(ref("Turno"),"El turno con su ETag nuevo",H_ETAG),**problemas(400,404,409,412)},
        desc+" `409` si la transicion no es valida (patron State). Con `If-Match` (opcional) solo se aplica si el turno sigue en esa version; si no, `412`.",parametros=[P_ID_TURNO,P_IF_MATCH],tag="Turnos",opid=opid))

# ---------- pacientes ----------
agregar("/pacientes","get",op("Padron de pacientes",["RECEPCION","MEDICO","ADMIN"],{"200":json_ok(lista("Paciente"))},parametros=[q("q","Filtra por nombre o documento")],tag="Pacientes",opid="listarPacientes"))
agregar("/pacientes","post",op("Registrar un paciente",RESERVA,{"201":json_ok(ref("Paciente"),"Paciente creado"),**problemas(400)},cuerpo=ref("Paciente"),tag="Pacientes",opid="registrarPaciente"))
agregar("/pacientes/mi-ficha","get",op("Ficha del paciente logueado",TODOS,{"200":json_ok(ref("Paciente")),**problemas(404)},tag="Pacientes",opid="miFicha"))
agregar("/pacientes/{documento}","get",op("Un paciente",TODOS,{"200":json_ok(ref("Paciente"),"La ficha, con su ETag",H_ETAG),**R304,**problemas(400,404)},
    "Un PACIENTE solo puede ver su propia ficha (`400`, BOLA). Devuelve `ETag`; con `If-None-Match` igual responde `304`.",parametros=[P_DOC,P_IF_NONE],tag="Pacientes",opid="obtenerPaciente"))
agregar("/pacientes/{documento}","put",op("Actualizar un paciente",["RECEPCION","ADMIN"],{"200":json_ok(ref("Paciente"),"La ficha con su ETag nuevo",H_ETAG),**problemas(400,404,409,412)},
    "Con `If-Match` (opcional) solo se aplica si la ficha sigue en esa version; si no, `412`.",cuerpo=ref("Paciente"),parametros=[P_DOC,P_IF_MATCH],tag="Pacientes",opid="actualizarPaciente"))
agregar("/pacientes/{documento}/turnos","get",op("Historial de turnos del paciente",TODOS,{"200":json_ok(lista("Turno")),**problemas(400,404)},"Un PACIENTE solo puede ver el suyo.",parametros=[P_DOC],tag="Pacientes",opid="historial"))
agregar("/pacientes/{documento}/cobertura","get",op("Validar la cobertura (SOAP o REST)",TODOS,{"200":json_ok(ref("ResultadoCobertura")),**problemas(400,404)},
    "PAMI, OSDE e IOMA se validan contra el sistema legado por SOAP; el resto contra el partner por REST (patron Adapter). "
    "**Timeout de 5 s con fallback:** si el sistema externo falla o no responde a tiempo se responde igual `200` con `contingencia: true`: "
    "el ultimo resultado verificado de las ultimas 24 h (`origen: CACHE`) o uno a verificar en la atencion (`origen: CONTINGENCIA`).",
    parametros=[P_DOC],tag="Pacientes",opid="validarCobertura"))

# ---------- profesionales ----------
agregar("/profesionales","get",op("Profesionales",TODOS,{"200":json_ok(lista("Profesional"))},parametros=[q("especialidad","Solo los de esa especialidad",ejemplo="Cardiologia")],tag="Profesionales",opid="listarProfesionales"))
agregar("/profesionales","post",op("Dar de alta un profesional",["ADMIN"],{"201":json_ok(ref("Profesional"),"Profesional creado"),**problemas(400)},cuerpo=ref("Profesional"),tag="Profesionales",opid="registrarProfesional"))
agregar("/profesionales/especialidades","get",op("Especialidades con profesionales activos",TODOS,{"200":json_ok({"type":"array","items":{"type":"string"}})},tag="Profesionales",opid="especialidades"))
agregar("/profesionales/mi-perfil","get",op("Ficha del medico logueado",["MEDICO"],{"200":json_ok(ref("Profesional")),**problemas(404)},tag="Profesionales",opid="miPerfil"))
agregar("/profesionales/{id}","get",op("Un profesional",TODOS,{"200":json_ok(ref("Profesional")),**problemas(404)},parametros=[P_ID_PROF],tag="Profesionales",opid="obtenerProfesional"))
agregar("/profesionales/{id}/disponibilidad","put",op("Reemplazar la agenda semanal",["RECEPCION","MEDICO","ADMIN"],{"200":json_ok(ref("Profesional")),**problemas(400,404)},
    "Un MEDICO solo puede editar la propia.",cuerpo=lista("Disponibilidad"),parametros=[P_ID_PROF],tag="Profesionales",opid="definirDisponibilidad"))
agregar("/profesionales/{id}/horarios-libres","get",op("Horarios libres de un dia",TODOS,{"200":json_ok({"type":"array","items":{"type":"string","example":"2026-11-24T09:30"}}),**problemas(400,404)},
    parametros=[P_ID_PROF,q("fecha","Dia (aaaa-MM-dd)",True,"date","2026-11-24")],tag="Profesionales",opid="horariosLibres"))

# ---------- reserva guiada ----------
R="Reserva guiada (@Stateful)"
agregar("/reserva","get",op("Estado de la reserva en curso",RESERVA,{"200":json_ok(ref("EstadoReserva"))},"Lo que recuerda el EJB @Stateful asociado a la sesion HTTP.",tag=R,opid="estadoReserva"))
agregar("/reserva","delete",op("Abandonar la reserva",RESERVA,{"200":json_ok(ref("EstadoReserva"))},tag=R,opid="cancelarReserva"))
agregar("/reserva/iniciar","post",op("Paso 0: identificar al paciente",RESERVA,{"200":json_ok(ref("EstadoReserva")),**problemas(400,404)},cuerpo=ref("PasoReserva"),tag=R,opid="iniciarReserva"))
agregar("/reserva/especialidad","put",op("Paso 1: elegir especialidad",RESERVA,{"200":json_ok(ref("EstadoReserva")),**problemas(400)},cuerpo=ref("PasoReserva"),tag=R,opid="elegirEspecialidad"))
agregar("/reserva/orientacion","post",op("Paso 1 con IA: sugerir especialidad (gateway de IA)",RESERVA,{"200":json_ok(ref("SugerenciaEspecialidad")),**problemas(400,429)},
    "La conversacion la manda el cliente (el servidor no la guarda). Solo se sugieren especialidades con profesionales activos. "
    "**Gateway:** maximo 10 consultas por minuto por usuario (`429` con `Retry-After`); si el modelo falla o no responde en 5 s se usa la orientacion "
    "de respaldo por palabras clave (`contingencia: true`), que tambien detecta emergencias.",cuerpo=ref("PedidoOrientacion"),tag=R,opid="orientarEspecialidad"))
agregar("/reserva/profesional","put",op("Paso 2: elegir profesional",RESERVA,{"200":json_ok(ref("EstadoReserva")),**problemas(400,404)},cuerpo=ref("PasoReserva"),tag=R,opid="elegirProfesional"))
agregar("/reserva/horarios","get",op("Horarios libres del profesional elegido",RESERVA,{"200":json_ok({"type":"array","items":{"type":"string","example":"2026-11-24T09:30"}}),**problemas(400)},
    parametros=[q("fecha","Dia (aaaa-MM-dd)",True,"date","2026-11-24")],tag=R,opid="horariosReserva"))
agregar("/reserva/horario","put",op("Paso 3: elegir horario",RESERVA,{"200":json_ok(ref("EstadoReserva")),**problemas(400)},cuerpo=ref("PasoReserva"),tag=R,opid="elegirHorario"))
agregar("/reserva/volver","put",op("Volver un paso atras",RESERVA,{"200":json_ok(ref("EstadoReserva")),**problemas(400)},tag=R,opid="volverPaso"))
agregar("/reserva/confirmar","post",op("Paso 4: confirmar y crear el turno",RESERVA,{"200":json_ok(ref("EstadoReserva")),**problemas(400)},
    "Crea el turno por la misma fachada que POST /turnos y descarta el EJB @Stateful. Un segundo confirmar no duplica: la conversacion ya termino.",tag=R,opid="confirmarReserva"))

# ---------- mensajeria ----------
M="Mensajeria JMS"
agregar("/mensajeria/mensajes-fallidos","get",op("Mensajes en la DLQ",["ADMIN"],{"200":json_ok(lista("MensajeFallido"))},
    "Mensajes que agotaron los reintentos y llegaron a la cola MensajesFallidos.",tag=M,opid="mensajesFallidos"))
agregar("/mensajeria/mensaje-defectuoso","post",op("Enviar un mensaje que ningun consumidor entiende",["ADMIN"],
    {"202":json_ok({"type":"object","properties":{"eventoId":{"type":"string"}}},"Aceptado: los reintentos y el pase a la DLQ ocurren de forma asincronica")},
    "Demostracion en vivo de reintentos con backoff y DLQ.",tag=M,opid="enviarMensajeDefectuoso"))

s={"type":"string"}; i64={"type":"integer","format":"int64"}; fh={"type":"string","example":"2026-11-24T09:30","description":"aaaa-MM-ddTHH:mm"}
schemas={
 "Problema":{"type":"object","description":"Problem Details (RFC 9457). `type` identifica el error; `detail` es para personas.",
   "required":["type","title","status"],
   "properties":{"type":{"type":"string","format":"uri","example":"urn:mediconecta:problema:regla-negocio",
      "description":"urn:mediconecta:problema:{regla-negocio|pedido-invalido|no-autenticado|credenciales-invalidas|acceso-denegado|no-encontrado|transicion-invalida|idempotencia-en-curso|idempotencia-reutilizada|modificacion-concurrente|precondicion-fallida|demasiadas-solicitudes|integracion-externa|tiempo-agotado|error-interno}, o about:blank para errores HTTP genericos"},
     "title":{"type":"string","example":"Regla de negocio incumplida"},"status":{"type":"integer","example":400},
     "detail":{"type":"string","example":"El profesional ya tiene un turno en ese horario."},
     "instance":{"type":"string","example":"/mediconecta/api/turnos"},
     "idError":{"type":"string","description":"Solo en los 500: el mismo identificador queda en el log del servidor"}}},
 "Paciente":{"type":"object","required":["documento","nombre"],"properties":{"id":i64,"documento":{"type":"string","example":"30111222"},"nombre":{"type":"string","example":"Ana Gomez"},
     "email":{"type":"string","format":"email"},"telefono":s,"cobertura":{"type":"string","example":"OSDE 210"},"usuario":{"type":"string","nullable":True},"activo":{"type":"boolean"}}},
 "Disponibilidad":{"type":"object","properties":{"id":i64,"diaSemana":{"type":"string","enum":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"]},
     "horaDesde":{"type":"string","example":"09:00:00"},"horaHasta":{"type":"string","example":"13:00:00"},"duracionMinutos":{"type":"integer","example":30}}},
 "Profesional":{"type":"object","required":["nombre","especialidad","matricula"],"properties":{"id":i64,"nombre":{"type":"string","example":"Dra. Laura Fernandez"},"especialidad":{"type":"string","example":"Cardiologia"},
     "matricula":{"type":"string","example":"MP-10234"},"email":s,"activo":{"type":"boolean"},"usuario":{"type":"string","nullable":True},"disponibilidades":lista("Disponibilidad")}},
 "Turno":{"type":"object","properties":{"id":i64,"paciente":ref("Paciente"),"profesional":ref("Profesional"),"especialidad":s,"fechaHora":fh,
     "estado":{"type":"string","enum":["PROGRAMADO","ATENDIDO","CANCELADO"]},"motivo":s,"archivado":{"type":"boolean"}}},
 "PedidoTurno":{"type":"object","description":"Forma corta (la de la interfaz) o la de la Entrega 1 (paciente embebido y profesional por nombre).",
   "properties":{"documentoPaciente":{"type":"string","example":"30111222"},"nombrePaciente":{"type":"string","description":"Solo para dar de alta al paciente si no existe"},
     "profesionalId":{"type":"integer","format":"int64","example":1},"profesional":{"type":"string","description":"Nombre del profesional (forma de la Entrega 1)"},
     "paciente":{"type":"object","properties":{"nombre":s,"documento":s}},"especialidad":{"type":"string","example":"Cardiologia"},"fechaHora":fh,"motivo":{"type":"string","example":"Control anual"}}},
 "PedidoLogin":{"type":"object","required":["usuario","clave"],"properties":{"usuario":{"type":"string","example":"recepcion"},"clave":{"type":"string","format":"password"}}},
 "RespuestaLogin":{"type":"object","properties":{"token":{"type":"string"},"tipo":{"type":"string","example":"Bearer"},"expiraEnSegundos":{"type":"integer","example":7200},
     "usuario":s,"roles":{"type":"array","items":s}}},
 "Sesion":{"type":"object","properties":{"autenticado":{"type":"boolean"},"usuario":s,"roles":{"type":"array","items":s}}},
 "ResumenAgenda":{"type":"object","properties":{"turnosDeHoy":{"type":"integer"},"pacientesActivos":{"type":"integer"},"profesionalesActivos":{"type":"integer"},
     "pendientes":{"type":"integer"},"proximosTurnos":lista("Turno")}},
 "ResultadoCobertura":{"type":"object","properties":{"vigente":{"type":"boolean"},"plan":s,"copago":{"type":"number"},
     "origen":{"type":"string","enum":["LEGADO","PARTNER","NINGUNA","CACHE","CONTINGENCIA"]},
     "contingencia":{"type":"boolean","description":"true si el sistema externo no respondio y el resultado es de respaldo"},"mensaje":{"type":"string","nullable":True}}},
 "PasoReserva":{"type":"object","description":"Cada paso usa solo su campo.","properties":{"documentoPaciente":s,"especialidad":s,"profesionalId":i64,"fechaHora":fh}},
 "EstadoReserva":{"type":"object","properties":{"idConversacion":s,"instancia":s,"paso":{"type":"integer"},
     "pasoActual":{"type":"string","enum":["SIN_INICIAR","ELEGIR_ESPECIALIDAD","ELEGIR_PROFESIONAL","ELEGIR_HORARIO","CONFIRMAR","FINALIZADA"]},
     "iniciadaEn":s,"documentoPaciente":s,"nombrePaciente":s,"especialidad":s,"profesionalId":i64,"nombreProfesional":s,"fechaHora":fh,"finalizada":{"type":"boolean"},"turnoId":i64}},
 "MensajeChat":{"type":"object","required":["rol","texto"],"properties":{"rol":{"type":"string","enum":["usuario","asistente"]},"texto":s}},
 "PedidoOrientacion":{"type":"object","required":["mensajes"],"properties":{"mensajes":lista("MensajeChat")}},
 "SugerenciaEspecialidad":{"type":"object","properties":{"mensaje":s,"especialidad":{"type":"string","nullable":True},"urgente":{"type":"boolean"},
     "contingencia":{"type":"boolean","description":"true si respondio la orientacion de respaldo y no el modelo de IA"}}},
 "Notificacion":{"type":"object","properties":{"id":i64,"eventoId":s,"tipo":{"type":"string","enum":["CONFIRMACION","RECORDATORIO","AVISO_PROFESIONAL"]},
     "canal":{"type":"string","enum":["EMAIL","SMS","AGENDA"]},"turnoId":i64,"destinatario":s,"mensaje":s,"creadaEn":fh,"programadaPara":{**fh,"nullable":True}}},
 "MensajeFallido":{"type":"object","properties":{"id":i64,"mensajeId":s,"eventoId":{"type":"string","nullable":True},"destinoOriginal":s,"cuerpo":s,"recibidoEn":{"type":"string","example":"2026-11-19T13:05:12"}}},
 "EstadoSalud":{"type":"object","properties":{"estado":{"type":"string","enum":["UP","DOWN"]},
     "componentes":{"type":"object","additionalProperties":{"type":"string","enum":["UP","DOWN"]},"example":{"baseDeDatos":"UP","mensajeria":"UP"}},
     "verificadoEn":{"type":"string","format":"date-time"}}},
}
desc_resp={400:"Pedido invalido o regla de negocio incumplida",401:"Falta el token o es invalido",403:"El rol no tiene permiso",404:"No existe el recurso",
   409:"Conflicto con el estado actual (transicion invalida, pedido repetido en curso o modificacion concurrente)",412:"If-Match no coincide con la version actual del recurso",422:"Idempotency-Key reutilizada con otro pedido",429:"Demasiadas solicitudes (fuerza bruta en el login o limite del gateway de IA)",
   500:"Error inesperado (sin detalles internos; ver idError)",502:"Fallo un sistema externo",504:"Un sistema externo no respondio a tiempo"}
responses={"Problema%d"%c:{"description":d,"content":{"application/problem+json":{"schema":ref("Problema")}}} for c,d in desc_resp.items()}
responses["Problema429"]["headers"]={"Retry-After":{"description":"Segundos a esperar","schema":{"type":"integer"}}}
doc={
 "openapi":"3.1.0",
 "info":{"title":"API de MediConecta","version":"3.1.0",
   "description":"Gestion de turnos medicos (TP integrador, Desarrollo de Aplicaciones II - UADE).\n\n"
     "* **Autenticacion:** `POST /auth/login` devuelve un JWT; el resto de las operaciones lo esperan en `Authorization: Bearer <token>`.\n"
     "* **Autorizacion:** por rol, en la capa de negocio (`@RolesAllowed`). Cada operacion indica sus roles (tambien en `x-roles`).\n"
     "* **Errores:** siempre `application/problem+json` (RFC 9457).\n"
     "* **Idempotencia:** `POST /turnos` acepta `Idempotency-Key`.\n"
     "* **Concurrencia optimista:** turnos y pacientes devuelven `ETag`; `If-None-Match` da `304` y `If-Match` con una version vieja da `412`.\n"
     "* **Limites:** `429` con `Retry-After` ante fuerza bruta en el login y en el gateway de IA.\n\n"
     "Este documento lo valida un test unitario (ContratoOpenApiTest): si se agrega un endpoint sin documentarlo, el build falla."},
 "servers":[{"url":"/mediconecta/api","description":"Este servidor"}],
 "tags":[{"name":n} for n in ["Salud","Sesion","Turnos","Pacientes","Profesionales",R,M]],
 "security":[{"bearerAuth":[]}],
 "paths":dict(sorted(paths.items())),
 "components":{
   "securitySchemes":{"bearerAuth":{"type":"http","scheme":"bearer","bearerFormat":"JWT"}},
   "parameters":{
     "IdTurno":{"name":"id","in":"path","required":True,"schema":i64},
     "IdProfesional":{"name":"id","in":"path","required":True,"schema":i64},
     "Documento":{"name":"documento","in":"path","required":True,"schema":{"type":"string","example":"30111222"}},
     "IfMatch":{"name":"If-Match","in":"header","required":False,"description":"ETag de la version que el cliente conoce; si cambio, 412","schema":{"type":"string","example":"\"turno-42-v3\""}},
     "IfNoneMatch":{"name":"If-None-Match","in":"header","required":False,"description":"ETag de la version en cache; si no cambio, 304","schema":{"type":"string","example":"\"turno-42-v3\""}},
     "IdempotencyKey":{"name":"Idempotency-Key","in":"header","required":False,
        "description":"Clave unica por intento de reserva (se recomienda un UUID). Repetir el pedido con la misma clave no crea otro turno.",
        "schema":{"type":"string","minLength":8,"maxLength":100,"pattern":"^[A-Za-z0-9._:-]{8,100}$","example":"5f0c7d2e-8b1a-4f3e-9c4d-2a6b7e8f9a01"}}},
   "headers":{"ETag":{"description":"Version del recurso (\"<recurso>-<id>-v<version>\")","schema":{"type":"string"}}},
   "responses":responses,
   "schemas":schemas}}
json.dump(doc, open(sys.argv[1],"w",encoding="utf-8"), ensure_ascii=False, indent=2)
open(sys.argv[1],"a").write("\n")
print(len(paths),"rutas,",sum(len(v) for v in paths.values()),"operaciones")
