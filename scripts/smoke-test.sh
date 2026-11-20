#!/usr/bin/env bash
# =============================================================================
#  MediConecta - prueba de humo de punta a punta (bash + curl + jq)
# =============================================================================
#
#  Recorre la aplicacion desplegada como lo haria un cliente real: salud,
#  cabeceras de seguridad, contrato OpenAPI, autenticacion JWT, roles,
#  Problem Details, pacientes, profesionales, turnos (patron State, ownership,
#  Idempotency-Key), cobertura SOAP/REST, asistente @Stateful, consumidores
#  JMS y la cola de mensajes muertos (DLQ).
#
#  Es la que corre el pipeline de CI contra Docker Compose. Funciona en Linux
#  y en macOS. En Windows esta la version PowerShell: scripts/smoke-test.ps1.
#
#  Uso:
#      scripts/smoke-test.sh                                  # http://localhost:8080/mediconecta
#      scripts/smoke-test.sh http://localhost:8180/mediconecta
#      MEDICONECTA_DEMO_CLAVE=otra scripts/smoke-test.sh
#
#  Devuelve 0 si todas las pruebas pasan y 1 si alguna falla.
# =============================================================================

set -o pipefail

BASE_URL="${1:-http://localhost:8080/mediconecta}"
CLAVE="${MEDICONECTA_DEMO_CLAVE:-Demo.2026}"

for herramienta in curl jq; do
    command -v "$herramienta" > /dev/null || { echo "Falta $herramienta en el PATH."; exit 1; }
done

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

OK=0
FALLOS=0
CODIGO=""
TEXTO=""

if [ -t 1 ]; then VERDE=$'\e[32m'; ROJO=$'\e[31m'; CIAN=$'\e[36m'; NORMAL=$'\e[0m'; else VERDE=""; ROJO=""; CIAN=""; NORMAL=""; fi

# ------------------------------------------------------------------ util ----

titulo() {
    printf '\n%s%s%s\n' "$CIAN" "$1" "$NORMAL"
}

# probar "nombre" "detalle si falla" 'condicion de bash'
probar() {
    local nombre="$1" detalle="$2" condicion="$3"
    if eval "$condicion"; then
        OK=$((OK + 1))
        printf '  %s[OK]%s    %s\n' "$VERDE" "$NORMAL" "$nombre"
    else
        FALLOS=$((FALLOS + 1))
        printf '  %s[FALLA]%s %s  %s\n' "$ROJO" "$NORMAL" "$nombre" "$detalle"
    fi
}

# invocar METODO RUTA [CUERPO] [SESION] [cabecera extra...]
# Deja el codigo HTTP en $CODIGO, el cuerpo en $TEXTO y las cabeceras en $TMP/cabeceras.
invocar() {
    local metodo="$1" ruta="$2" cuerpo="${3:-}" sesion="${4:-}"
    shift 4 2> /dev/null || shift $#
    local args=(-s -X "$metodo" -o "$TMP/cuerpo" -D "$TMP/cabeceras" -w '%{http_code}'
                -H 'Accept: application/json' -H 'X-Requested-With: XMLHttpRequest'
                -H 'Content-Type: application/json')
    if [ -n "$sesion" ]; then
        local token_var="TOKEN_$sesion"
        args+=(-H "Authorization: Bearer ${!token_var}" -b "$TMP/jar_$sesion" -c "$TMP/jar_$sesion")
    fi
    for extra in "$@"; do args+=(-H "$extra"); done
    [ -n "$cuerpo" ] && args+=(--data-binary "$cuerpo")
    : > "$TMP/cuerpo"
    CODIGO="$(curl "${args[@]}" "$BASE_URL$ruta" || echo 000)"
    TEXTO="$(cat "$TMP/cuerpo" 2> /dev/null)"
}

# dato '.campo'  -> valor del JSON de la ultima respuesta (vacio si no hay)
dato() {
    jq -r "$1 // empty" <<< "$TEXTO" 2> /dev/null
}

cabecera() {
    grep -i "^$1:" "$TMP/cabeceras" | head -1 | cut -d' ' -f2- | tr -d '\r'
}

abrir_sesion() {
    local usuario="$1"
    invocar POST /api/auth/login "$(jq -nc --arg u "$usuario" --arg c "$CLAVE" '{usuario:$u, clave:$c}')"
    printf -v "TOKEN_$usuario" '%s' "$(dato .token)"
    : > "$TMP/jar_$usuario"
}

fecha_mas() {
    date -d "+$1 day" +%F 2> /dev/null || date -v+"$1"d +%F
}

# primer_horario PROFESIONAL [indice] -> primer horario libre en las proximas 3 semanas
primer_horario() {
    local profesional="$1" indice="${2:-0}" i
    for i in $(seq 1 21); do
        invocar GET "/api/profesionales/$profesional/horarios-libres?fecha=$(fecha_mas "$i")" "" recepcion
        local horario
        horario="$(jq -r ".[$indice] // empty" <<< "$TEXTO" 2> /dev/null)"
        if [ "$CODIGO" = 200 ] && [ -n "$horario" ]; then
            echo "$horario"
            return
        fi
    done
}

aleatorio() {
    echo $(( $1 + (RANDOM * 32768 + RANDOM) % ($2 - $1) ))
}

# ================================================================== SALUD ===

titulo "SALUD (publico, sin autenticacion)"

invocar GET /api/salud
if [ "$CODIGO" = 000 ]; then
    echo "No se pudo contactar la aplicacion en $BASE_URL. Esta desplegada?"
    exit 1
fi
probar "GET /api/salud responde 200" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 200 ]'
probar "El estado general es UP" "$TEXTO" '[ "$(dato .estado)" = UP ]'
probar "La base de datos responde" "$TEXTO" '[ "$(dato .componentes.baseDeDatos)" = UP ]'
probar "El broker JMS responde" "$TEXTO" '[ "$(dato .componentes.mensajeria)" = UP ]'
invocar GET /api/salud/vivo
probar "GET /api/salud/vivo (liveness) responde 200" "codigo=$CODIGO" '[ "$CODIGO" = 200 ] && [ "$(dato .estado)" = UP ]'

