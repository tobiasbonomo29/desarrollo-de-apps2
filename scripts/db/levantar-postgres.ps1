<#
    =========================================================================
     MediConecta - PostgreSQL para desarrollo
    =========================================================================

     Levanta un PostgreSQL en un contenedor Docker con la base, el usuario y la
     contrasena que espera el datasource de WildFly. Es la forma mas rapida de
     tener la base andando sin instalar nada en la maquina.

         .\levantar-postgres.ps1            # crea o arranca el contenedor
         .\levantar-postgres.ps1 -Borrar    # lo elimina junto con sus datos

     Si preferis un PostgreSQL instalado localmente, no hace falta este script:
     alcanza con ejecutar scripts\db\crear-base.sql con psql como superusuario.

     Docker se usa solo para la base de datos. El WAR no depende de Docker.
    =========================================================================
#>

param(
    [string] $Nombre    = "mediconecta-postgres",
    [string] $Version   = "16-alpine",
    [int]    $Puerto    = 5432,
    [string] $Base      = "mediconecta",
    [string] $Usuario   = "mediconecta",
    [string] $Clave     = "mediconecta",
    [switch] $Borrar
)

$ErrorActionPreference = "Stop"

docker version --format "{{.Server.Version}}" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker no responde. Abri Docker Desktop y volve a intentar."
}

if ($Borrar) {
    docker rm -f $Nombre | Out-Null
    Write-Host "Contenedor $Nombre eliminado (se perdieron sus datos)." -ForegroundColor Yellow
    exit 0
}

$existe = docker ps -a --filter "name=^/$Nombre$" --format "{{.Names}}"

if ($existe -eq $Nombre) {
    docker start $Nombre | Out-Null
    Write-Host "Contenedor $Nombre en marcha (ya existia, se conservan los datos)." -ForegroundColor Green
} else {
    docker run -d --name $Nombre `
        -e POSTGRES_DB=$Base `
        -e POSTGRES_USER=$Usuario `
        -e POSTGRES_PASSWORD=$Clave `
        -p "${Puerto}:5432" `
        "postgres:$Version" | Out-Null
    Write-Host "Contenedor $Nombre creado." -ForegroundColor Green
}

Write-Host ""
Write-Host "  Base      : $Base"
Write-Host "  Usuario   : $Usuario"
Write-Host "  URL JDBC  : jdbc:postgresql://localhost:$Puerto/$Base"
Write-Host ""
Write-Host "Las tablas las crea Hibernate al desplegar el WAR." -ForegroundColor DarkGray
Write-Host "Para entrar por consola:  docker exec -it $Nombre psql -U $Usuario -d $Base" -ForegroundColor DarkGray
