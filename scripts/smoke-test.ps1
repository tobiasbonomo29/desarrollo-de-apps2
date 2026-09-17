<#
    =========================================================================
     MediConecta - prueba de humo de la API
    =========================================================================

     Recorre de punta a punta las funcionalidades de la Entrega 1 contra una
     instancia ya desplegada. Verifica salud, turnos, pacientes, profesionales,
     el asistente stateful y la seguridad por roles.

     Uso:
         .\smoke-test.ps1
         .\smoke-test.ps1 -BaseUrl http://localhost:8080/mediconecta

     Devuelve codigo de salida 0 si todas las pruebas pasan, 1 si alguna falla.
     Escrito para Windows PowerShell 5.1 (el que trae Windows por defecto).
    =========================================================================
#>

param(
    [string] $BaseUrl = "http://localhost:8080/mediconecta",
    [string] $Clave   = "Demo.2026"
)

$ErrorActionPreference = "Continue"
$script:ok = 0
$script:fallos = 0

# ------------------------------------------------------------------ util ----

function Invocar {
    param(
        [string] $Metodo,
        [string] $Ruta,
        $Cuerpo,
        $Sesion
    )

    $parametros = @{
        Uri             = "$BaseUrl$Ruta"
        Method          = $Metodo
        UseBasicParsing = $true
        Headers         = @{ "Accept" = "application/json"; "X-Requested-With" = "XMLHttpRequest" }
        ContentType     = "application/json"
    }
    if ($null -ne $Sesion) { $parametros.WebSession = $Sesion }

    # Ojo con ConvertTo-Json por pipeline: PowerShell desarma los arreglos y un
    # arreglo de un solo elemento terminaria enviandose como objeto JSON.
    # -InputObject conserva la forma original.
    if ($null -ne $Cuerpo) { $parametros.Body = (ConvertTo-Json -InputObject $Cuerpo -Depth 6 -Compress) }

    try {
        $r = Invoke-WebRequest @parametros -ErrorAction Stop
        $texto = $r.Content
    } catch {
        $respuesta = $_.Exception.Response
        if ($null -eq $respuesta) {
            return [pscustomobject]@{ Codigo = 0; Texto = $_.Exception.Message; Datos = $null }
        }
        $codigo = [int] $respuesta.StatusCode

        # En Windows PowerShell 5.1 el cuerpo de una respuesta de error ya viene
        # leido en ErrorDetails; el stream de la excepcion suele llegar vacio.
        $texto = ""
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $texto = $_.ErrorDetails.Message
        } else {
            try {
                $lector = New-Object System.IO.StreamReader($respuesta.GetResponseStream())
                $texto = $lector.ReadToEnd()
                $lector.Close()
            } catch { }
        }

        $datos = $null
        if ($texto) { try { $datos = $texto | ConvertFrom-Json } catch { } }
        return [pscustomobject]@{ Codigo = $codigo; Texto = $texto; Datos = $datos }
    }

    $datos = $null
    if ($texto) { try { $datos = $texto | ConvertFrom-Json } catch { } }
    return [pscustomobject]@{ Codigo = [int] $r.StatusCode; Texto = $texto; Datos = $datos }
}