# ==================================================== CABECERAS DE SEGURIDAD ==

titulo "CABECERAS DE SEGURIDAD"

invocar GET /app/turnos.html
CSP="$(cabecera Content-Security-Policy)"
probar "Las pantallas tienen Content-Security-Policy estricta" "$CSP" \
    '[[ "$CSP" == *"script-src '"'"'self'"'"'"* && "$CSP" != *unsafe-inline* && "$CSP" == *"frame-ancestors '"'"'none'"'"'"* ]]'
probar "X-Content-Type-Options: nosniff" "" '[ "$(cabecera X-Content-Type-Options)" = nosniff ]'
probar "X-Frame-Options: DENY" "" '[ "$(cabecera X-Frame-Options)" = DENY ]'
probar "Referrer-Policy: no-referrer" "" '[ "$(cabecera Referrer-Policy)" = no-referrer ]'
probar "Permissions-Policy apaga camara, microfono y ubicacion" "" '[[ "$(cabecera Permissions-Policy)" == *"camera=()"* ]]'
invocar GET /api/salud
probar "La API no se cachea (Cache-Control: no-store)" "" '[ "$(cabecera Cache-Control)" = no-store ]'
probar "La API tiene CSP default-src 'none'" "" '[[ "$(cabecera Content-Security-Policy)" == "default-src '"'"'none'"'"'"* ]]'

# ============================================================ DOCUMENTACION ==

titulo "CONTRATO OPENAPI"

invocar GET /openapi.json
probar "GET /openapi.json publica el contrato OpenAPI 3.1" "codigo=$CODIGO" \
    '[ "$CODIGO" = 200 ] && [[ "$(dato .openapi)" == 3.1* ]]'
probar "El contrato documenta todas las operaciones" "" '[ "$(jq "[.paths[] | keys[]] | length" <<< "$TEXTO")" -ge 39 ]'
invocar GET /api-docs.html
probar "GET /api-docs.html sirve la documentacion interactiva" "codigo=$CODIGO" '[ "$CODIGO" = 200 ]'

# ============================================================== SEGURIDAD ===

titulo "SEGURIDAD"

invocar GET /api/turnos
probar "Endpoint protegido sin iniciar sesion devuelve 401" "codigo=$CODIGO" '[ "$CODIGO" = 401 ]'
probar "El 401 viene en Problem Details" "$(cabecera Content-Type)" \
    '[[ "$(cabecera Content-Type)" == application/problem+json* ]] && [ "$(dato .type)" = urn:mediconecta:problema:no-autenticado ]'

invocar POST /api/auth/login '{"usuario":"recepcion","clave":"incorrecta"}'
probar "Login con contrasena incorrecta devuelve 401" "codigo=$CODIGO" '[ "$CODIGO" = 401 ]'

invocar POST /api/auth/login "$(jq -nc --arg c "$CLAVE" '{usuario:"recepcion", clave:$c}')"
probar "Login correcto devuelve un JWT (tres partes)" "codigo=$CODIGO" \
    '[ "$CODIGO" = 200 ] && [ "$(dato .token | tr "." "\n" | wc -l | tr -d " ")" = 3 ]'
probar "El login informa los roles del usuario" "" '[ "$(dato ".roles | index(\"RECEPCION\")")" != "" ]'

for usuario in recepcion medico admin paciente; do abrir_sesion "$usuario"; done

invocar GET /api/sesion "" recepcion
probar "Con el JWT de 'recepcion' el servidor reconoce al usuario" "codigo=$CODIGO" \
    '[ "$CODIGO" = 200 ] && [ "$(dato .usuario)" = recepcion ]'

TOKEN_adulterado="${TOKEN_recepcion}x"
: > "$TMP/jar_adulterado"
invocar GET /api/turnos "" adulterado
probar "Un token con la firma alterada devuelve 401" "codigo=$CODIGO" '[ "$CODIGO" = 401 ]'

invocar GET /api/turnos "" paciente
probar "Un PACIENTE no puede ver la agenda completa (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
probar "El 403 viene en Problem Details" "$TEXTO" '[ "$(dato .type)" = urn:mediconecta:problema:acceso-denegado ]'

MATRICULA="MP-$(aleatorio 60000 99999)"
NUEVO_PROFESIONAL="$(jq -nc --arg m "$MATRICULA" '{nombre:"Dr. Prueba Humo", especialidad:"Dermatologia", matricula:$m}')"
invocar POST /api/profesionales "$NUEVO_PROFESIONAL" recepcion
probar "Alta de profesional con rol RECEPCION devuelve 403" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
invocar POST /api/profesionales "$NUEVO_PROFESIONAL" admin
probar "Alta de profesional con rol ADMIN devuelve 201" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 201 ]'
ID_NUEVO="$(dato .id)"

if [ -n "$ID_NUEVO" ]; then
    FRANJAS='[{"diaSemana":"MONDAY","horaDesde":"08:00","horaHasta":"12:00","duracionMinutos":30}]'
    invocar PUT "/api/profesionales/$ID_NUEVO/disponibilidad" "$FRANJAS" paciente
    probar "Definir disponibilidad con rol PACIENTE devuelve 403" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
    invocar PUT "/api/profesionales/$ID_NUEVO/disponibilidad" "$FRANJAS" recepcion
    probar "RECEPCION puede definir la disponibilidad de cualquier profesional" "codigo=$CODIGO" '[ "$CODIGO" = 200 ]'
    invocar PUT "/api/profesionales/$ID_NUEVO/disponibilidad" "$FRANJAS" medico
    probar "Un MEDICO no puede definir la disponibilidad de otro profesional (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
    invocar GET /api/profesionales/mi-perfil "" medico
    probar "GET /api/profesionales/mi-perfil devuelve la ficha del medico logueado" "codigo=$CODIGO" \
        '[ "$CODIGO" = 200 ] && [[ "$(dato .nombre)" == *Fernandez* ]]'
    MI_PERFIL="$TEXTO"
    invocar PUT "/api/profesionales/$(jq -r .id <<< "$MI_PERFIL")/disponibilidad" \
        "$(jq -c '.disponibilidades' <<< "$MI_PERFIL")" medico
    probar "Un MEDICO si puede definir su propia disponibilidad" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 200 ]'
fi

# ====================================================== PROBLEM DETAILS =====

titulo "ERRORES EN PROBLEM DETAILS (RFC 9457)"

invocar GET /api/no-existe "" recepcion
probar "Una ruta inexistente devuelve 404 en application/problem+json" "codigo=$CODIGO $(cabecera Content-Type)" \
    '[ "$CODIGO" = 404 ] && [[ "$(cabecera Content-Type)" == application/problem+json* ]] && [ "$(dato .type)" = about:blank ]'
invocar POST /api/turnos '{"documentoPaciente": 30111222, ' recepcion
probar "Un JSON roto devuelve 400 pedido-invalido (no 500)" "codigo=$CODIGO $TEXTO" \
    '[ "$CODIGO" = 400 ] && [ "$(dato .type)" = urn:mediconecta:problema:pedido-invalido ]'
invocar DELETE /api/tablero "" recepcion
probar "Un metodo no permitido devuelve 405 con la cabecera Allow" "codigo=$CODIGO" \
    '[ "$CODIGO" = 405 ] && [ -n "$(cabecera Allow)" ]'

# ============================================================ PROFESIONALES ==

titulo "PROFESIONALES"

invocar GET /api/profesionales "" recepcion
probar "GET /api/profesionales devuelve la lista" "codigo=$CODIGO" '[ "$CODIGO" = 200 ] && [ "$(jq length <<< "$TEXTO")" -ge 4 ]'

invocar GET "/api/profesionales?especialidad=Cardiologia" "" recepcion
CARDIO="$TEXTO"
probar "El filtro por especialidad devuelve solo Cardiologia" "" \
    '[ "$CODIGO" = 200 ] && [ "$(jq "length" <<< "$CARDIO")" -ge 1 ] && [ "$(jq "all(.especialidad == \"Cardiologia\")" <<< "$CARDIO")" = true ]'
PROFESIONAL_ID="$(jq -r '.[0].id' <<< "$CARDIO")"
PROFESIONAL_NOMBRE="$(jq -r '.[0].nombre' <<< "$CARDIO")"

invocar GET /api/profesionales/especialidades "" recepcion
probar "GET /api/profesionales/especialidades devuelve valores" "" '[ "$CODIGO" = 200 ] && [ "$(jq length <<< "$TEXTO")" -ge 1 ]'

HORARIO="$(primer_horario "$PROFESIONAL_ID")"
probar "GET /api/profesionales/{id}/horarios-libres devuelve horarios" "no se encontraron horarios en 21 dias" '[ -n "$HORARIO" ]'

invocar GET "/api/profesionales/$PROFESIONAL_ID/horarios-libres?fecha=18-09-2026" "" recepcion
probar "Una fecha mal formada devuelve 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'
invocar GET /api/profesionales/999999 "" recepcion
probar "GET de un profesional inexistente devuelve 404" "codigo=$CODIGO" '[ "$CODIGO" = 404 ]'

# ================================================================ PACIENTES ==

titulo "PACIENTES"

invocar GET /api/pacientes "" recepcion
probar "GET /api/pacientes devuelve el padron" "codigo=$CODIGO" '[ "$CODIGO" = 200 ] && [ "$(jq length <<< "$TEXTO")" -ge 4 ]'

DOCUMENTO="$(aleatorio 40000000 49999999)"
NUEVO_PACIENTE="$(jq -nc --arg d "$DOCUMENTO" '{nombre:"Paciente De Prueba", documento:$d, email:"prueba@mail.com", cobertura:"OSDE 210"}')"
invocar POST /api/pacientes "$NUEVO_PACIENTE" recepcion
probar "POST /api/pacientes registra un paciente (201)" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 201 ]'
invocar POST /api/pacientes "$NUEVO_PACIENTE" recepcion
probar "Un documento duplicado devuelve 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'
invocar POST /api/pacientes '{"nombre":"Sin Documento"}' recepcion
probar "Un paciente sin documento devuelve 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'
invocar GET "/api/pacientes/$DOCUMENTO" "" recepcion
probar "GET /api/pacientes/{documento} encuentra al paciente" "" '[ "$CODIGO" = 200 ] && [ "$(dato .documento)" = "$DOCUMENTO" ]'
invocar GET /api/pacientes/00000000 "" recepcion
probar "GET de un paciente inexistente devuelve 404" "codigo=$CODIGO" '[ "$CODIGO" = 404 ]'
invocar PUT "/api/pacientes/$DOCUMENTO" '{"nombre":"Paciente Modificado","telefono":"11-1111-2222","cobertura":"Swiss Medical","activo":true}' recepcion
probar "PUT /api/pacientes/{documento} actualiza los datos" "codigo=$CODIGO" '[ "$CODIGO" = 200 ] && [ "$(dato .nombre)" = "Paciente Modificado" ]'
invocar GET "/api/pacientes/$DOCUMENTO/turnos" "" recepcion
probar "GET /api/pacientes/{documento}/turnos devuelve el historial" "codigo=$CODIGO" '[ "$CODIGO" = 200 ]'

# =================================================================== TURNOS ==

titulo "TURNOS"

invocar GET /api/turnos "" recepcion
probar "GET /api/turnos devuelve la agenda" "codigo=$CODIGO" '[ "$CODIGO" = 200 ] && [ "$(jq length <<< "$TEXTO")" -ge 3 ]'
PRIMER_ID="$(jq -r '.[0].id' <<< "$TEXTO")"
invocar GET "/api/turnos/$PRIMER_ID" "" recepcion
probar "GET /api/turnos/{id} devuelve un turno existente" "" '[ "$CODIGO" = 200 ] && [ "$(dato .id)" = "$PRIMER_ID" ]'