function Abrir-Sesion {
    param([string] $Usuario)

    $sesion = New-Object Microsoft.PowerShell.Commands.WebRequestSession

    # Pedir un recurso protegido crea la sesion y hace que el contenedor guarde
    # la peticion original; despues se envian las credenciales a j_security_check.
    try {
        Invoke-WebRequest -Uri "$BaseUrl/app/index.html" -WebSession $sesion -UseBasicParsing | Out-Null
    } catch { }

    try {
        Invoke-WebRequest -Uri "$BaseUrl/j_security_check" -Method Post -WebSession $sesion `
            -UseBasicParsing -Body @{ j_username = $Usuario; j_password = $Clave } | Out-Null
    } catch { }

    return $sesion
}

function Probar {
    param(
        [string] $Nombre,
        [bool]   $Condicion,
        [string] $Detalle = ""
    )
    if ($Condicion) {
        $script:ok++
        Write-Host ("  [OK]    {0}" -f $Nombre) -ForegroundColor Green
    } else {
        $script:fallos++
        Write-Host ("  [FALLA] {0}  {1}" -f $Nombre, $Detalle) -ForegroundColor Red
    }
}

function Titulo($texto) {
    Write-Host ""
    Write-Host $texto -ForegroundColor Cyan
    Write-Host ("-" * $texto.Length) -ForegroundColor DarkGray
}

# =================================================================== SALUD ===

Titulo "SALUD (endpoint publico, sin autenticacion)"

try {
    $salud = Invoke-WebRequest -Uri "$BaseUrl/api/salud" -UseBasicParsing
    Probar "GET /api/salud responde 200" ([int]$salud.StatusCode -eq 200)
    Probar "GET /api/salud devuelve el saludo del contenedor" ($salud.Content -match "Hola desde el contenedor")
} catch {
    Probar "GET /api/salud responde 200" $false $_.Exception.Message
    Write-Host ""
    Write-Host "No se pudo contactar la aplicacion en $BaseUrl. Esta desplegada?" -ForegroundColor Red
    exit 1
}

# ============================================================== SEGURIDAD ====

Titulo "SEGURIDAD"

$sinSesion = Invocar -Metodo GET -Ruta "/api/turnos"
Probar "Endpoint protegido sin iniciar sesion devuelve 401" ($sinSesion.Codigo -eq 401) "codigo=$($sinSesion.Codigo)"

$recepcion = Abrir-Sesion -Usuario "recepcion"
$medico    = Abrir-Sesion -Usuario "medico"
$admin     = Abrir-Sesion -Usuario "admin"
$paciente  = Abrir-Sesion -Usuario "paciente"

$quien = Invocar -Metodo GET -Ruta "/api/sesion" -Sesion $recepcion
Probar "Login FORM de 'recepcion' deja sesion activa" ($quien.Codigo -eq 200 -and $quien.Datos.usuario -eq "recepcion") "codigo=$($quien.Codigo)"
Probar "La sesion de 'recepcion' tiene el rol RECEPCION" ($quien.Datos.roles -contains "RECEPCION")

$agendaPaciente = Invocar -Metodo GET -Ruta "/api/turnos" -Sesion $paciente
Probar "Un PACIENTE no puede ver la agenda completa (403)" ($agendaPaciente.Codigo -eq 403) "codigo=$($agendaPaciente.Codigo)"

$matricula = "MP-" + (Get-Random -Minimum 60000 -Maximum 99999)
$nuevoProfesional = @{ nombre = "Dr. Prueba Humo"; especialidad = "Dermatologia"; matricula = $matricula }

$altaConRecepcion = Invocar -Metodo POST -Ruta "/api/profesionales" -Cuerpo $nuevoProfesional -Sesion $recepcion
Probar "Alta de profesional con rol RECEPCION devuelve 403" ($altaConRecepcion.Codigo -eq 403) "codigo=$($altaConRecepcion.Codigo)"

$altaConAdmin = Invocar -Metodo POST -Ruta "/api/profesionales" -Cuerpo $nuevoProfesional -Sesion $admin
Probar "Alta de profesional con rol ADMIN devuelve 201" ($altaConAdmin.Codigo -eq 201) "codigo=$($altaConAdmin.Codigo) $($altaConAdmin.Texto)"

if ($altaConAdmin.Codigo -eq 201) {
    $idNuevo = $altaConAdmin.Datos.id
    $franjas = @(@{ diaSemana = "MONDAY"; horaDesde = "08:00"; horaHasta = "12:00"; duracionMinutos = 30 })

    $dispPaciente = Invocar -Metodo PUT -Ruta "/api/profesionales/$idNuevo/disponibilidad" -Cuerpo $franjas -Sesion $paciente
    Probar "Definir disponibilidad con rol PACIENTE devuelve 403" ($dispPaciente.Codigo -eq 403) "codigo=$($dispPaciente.Codigo)"

    $dispMedico = Invocar -Metodo PUT -Ruta "/api/profesionales/$idNuevo/disponibilidad" -Cuerpo $franjas -Sesion $medico
    Probar "Definir disponibilidad con rol MEDICO devuelve 200" ($dispMedico.Codigo -eq 200) "codigo=$($dispMedico.Codigo) $($dispMedico.Texto)"
}

# ============================================================ PROFESIONALES ==

Titulo "PROFESIONALES"

$profesionales = Invocar -Metodo GET -Ruta "/api/profesionales" -Sesion $recepcion
Probar "GET /api/profesionales devuelve la lista" ($profesionales.Codigo -eq 200 -and $profesionales.Datos.Count -ge 4) "codigo=$($profesionales.Codigo)"

$cardio = Invocar -Metodo GET -Ruta "/api/profesionales?especialidad=Cardiologia" -Sesion $recepcion
$soloCardio = $true
foreach ($p in $cardio.Datos) { if ($p.especialidad -ne "Cardiologia") { $soloCardio = $false } }
Probar "El filtro por especialidad devuelve solo Cardiologia" ($cardio.Codigo -eq 200 -and $soloCardio -and $cardio.Datos.Count -ge 1)

$especialidades = Invocar -Metodo GET -Ruta "/api/profesionales/especialidades" -Sesion $recepcion
Probar "GET /api/profesionales/especialidades devuelve valores" ($especialidades.Codigo -eq 200 -and $especialidades.Datos.Count -ge 1)

# Buscar un profesional con horarios libres en los proximos dias.
$profesionalId = $cardio.Datos[0].id
$fechaLibre = $null
$horarios = @()
for ($i = 1; $i -le 14; $i++) {
    $fecha = (Get-Date).AddDays($i).ToString("yyyy-MM-dd")
    $r = Invocar -Metodo GET -Ruta "/api/profesionales/$profesionalId/horarios-libres?fecha=$fecha" -Sesion $recepcion
    if ($r.Codigo -eq 200 -and $r.Datos.Count -gt 0) { $fechaLibre = $fecha; $horarios = $r.Datos; break }
}
Probar "GET /api/profesionales/{id}/horarios-libres devuelve horarios" ($null -ne $fechaLibre) "no se encontraron horarios libres en 14 dias"

$fechaMala = Invocar -Metodo GET -Ruta "/api/profesionales/$profesionalId/horarios-libres?fecha=18-09-2026" -Sesion $recepcion
Probar "Una fecha mal formada devuelve 400" ($fechaMala.Codigo -eq 400) "codigo=$($fechaMala.Codigo)"

$inexistente = Invocar -Metodo GET -Ruta "/api/profesionales/999999" -Sesion $recepcion
Probar "GET de un profesional inexistente devuelve 404" ($inexistente.Codigo -eq 404) "codigo=$($inexistente.Codigo)"

# ================================================================ PACIENTES ==

Titulo "PACIENTES"

$listaPacientes = Invocar -Metodo GET -Ruta "/api/pacientes" -Sesion $recepcion
Probar "GET /api/pacientes devuelve el padron" ($listaPacientes.Codigo -eq 200 -and $listaPacientes.Datos.Count -ge 4) "codigo=$($listaPacientes.Codigo)"

$documento = "" + (Get-Random -Minimum 40000000 -Maximum 49999999)
$nuevoPaciente = @{ nombre = "Paciente De Prueba"; documento = $documento; email = "prueba@mail.com"; cobertura = "OSDE 210" }

$alta = Invocar -Metodo POST -Ruta "/api/pacientes" -Cuerpo $nuevoPaciente -Sesion $recepcion
Probar "POST /api/pacientes registra un paciente (201)" ($alta.Codigo -eq 201) "codigo=$($alta.Codigo) $($alta.Texto)"

$duplicado = Invocar -Metodo POST -Ruta "/api/pacientes" -Cuerpo $nuevoPaciente -Sesion $recepcion
Probar "Un documento duplicado devuelve 400" ($duplicado.Codigo -eq 400) "codigo=$($duplicado.Codigo)"

$sinDocumento = Invocar -Metodo POST -Ruta "/api/pacientes" -Cuerpo @{ nombre = "Sin Documento" } -Sesion $recepcion
Probar "Un paciente sin documento devuelve 400" ($sinDocumento.Codigo -eq 400) "codigo=$($sinDocumento.Codigo)"

$busqueda = Invocar -Metodo GET -Ruta "/api/pacientes/$documento" -Sesion $recepcion
Probar "GET /api/pacientes/{documento} encuentra al paciente" ($busqueda.Codigo -eq 200 -and $busqueda.Datos.documento -eq $documento)

$noExiste = Invocar -Metodo GET -Ruta "/api/pacientes/00000000" -Sesion $recepcion
Probar "GET de un paciente inexistente devuelve 404" ($noExiste.Codigo -eq 404) "codigo=$($noExiste.Codigo)"

$modificado = Invocar -Metodo PUT -Ruta "/api/pacientes/$documento" `
    -Cuerpo @{ nombre = "Paciente Modificado"; telefono = "11-1111-2222"; cobertura = "Swiss Medical"; activo = $true } `
    -Sesion $recepcion
Probar "PUT /api/pacientes/{documento} actualiza los datos" ($modificado.Codigo -eq 200 -and $modificado.Datos.nombre -eq "Paciente Modificado") "codigo=$($modificado.Codigo)"

$historial = Invocar -Metodo GET -Ruta "/api/pacientes/$documento/turnos" -Sesion $recepcion
Probar "GET /api/pacientes/{documento}/turnos devuelve el historial" ($historial.Codigo -eq 200)

# =================================================================== TURNOS ==

Titulo "TURNOS"

$turnos = Invocar -Metodo GET -Ruta "/api/turnos" -Sesion $recepcion
Probar "GET /api/turnos devuelve la agenda" ($turnos.Codigo -eq 200 -and $turnos.Datos.Count -ge 3) "codigo=$($turnos.Codigo)"

$primerId = $turnos.Datos[0].id
$unTurno = Invocar -Metodo GET -Ruta "/api/turnos/$primerId" -Sesion $recepcion
Probar "GET /api/turnos/{id} devuelve un turno existente" ($unTurno.Codigo -eq 200 -and $unTurno.Datos.id -eq $primerId)

$turnoFantasma = Invocar -Metodo GET -Ruta "/api/turnos/999999" -Sesion $recepcion
Probar "GET /api/turnos/999999 devuelve 404" ($turnoFantasma.Codigo -eq 404) "codigo=$($turnoFantasma.Codigo)"
Probar "El 404 trae el mensaje de negocio" ($turnoFantasma.Datos.error -like "*No existe un turno*") "$($turnoFantasma.Texto)"

$horarioElegido = $horarios[0]
$pedido = @{ documentoPaciente = $documento; profesionalId = $profesionalId; fechaHora = $horarioElegido; motivo = "Prueba de humo" }

$creado = Invocar -Metodo POST -Ruta "/api/turnos" -Cuerpo $pedido -Sesion $recepcion
Probar "POST /api/turnos agenda un turno (201)" ($creado.Codigo -eq 201) "codigo=$($creado.Codigo) $($creado.Texto)"
Probar "El turno nace en estado PROGRAMADO" ($creado.Datos.estado -eq "PROGRAMADO") "estado=$($creado.Datos.estado)"

$superpuesto = Invocar -Metodo POST -Ruta "/api/turnos" -Cuerpo $pedido -Sesion $recepcion
Probar "Un turno superpuesto del mismo profesional devuelve 400" ($superpuesto.Codigo -eq 400) "codigo=$($superpuesto.Codigo)"
Probar "El mensaje explica la superposicion" ($superpuesto.Datos.error -like "*ya tiene un turno en ese horario*") "$($superpuesto.Texto)"

$enElPasado = Invocar -Metodo POST -Ruta "/api/turnos" `
    -Cuerpo @{ documentoPaciente = $documento; profesionalId = $profesionalId; fechaHora = "2020-01-06T10:00" } `
    -Sesion $recepcion
Probar "Un turno en el pasado devuelve 400" ($enElPasado.Codigo -eq 400) "codigo=$($enElPasado.Codigo)"
Probar "El mensaje explica que no se agenda en el pasado" ($enElPasado.Datos.error -like "*pasado*") "$($enElPasado.Texto)"

$fechaInvalida = Invocar -Metodo POST -Ruta "/api/turnos" `
    -Cuerpo @{ documentoPaciente = $documento; profesionalId = $profesionalId; fechaHora = "15/12/2026" } `
    -Sesion $recepcion
Probar "Una fecha con formato invalido devuelve 400" ($fechaInvalida.Codigo -eq 400) "codigo=$($fechaInvalida.Codigo)"

$sinPaciente = Invocar -Metodo POST -Ruta "/api/turnos" `
    -Cuerpo @{ profesionalId = $profesionalId; fechaHora = $horarioElegido } -Sesion $recepcion
Probar "Un turno sin paciente devuelve 400" ($sinPaciente.Codigo -eq 400) "codigo=$($sinPaciente.Codigo)"

# Compatibilidad con el formato de peticion de la Entrega 1.
$fechaLibre2 = $null
for ($i = 1; $i -le 14; $i++) {
    $f = (Get-Date).AddDays($i).ToString("yyyy-MM-dd")
    $r = Invocar -Metodo GET -Ruta "/api/profesionales/$profesionalId/horarios-libres?fecha=$f" -Sesion $recepcion
    if ($r.Codigo -eq 200 -and $r.Datos.Count -gt 0) { $fechaLibre2 = $r.Datos[0]; break }
}
$formatoViejo = Invocar -Metodo POST -Ruta "/api/turnos" -Sesion $recepcion -Cuerpo @{
    paciente     = @{ nombre = "Compatibilidad Entrega1"; documento = "" + (Get-Random -Minimum 50000000 -Maximum 59999999) }
    profesional  = $cardio.Datos[0].nombre
    especialidad = "Cardiologia"
    fechaHora    = $fechaLibre2
}
Probar "El cuerpo JSON de la Entrega 1 sigue funcionando (201)" ($formatoViejo.Codigo -eq 201) "codigo=$($formatoViejo.Codigo) $($formatoViejo.Texto)"

$idCreado = $creado.Datos.id

$cancelarPaciente = Invocar -Metodo PUT -Ruta "/api/turnos/$idCreado/cancelar" -Sesion $paciente
Probar "Cancelar un turno con rol PACIENTE devuelve 403" ($cancelarPaciente.Codigo -eq 403) "codigo=$($cancelarPaciente.Codigo)"

$cancelado = Invocar -Metodo PUT -Ruta "/api/turnos/$idCreado/cancelar" -Sesion $recepcion
Probar "PUT /api/turnos/{id}/cancelar cancela el turno" ($cancelado.Codigo -eq 200 -and $cancelado.Datos.estado -eq "CANCELADO") "codigo=$($cancelado.Codigo) estado=$($cancelado.Datos.estado)"

$recancelado = Invocar -Metodo PUT -Ruta "/api/turnos/$idCreado/cancelar" -Sesion $recepcion
Probar "Cancelar dos veces devuelve 400 con un mensaje claro" ($recancelado.Codigo -eq 400 -and $recancelado.Datos.error -like "*ya estaba cancelado*") "codigo=$($recancelado.Codigo)"

$reutilizado = Invocar -Metodo POST -Ruta "/api/turnos" -Cuerpo $pedido -Sesion $recepcion
Probar "El horario de un turno cancelado vuelve a estar libre (201)" ($reutilizado.Codigo -eq 201) "codigo=$($reutilizado.Codigo) $($reutilizado.Texto)"
if ($reutilizado.Codigo -eq 201) {
    Invocar -Metodo PUT -Ruta "/api/turnos/$($reutilizado.Datos.id)/cancelar" -Sesion $recepcion | Out-Null
}

# ================================================== ASISTENTE STATEFUL =======

Titulo "ASISTENTE DE RESERVA (@Stateful)"

$estado0 = Invocar -Metodo GET -Ruta "/api/reserva" -Sesion $paciente
Probar "GET /api/reserva abre una conversacion nueva" ($estado0.Codigo -eq 200 -and $estado0.Datos.pasoActual -eq "SIN_INICIAR") "paso=$($estado0.Datos.pasoActual)"
$conversacionInicial = $estado0.Datos.idConversacion

$fueraDeOrden = Invocar -Metodo PUT -Ruta "/api/reserva/profesional" -Cuerpo @{ profesionalId = $profesionalId } -Sesion $paciente
Probar "Saltearse un paso devuelve 400" ($fueraDeOrden.Codigo -eq 400) "codigo=$($fueraDeOrden.Codigo)"

$paso1 = Invocar -Metodo POST -Ruta "/api/reserva/iniciar" -Cuerpo @{ documentoPaciente = "30111222" } -Sesion $paciente
Probar "Paso 0: iniciar la reserva identifica al paciente" ($paso1.Codigo -eq 200 -and $paso1.Datos.nombrePaciente -eq "Ana Gomez") "codigo=$($paso1.Codigo)"

$paso2 = Invocar -Metodo PUT -Ruta "/api/reserva/especialidad" -Cuerpo @{ especialidad = "Cardiologia" } -Sesion $paciente
Probar "Paso 1: elegir especialidad avanza a ELEGIR_PROFESIONAL" ($paso2.Codigo -eq 200 -and $paso2.Datos.pasoActual -eq "ELEGIR_PROFESIONAL") "paso=$($paso2.Datos.pasoActual)"

$especialidadMala = Invocar -Metodo PUT -Ruta "/api/reserva/especialidad" -Cuerpo @{ especialidad = "Astrologia" } -Sesion $paciente
Probar "Una especialidad inexistente devuelve 400" ($especialidadMala.Codigo -eq 400) "codigo=$($especialidadMala.Codigo)"

$paso3 = Invocar -Metodo PUT -Ruta "/api/reserva/profesional" -Cuerpo @{ profesionalId = $profesionalId } -Sesion $paciente
Probar "Paso 2: elegir profesional avanza a ELEGIR_HORARIO" ($paso3.Codigo -eq 200 -and $paso3.Datos.pasoActual -eq "ELEGIR_HORARIO") "paso=$($paso3.Datos.pasoActual)"

# La prueba clave del estado: se consulta sin enviar NADA de lo elegido antes.
$intermedio = Invocar -Metodo GET -Ruta "/api/reserva" -Sesion $paciente
Probar "El servidor recuerda la especialidad sin que el cliente la reenvie" ($intermedio.Datos.especialidad -eq "Cardiologia") "especialidad=$($intermedio.Datos.especialidad)"
Probar "El servidor recuerda el profesional sin que el cliente lo reenvie" ($intermedio.Datos.profesionalId -eq $profesionalId) "profesionalId=$($intermedio.Datos.profesionalId)"
Probar "Es la misma conversacion desde el inicio" ($intermedio.Datos.idConversacion -eq $paso1.Datos.idConversacion)

$horariosReserva = Invocar -Metodo GET -Ruta "/api/reserva/horarios?fecha=$fechaLibre" -Sesion $paciente
Probar "GET /api/reserva/horarios usa el profesional recordado" ($horariosReserva.Codigo -eq 200) "codigo=$($horariosReserva.Codigo)"

$atras = Invocar -Metodo PUT -Ruta "/api/reserva/volver" -Sesion $paciente
Probar "Volver atras es una operacion del servidor" ($atras.Codigo -eq 200 -and $atras.Datos.pasoActual -eq "ELEGIR_PROFESIONAL") "paso=$($atras.Datos.pasoActual)"
Probar "Al volver atras el servidor olvida el profesional" ($null -eq $atras.Datos.profesionalId)

Invocar -Metodo PUT -Ruta "/api/reserva/profesional" -Cuerpo @{ profesionalId = $profesionalId } -Sesion $paciente | Out-Null

$horariosOtraVez = Invocar -Metodo GET -Ruta "/api/reserva/horarios?fecha=$fechaLibre" -Sesion $paciente
$horarioReserva = $horariosOtraVez.Datos[0]

$paso4 = Invocar -Metodo PUT -Ruta "/api/reserva/horario" -Cuerpo @{ fechaHora = $horarioReserva } -Sesion $paciente
Probar "Paso 3: elegir horario avanza a CONFIRMAR" ($paso4.Codigo -eq 200 -and $paso4.Datos.pasoActual -eq "CONFIRMAR") "paso=$($paso4.Datos.pasoActual)"

$confirmado = Invocar -Metodo POST -Ruta "/api/reserva/confirmar" -Sesion $paciente
Probar "Paso 4: confirmar crea el turno" ($confirmado.Codigo -eq 200 -and $null -ne $confirmado.Datos.turnoId) "codigo=$($confirmado.Codigo) $($confirmado.Texto)"
Probar "La conversacion queda marcada como finalizada" ($confirmado.Datos.finalizada -eq $true)

$despues = Invocar -Metodo GET -Ruta "/api/reserva" -Sesion $paciente
Probar "Tras confirmar, el contenedor descarto el asistente" ($despues.Datos.pasoActual -eq "SIN_INICIAR") "paso=$($despues.Datos.pasoActual)"
Probar "La nueva conversacion tiene otro identificador" ($despues.Datos.idConversacion -ne $confirmado.Datos.idConversacion)

$turnoDelAsistente = Invocar -Metodo GET -Ruta "/api/turnos/$($confirmado.Datos.turnoId)" -Sesion $recepcion
Probar "El turno creado por el asistente existe y esta PROGRAMADO" ($turnoDelAsistente.Codigo -eq 200 -and $turnoDelAsistente.Datos.estado -eq "PROGRAMADO")

Invocar -Metodo PUT -Ruta "/api/turnos/$($confirmado.Datos.turnoId)/cancelar" -Sesion $recepcion | Out-Null

# Un paciente solo ve su propio historial.
$historialAjeno = Invocar -Metodo GET -Ruta "/api/pacientes/28999888/turnos" -Sesion $paciente
Probar "Un PACIENTE no puede ver el historial de otro (400)" ($historialAjeno.Codigo -eq 400) "codigo=$($historialAjeno.Codigo)"

$historialPropio = Invocar -Metodo GET -Ruta "/api/pacientes/30111222/turnos" -Sesion $paciente
Probar "Un PACIENTE si puede ver su propio historial" ($historialPropio.Codigo -eq 200) "codigo=$($historialPropio.Codigo)"

# =================================================================== CIERRE ==

Write-Host ""
Write-Host ("=" * 60) -ForegroundColor DarkGray
Write-Host ("Resultado: {0} pruebas OK, {1} fallidas" -f $script:ok, $script:fallos) `
    -ForegroundColor $(if ($script:fallos -eq 0) { "Green" } else { "Red" })
Write-Host ("=" * 60) -ForegroundColor DarkGray

if ($script:fallos -gt 0) { exit 1 }
exit 0