invocar GET /api/turnos/999999 "" recepcion
probar "GET /api/turnos/999999 devuelve 404" "codigo=$CODIGO" '[ "$CODIGO" = 404 ]'
probar "El 404 trae el mensaje de negocio en detail" "$TEXTO" '[[ "$(dato .detail)" == *"No existe un turno"* ]]'

PEDIDO="$(jq -nc --arg d "$DOCUMENTO" --argjson p "$PROFESIONAL_ID" --arg f "$HORARIO" '{documentoPaciente:$d, profesionalId:$p, fechaHora:$f, motivo:"Prueba de humo"}')"
invocar POST /api/turnos "$PEDIDO" recepcion
probar "POST /api/turnos agenda un turno (201)" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 201 ]'
probar "La respuesta trae Location" "" '[[ "$(cabecera Location)" == */api/turnos/* ]]'
probar "El turno nace en estado PROGRAMADO" "estado=$(dato .estado)" '[ "$(dato .estado)" = PROGRAMADO ]'
ID_CREADO="$(dato .id)"

invocar POST /api/turnos "$PEDIDO" recepcion
probar "Un turno superpuesto del mismo profesional devuelve 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'
probar "El mensaje explica la superposicion" "$TEXTO" '[[ "$(dato .detail)" == *"ya tiene un turno en ese horario"* ]] && [ "$(dato .type)" = urn:mediconecta:problema:regla-negocio ]'

invocar POST /api/turnos "$(jq -nc --arg d "$DOCUMENTO" --argjson p "$PROFESIONAL_ID" '{documentoPaciente:$d, profesionalId:$p, fechaHora:"2020-01-06T10:00"}')" recepcion
probar "Un turno en el pasado devuelve 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ] && [[ "$(dato .detail)" == *pasado* ]]'
invocar POST /api/turnos "$(jq -nc --arg d "$DOCUMENTO" --argjson p "$PROFESIONAL_ID" '{documentoPaciente:$d, profesionalId:$p, fechaHora:"15/12/2026"}')" recepcion
probar "Una fecha con formato invalido devuelve 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'
invocar POST /api/turnos "$(jq -nc --argjson p "$PROFESIONAL_ID" --arg f "$HORARIO" '{profesionalId:$p, fechaHora:$f}')" recepcion
probar "Un turno sin paciente devuelve 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'

HORARIO_E1="$(primer_horario "$PROFESIONAL_ID")"
invocar POST /api/turnos "$(jq -nc --arg n "$PROFESIONAL_NOMBRE" --arg f "$HORARIO_E1" --arg d "$(aleatorio 50000000 59999999)" \
    '{paciente:{nombre:"Compatibilidad Entrega1", documento:$d}, profesional:$n, especialidad:"Cardiologia", fechaHora:$f}')" recepcion
probar "El cuerpo JSON de la Entrega 1 sigue funcionando (201)" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 201 ]'

invocar GET "/api/turnos/$ID_CREADO" "" paciente
probar "Un PACIENTE no puede leer el turno de otro paciente por id (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
invocar PUT "/api/turnos/$ID_CREADO/cancelar" "" paciente
probar "Un PACIENTE no puede cancelar el turno de otro paciente (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
invocar PUT "/api/turnos/$ID_CREADO/cancelar" "" recepcion
probar "PUT /api/turnos/{id}/cancelar cancela el turno" "codigo=$CODIGO" '[ "$CODIGO" = 200 ] && [ "$(dato .estado)" = CANCELADO ]'
invocar PUT "/api/turnos/$ID_CREADO/cancelar" "" recepcion
probar "Cancelar dos veces devuelve 409 (patron State) con un mensaje claro" "codigo=$CODIGO $TEXTO" \
    '[ "$CODIGO" = 409 ] && [[ "$(dato .detail)" == *"ya estaba cancelado"* ]] && [ "$(dato .type)" = urn:mediconecta:problema:transicion-invalida ]'
invocar POST /api/turnos "$PEDIDO" recepcion
probar "El horario de un turno cancelado vuelve a estar libre (201)" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 201 ]'
[ "$CODIGO" = 201 ] && invocar PUT "/api/turnos/$(dato .id)/cancelar" "" recepcion

# ============================================================ IDEMPOTENCIA ==

titulo "IDEMPOTENCY-KEY EN POST /api/turnos"

HORARIO_IDEM="$(primer_horario "$PROFESIONAL_ID")"
CLAVE_IDEM="humo-$(date +%s)-$RANDOM-$RANDOM"
PEDIDO_IDEM="$(jq -nc --arg d "$DOCUMENTO" --argjson p "$PROFESIONAL_ID" --arg f "$HORARIO_IDEM" '{documentoPaciente:$d, profesionalId:$p, fechaHora:$f, motivo:"Idempotencia"}')"
invocar POST /api/turnos "$PEDIDO_IDEM" recepcion "Idempotency-Key: $CLAVE_IDEM"
probar "Primer pedido con Idempotency-Key crea el turno (201)" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 201 ] && [ -z "$(cabecera Idempotent-Replayed)" ]'
ID_IDEM="$(dato .id)"
invocar POST /api/turnos "$PEDIDO_IDEM" recepcion "Idempotency-Key: $CLAVE_IDEM"
probar "El mismo pedido repetido devuelve el MISMO turno (no crea otro)" "codigo=$CODIGO id=$(dato .id) esperado=$ID_IDEM" \
    '[ "$CODIGO" = 201 ] && [ "$(dato .id)" = "$ID_IDEM" ]'
probar "La repeticion se marca con Idempotent-Replayed: true" "" '[ "$(cabecera Idempotent-Replayed)" = true ]'
invocar POST /api/turnos "$(jq -c '.motivo = "Otro motivo"' <<< "$PEDIDO_IDEM")" recepcion "Idempotency-Key: $CLAVE_IDEM"
probar "La misma clave con otro cuerpo devuelve 422" "codigo=$CODIGO $TEXTO" \
    '[ "$CODIGO" = 422 ] && [ "$(dato .type)" = urn:mediconecta:problema:idempotencia-reutilizada ]'
invocar POST /api/turnos "$PEDIDO_IDEM" recepcion "Idempotency-Key: corta"
probar "Una clave con formato invalido devuelve 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'
invocar POST /api/turnos "$PEDIDO_IDEM" admin "Idempotency-Key: $CLAVE_IDEM"
probar "La clave es por usuario: otro usuario con la misma clave no recibe ese turno" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'
[ -n "$ID_IDEM" ] && invocar PUT "/api/turnos/$ID_IDEM/cancelar" "" recepcion

# --------------------------------------------------- OWNERSHIP Y ARCHIVADO --

titulo "OWNERSHIP DE CANCELACION Y ARCHIVADO"

invocar GET "/api/profesionales?especialidad=Traumatologia" "" recepcion
PROFESIONAL_AJENO="$(jq -r '.[0].id' <<< "$TEXTO")"
HORARIO_AJENO="$(primer_horario "$PROFESIONAL_AJENO")"
invocar POST /api/turnos "$(jq -nc --argjson p "$PROFESIONAL_AJENO" --arg f "$HORARIO_AJENO" '{documentoPaciente:"30111222", profesionalId:$p, fechaHora:$f}')" recepcion
probar "Se crea un turno de Ana Gomez con un profesional que MEDICO no es" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 201 ]'
ID_ANA="$(dato .id)"

if [ -n "$ID_ANA" ]; then
    invocar GET "/api/turnos/$ID_ANA" "" paciente
    probar "Un PACIENTE si puede leer su propio turno" "codigo=$CODIGO" '[ "$CODIGO" = 200 ]'
    invocar PUT "/api/turnos/$ID_ANA/cancelar" "" medico
    probar "Un MEDICO no puede cancelar un turno fuera de su agenda (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
    invocar PUT "/api/turnos/$ID_ANA/cancelar" "" paciente
    probar "Un PACIENTE puede cancelar su propio turno (200)" "codigo=$CODIGO" '[ "$CODIGO" = 200 ] && [ "$(dato .estado)" = CANCELADO ]'
    invocar PUT "/api/turnos/$ID_ANA/archivar" "" paciente
    probar "Un PACIENTE no puede archivar turnos (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
    invocar PUT "/api/turnos/$ID_ANA/archivar" "" medico
    probar "Un MEDICO no puede archivar turnos (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
    invocar GET /api/turnos "" recepcion
    probar "El turno cancelado todavia aparece en la agenda antes de archivar" "" \
        '[ "$(jq "any(.id == $ID_ANA)" <<< "$TEXTO")" = true ]'
    invocar PUT "/api/turnos/$ID_ANA/archivar" "" recepcion
    probar "RECEPCION puede archivar un turno cancelado (200)" "codigo=$CODIGO" '[ "$CODIGO" = 200 ] && [ "$(dato .archivado)" = true ]'
    invocar PUT "/api/turnos/$ID_ANA/archivar" "" recepcion
    probar "Archivar dos veces devuelve 409 (patron State)" "codigo=$CODIGO" '[ "$CODIGO" = 409 ]'
    invocar GET /api/turnos "" recepcion
    probar "El turno archivado desaparece de la agenda general" "" '[ "$(jq "any(.id == $ID_ANA)" <<< "$TEXTO")" = false ]'
    invocar GET /api/pacientes/30111222/turnos "" recepcion
    probar "El turno archivado sigue en el historial del paciente, como CANCELADO" "" \
        '[ "$(jq "any(.id == $ID_ANA and .estado == \"CANCELADO\")" <<< "$TEXTO")" = true ]'
    invocar GET "/api/turnos/$ID_ANA" "" recepcion
    probar "El turno archivado se puede seguir consultando por id" "codigo=$CODIGO" '[ "$CODIGO" = 200 ]'
    invocar POST /api/turnos "$(jq -nc --arg d "$DOCUMENTO" --argjson p "$PROFESIONAL_AJENO" --arg f "$HORARIO_AJENO" '{documentoPaciente:$d, profesionalId:$p, fechaHora:$f}')" recepcion
    probar "El horario de un turno archivado no queda bloqueado (201)" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 201 ]'
    [ "$CODIGO" = 201 ] && invocar PUT "/api/turnos/$(dato .id)/cancelar" "" recepcion
fi

invocar POST /api/turnos "$(jq -nc --arg d "$DOCUMENTO" --argjson p "$PROFESIONAL_ID" --arg f "$(primer_horario "$PROFESIONAL_ID")" '{documentoPaciente:$d, profesionalId:$p, fechaHora:$f}')" recepcion
if [ "$CODIGO" = 201 ]; then
    invocar PUT "/api/turnos/$(dato .id)/cancelar" "" medico
    probar "Un MEDICO puede cancelar un turno de su propia agenda (200)" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 200 ]'
fi

# ============================================================ PATRON STATE ===

titulo "PATRON STATE: TRANSICIONES DEL TURNO (409 ante una invalida)"

invocar POST /api/turnos "$(jq -nc --arg d "$DOCUMENTO" --argjson p "$PROFESIONAL_ID" --arg f "$(primer_horario "$PROFESIONAL_ID" 1)" '{documentoPaciente:$d, profesionalId:$p, fechaHora:$f}')" recepcion
probar "Se crea un turno PROGRAMADO para probar las transiciones" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 201 ]'
ID_STATE="$(dato .id)"
if [ -n "$ID_STATE" ]; then
    invocar PUT "/api/turnos/$ID_STATE/archivar" "" recepcion
    probar "PROGRAMADO -> archivar es invalido (409)" "codigo=$CODIGO" '[ "$CODIGO" = 409 ]'
    invocar PUT "/api/turnos/$ID_STATE/atender" "" recepcion
    probar "RECEPCION no puede registrar la atencion (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
    invocar PUT "/api/turnos/$ID_STATE/atender" "" medico
    probar "PROGRAMADO -> ATENDIDO lo hace el MEDICO de esa agenda (200)" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 200 ] && [ "$(dato .estado)" = ATENDIDO ]'
    invocar PUT "/api/turnos/$ID_STATE/atender" "" medico
    probar "ATENDIDO -> atender es invalido (409)" "codigo=$CODIGO" '[ "$CODIGO" = 409 ]'
    invocar PUT "/api/turnos/$ID_STATE/cancelar" "" recepcion
    probar "ATENDIDO -> cancelar es invalido (409)" "codigo=$CODIGO" '[ "$CODIGO" = 409 ]'
    probar "El 409 viene en Problem Details con status y detail" "$TEXTO" \
        '[ "$(dato .status)" = 409 ] && [[ "$(dato .detail)" == *atendido* ]]'
fi

# ========================================================= COBERTURA ========

titulo "COBERTURA: PATRON ADAPTER (SOAP legado + REST partner)"

invocar GET /api/pacientes/30111222/cobertura "" recepcion
probar "OSDE se valida contra el sistema legado por SOAP" "codigo=$CODIGO $TEXTO" \
    '[ "$CODIGO" = 200 ] && [ "$(dato .origen)" = LEGADO ] && [ "$(dato .vigente)" = true ]'
invocar GET /api/pacientes/28999888/cobertura "" recepcion
probar "Swiss Medical se valida contra el partner por REST" "codigo=$CODIGO $TEXTO" \
    '[ "$CODIGO" = 200 ] && [ "$(dato .origen)" = PARTNER ] && [ "$(dato .vigente)" = true ]'
invocar GET /api/pacientes/28999888/cobertura "" paciente
probar "Un PACIENTE no puede consultar la cobertura de otro (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
WSDL="$(curl -s "$BASE_URL/ServicioObraSocialLegadoImpl?wsdl")"
probar "El WSDL del sistema legado esta publicado" "" '[[ "$WSDL" == *definitions* ]]'

# ================================================== ASISTENTE STATEFUL =======

titulo "ASISTENTE DE RESERVA (@Stateful)"

invocar GET /api/reserva "" paciente
probar "GET /api/reserva abre una conversacion nueva" "paso=$(dato .pasoActual)" '[ "$CODIGO" = 200 ] && [ "$(dato .pasoActual)" = SIN_INICIAR ]'
invocar PUT /api/reserva/profesional "{\"profesionalId\":$PROFESIONAL_ID}" paciente
probar "Saltearse un paso devuelve 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'
invocar POST /api/reserva/iniciar '{"documentoPaciente":"30111222"}' paciente
probar "Paso 0: iniciar la reserva identifica al paciente" "codigo=$CODIGO" '[ "$CODIGO" = 200 ] && [ "$(dato .nombrePaciente)" = "Ana Gomez" ]'
CONVERSACION="$(dato .idConversacion)"
invocar PUT /api/reserva/especialidad '{"especialidad":"Cardiologia"}' paciente
probar "Paso 1: elegir especialidad avanza a ELEGIR_PROFESIONAL" "paso=$(dato .pasoActual)" '[ "$(dato .pasoActual)" = ELEGIR_PROFESIONAL ]'
invocar PUT /api/reserva/especialidad '{"especialidad":"Astrologia"}' paciente
probar "Una especialidad inexistente devuelve 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'
invocar PUT /api/reserva/profesional "{\"profesionalId\":$PROFESIONAL_ID}" paciente
probar "Paso 2: elegir profesional avanza a ELEGIR_HORARIO" "paso=$(dato .pasoActual)" '[ "$(dato .pasoActual)" = ELEGIR_HORARIO ]'
invocar GET /api/reserva "" paciente
probar "El servidor recuerda la especialidad sin que el cliente la reenvie" "" '[ "$(dato .especialidad)" = Cardiologia ]'
probar "El servidor recuerda el profesional sin que el cliente lo reenvie" "" '[ "$(dato .profesionalId)" = "$PROFESIONAL_ID" ]'
probar "Es la misma conversacion desde el inicio" "" '[ "$(dato .idConversacion)" = "$CONVERSACION" ]'
invocar PUT /api/reserva/volver "" paciente
probar "Volver atras es una operacion del servidor y olvida el profesional" "paso=$(dato .pasoActual)" \
    '[ "$(dato .pasoActual)" = ELEGIR_PROFESIONAL ] && [ -z "$(dato .profesionalId)" ]'
invocar PUT /api/reserva/profesional "{\"profesionalId\":$PROFESIONAL_ID}" paciente
HORARIO_ASISTENTE=""
for i in $(seq 1 21); do
    invocar GET "/api/reserva/horarios?fecha=$(fecha_mas "$i")" "" paciente
    HORARIO_ASISTENTE="$(jq -r '.[0] // empty' <<< "$TEXTO" 2> /dev/null)"
    [ -n "$HORARIO_ASISTENTE" ] && break
done
probar "GET /api/reserva/horarios usa el profesional recordado" "" '[ -n "$HORARIO_ASISTENTE" ]'
invocar PUT /api/reserva/horario "{\"fechaHora\":\"$HORARIO_ASISTENTE\"}" paciente
probar "Paso 3: elegir horario avanza a CONFIRMAR" "paso=$(dato .pasoActual) $TEXTO" '[ "$(dato .pasoActual)" = CONFIRMAR ]'
invocar POST /api/reserva/confirmar "" paciente
probar "Paso 4: confirmar crea el turno" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 200 ] && [ -n "$(dato .turnoId)" ]'
TURNO_ASISTENTE="$(dato .turnoId)"
CONVERSACION_CERRADA="$(dato .idConversacion)"
invocar GET /api/reserva "" paciente
probar "Tras confirmar, el contenedor descarto el asistente" "paso=$(dato .pasoActual)" \
    '[ "$(dato .pasoActual)" = SIN_INICIAR ] && [ "$(dato .idConversacion)" != "$CONVERSACION_CERRADA" ]'
invocar GET "/api/turnos/$TURNO_ASISTENTE" "" recepcion
probar "El turno creado por el asistente existe y esta PROGRAMADO" "" '[ "$(dato .estado)" = PROGRAMADO ]'
invocar GET /api/pacientes/28999888/turnos "" paciente
probar "Un PACIENTE no puede ver el historial de otro (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'

# ============================================================== MENSAJERIA ==

titulo "MENSAJERIA JMS (cola + topico, idempotencia, reintentos y DLQ)"

AVISOS=""
for i in $(seq 1 20); do
    invocar GET "/api/turnos/$TURNO_ASISTENTE/notificaciones" "" paciente
    [ "$(jq length <<< "$TEXTO" 2> /dev/null)" = 3 ] && { AVISOS="$TEXTO"; break; }
    sleep 1
done
probar "La cola genero la confirmacion por mail" "$TEXTO" '[ "$(jq "any(.tipo == \"CONFIRMACION\" and .canal == \"EMAIL\")" <<< "$AVISOS")" = true ]'
probar "Un suscriptor del topico programo el recordatorio" "" '[ "$(jq "any(.tipo == \"RECORDATORIO\" and .programadaPara != null)" <<< "$AVISOS")" = true ]'
probar "El otro suscriptor del topico aviso al profesional" "" '[ "$(jq "any(.tipo == \"AVISO_PROFESIONAL\")" <<< "$AVISOS")" = true ]'
probar "Los tres avisos comparten el mismo eventoId" "" '[ "$(jq "[.[].eventoId] | unique | length" <<< "$AVISOS")" = 1 ]'
invocar GET "/api/turnos/$TURNO_ASISTENTE/notificaciones" "" recepcion
probar "RECEPCION tambien ve los avisos del turno" "codigo=$CODIGO" '[ "$CODIGO" = 200 ]'
invocar PUT "/api/turnos/$TURNO_ASISTENTE/cancelar" "" recepcion

invocar POST /api/mensajeria/mensaje-defectuoso "" recepcion
probar "Solo un ADMIN puede inyectar un mensaje defectuoso (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
invocar POST /api/mensajeria/mensaje-defectuoso "" admin
probar "ADMIN envia un mensaje que ningun consumidor entiende (202)" "codigo=$CODIGO" '[ "$CODIGO" = 202 ]'
EVENTO_ROTO="$(dato .eventoId)"
FALLIDO=""
for i in $(seq 1 40); do
    invocar GET /api/mensajeria/mensajes-fallidos "" admin
    FALLIDO="$(jq -c --arg e "$EVENTO_ROTO" '[.[] | select(.eventoId == $e)][0] // empty' <<< "$TEXTO" 2> /dev/null)"
    [ -n "$FALLIDO" ] && break
    sleep 1
done
probar "Despues de los reintentos el mensaje llega a la DLQ y queda registrado" "evento=$EVENTO_ROTO" '[ -n "$FALLIDO" ]'
probar "Se registra de que destino vino" "$FALLIDO" '[[ "$(jq -r .destinoOriginal <<< "$FALLIDO")" == *ColaConfirmaciones* ]]'
invocar GET /api/mensajeria/mensajes-fallidos "" recepcion
probar "Solo un ADMIN ve los mensajes fallidos (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'

# ============================================== SEGURIDAD EN VIVO (OWASP API) ==

titulo "SEGURIDAD EN VIVO: JWT, BOLA, BFLA, INYECCIONES Y FUERZA BRUTA"

b64url() { printf '%s' "$1" | base64 | tr -d '=\n' | tr '/+' '_-'; }
EXP_FUTURO=$(( $(date +%s) + 3600 ))
TOKEN_algnone="$(b64url '{"alg":"none","typ":"JWT"}').$(b64url "{\"iss\":\"mediconecta\",\"sub\":\"admin\",\"groups\":[\"ADMIN\"],\"exp\":$EXP_FUTURO}")."
: > "$TMP/jar_algnone"
invocar GET /api/turnos "" algnone
probar "Un JWT con alg=none (sin firma) se rechaza con 401" "codigo=$CODIGO" '[ "$CODIGO" = 401 ]'
TOKEN_ajeno="$(b64url '{"alg":"HS256","typ":"JWT"}').$(b64url "{\"iss\":\"mediconecta\",\"sub\":\"admin\",\"groups\":[\"ADMIN\"],\"exp\":$EXP_FUTURO}").$(b64url firma-inventada)"
: > "$TMP/jar_ajeno"
invocar GET /api/turnos "" ajeno
probar "Un JWT firmado con otro secreto se rechaza con 401" "codigo=$CODIGO" '[ "$CODIGO" = 401 ]'
TOKEN_basura="no.es.un-jwt"
: > "$TMP/jar_basura"
invocar GET /api/turnos "" basura
probar "Un JWT mal formado se rechaza con 401" "codigo=$CODIGO" '[ "$CODIGO" = 401 ]'

invocar GET /api/pacientes/28999888 "" paciente
probar "BOLA: un PACIENTE no puede leer la ficha de otro (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
invocar GET /api/pacientes/30111222 "" paciente
probar "Un PACIENTE si puede leer su propia ficha" "codigo=$CODIGO" '[ "$CODIGO" = 200 ]'
invocar GET /api/turnos "" paciente
probar "BFLA: un PACIENTE no puede listar la agenda (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
invocar POST /api/profesionales "$NUEVO_PROFESIONAL" medico
probar "BFLA: un MEDICO no puede dar de alta profesionales (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'
invocar GET /api/mensajeria/mensajes-fallidos "" medico
probar "BFLA: un MEDICO no puede ver la DLQ (403)" "codigo=$CODIGO" '[ "$CODIGO" = 403 ]'

invocar GET "/api/pacientes?q=%27%20OR%20%271%27%3D%271" "" recepcion
probar "Inyeccion SQL en la busqueda no devuelve todo el padron" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 200 ] && [ "$(jq length <<< "$TEXTO")" = 0 ]'
invocar GET "/api/pacientes/1%27%20OR%20%271%27%3D%271" "" recepcion
probar "Inyeccion SQL en el documento da 404, no un paciente" "codigo=$CODIGO" '[ "$CODIGO" = 404 ]'
invocar POST /api/auth/login '{"usuario":"admin'"'"' OR '"'"'1'"'"'='"'"'1","clave":"x'"'"' OR '"'"'1'"'"'='"'"'1"}'
probar "Inyeccion SQL en el login devuelve 401" "codigo=$CODIGO" '[ "$CODIGO" = 401 ]'
invocar POST /api/pacientes "$(jq -nc --arg d "$(aleatorio 60000000 69999999)" '{nombre:"<script>alert(1)</script>", documento:$d}')" recepcion
probar "Un intento de XSS se guarda como texto y se devuelve como JSON" "$(cabecera Content-Type)" \
    '[ "$CODIGO" = 201 ] && [[ "$(cabecera Content-Type)" == application/json* ]]'

VICTIMA="fuerza-bruta-$RANDOM$RANDOM"
for i in 1 2 3 4 5; do
    invocar POST /api/auth/login "$(jq -nc --arg u "$VICTIMA" '{usuario:$u, clave:"mala"}')"
done
probar "Los primeros 5 intentos fallidos devuelven 401" "codigo=$CODIGO" '[ "$CODIGO" = 401 ]'
invocar POST /api/auth/login "$(jq -nc --arg u "$VICTIMA" '{usuario:$u, clave:"mala"}')"
probar "Fuerza bruta: el sexto intento devuelve 429" "codigo=$CODIGO $TEXTO" \
    '[ "$CODIGO" = 429 ] && [ "$(dato .type)" = urn:mediconecta:problema:demasiadas-solicitudes ]'
probar "El 429 trae Retry-After" "" '[ -n "$(cabecera Retry-After)" ]'

# ======================================================== ETAG / IF-MATCH ===

titulo "ETAG Y CONCURRENCIA OPTIMISTA"

invocar POST /api/turnos "$(jq -nc --arg d "$DOCUMENTO" --argjson p "$PROFESIONAL_ID" --arg f "$(primer_horario "$PROFESIONAL_ID")" '{documentoPaciente:$d, profesionalId:$p, fechaHora:$f}')" recepcion
ID_ETAG="$(dato .id)"
invocar GET "/api/turnos/$ID_ETAG" "" recepcion
ETAG="$(cabecera ETag)"
probar "GET /api/turnos/{id} devuelve ETag" "$ETAG" '[[ "$ETAG" == *"turno-$ID_ETAG-v"* ]]'
invocar GET "/api/turnos/$ID_ETAG" "" recepcion "If-None-Match: $ETAG"
probar "Con If-None-Match igual responde 304 sin cuerpo" "codigo=$CODIGO" '[ "$CODIGO" = 304 ] && [ -z "$TEXTO" ]'
invocar PUT "/api/turnos/$ID_ETAG/cancelar" "" recepcion "If-Match: \"turno-$ID_ETAG-v999\""
probar "Con If-Match de una version vieja responde 412" "codigo=$CODIGO $TEXTO" \
    '[ "$CODIGO" = 412 ] && [ "$(dato .type)" = urn:mediconecta:problema:precondicion-fallida ]'
invocar PUT "/api/turnos/$ID_ETAG/cancelar" "" recepcion "If-Match: $ETAG"
probar "Con If-Match correcto cancela y devuelve el ETag nuevo" "codigo=$CODIGO $(cabecera ETag)" \
    '[ "$CODIGO" = 200 ] && [ -n "$(cabecera ETag)" ] && [ "$(cabecera ETag)" != "$ETAG" ]'
invocar GET /api/pacientes/30111222 "" recepcion
probar "GET /api/pacientes/{documento} devuelve ETag" "" '[[ "$(cabecera ETag)" == *paciente-30111222-v* ]]'

# ================================================ IA: GATEWAY Y FALLBACK =====

titulo "ASISTENTE DE IA (gateway con timeout de 5 s y fallback)"

invocar POST /api/reserva/orientacion '{"mensajes":[{"rol":"usuario","texto":"Me cai jugando al futbol y me duele mucho la rodilla"}]}' paciente
probar "El gateway de IA responde (modelo o respaldo)" "codigo=$CODIGO $TEXTO" '[ "$CODIGO" = 200 ] && [ -n "$(dato .mensaje)" ]'
probar "Sugiere Traumatologia para un golpe en la rodilla" "$TEXTO" '[ "$(dato .especialidad)" = Traumatologia ]'
printf '         (respuesta %s)\n' "$( [ "$(dato .contingencia)" = true ] && echo 'del respaldo por palabras clave' || echo 'del modelo de IA')"
invocar POST /api/reserva/orientacion '{"mensajes":[{"rol":"usuario","texto":"Tengo un dolor fuerte en el pecho y falta de aire"}]}' paciente
probar "Ante una emergencia marca urgente y deriva al 107/911" "$TEXTO" '[ "$(dato .urgente)" = true ]'
invocar POST /api/reserva/orientacion '{"mensajes":[]}' paciente
probar "Una conversacion vacia se rechaza con 400" "codigo=$CODIGO" '[ "$CODIGO" = 400 ]'
invocar GET /api/pacientes/30111222/cobertura "" recepcion
probar "La cobertura informa si es de contingencia" "$TEXTO" '[ "$(dato '"'"'.contingencia | tostring'"'"')" = false ]'

# =================================================================== CIERRE ==

printf '\n============================================================\n'
if [ "$FALLOS" -eq 0 ]; then COLOR="$VERDE"; else COLOR="$ROJO"; fi
printf '%sResultado: %d pruebas OK, %d fallidas%s\n' "$COLOR" "$OK" "$FALLOS" "$NORMAL"
printf '============================================================\n'

[ "$FALLOS" -eq 0 ]
