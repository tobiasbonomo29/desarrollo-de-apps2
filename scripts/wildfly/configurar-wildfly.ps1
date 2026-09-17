<#
    =========================================================================
     MediConecta - configuracion completa de WildFly en un solo paso
    =========================================================================

     Deja el servidor listo para desplegar mediconecta.war:

       1. descarga el driver JDBC de PostgreSQL desde Maven Central;
       2. lo instala como modulo de WildFly (org.postgresql);
       3. crea el datasource java:/jdbc/MediConectaDS;
       4. crea los cuatro usuarios de demostracion con sus roles.

     Uso (PowerShell, desde cualquier carpeta):

         .\configurar-wildfly.ps1 -WildFly "C:\wildfly-36.0.1.Final"

     Los pasos 1 y 2 necesitan el servidor APAGADO.
     El paso 3 necesita el servidor ENCENDIDO: el script avisa y espera.

     Las credenciales de PostgreSQL se pueden pasar por parametro; el valor por
     omision es el del contenedor Docker de scripts/db/levantar-postgres.ps1.
     No hay ninguna contrasena de base de datos dentro del codigo Java.
    =========================================================================
#>

param(
    [Parameter(Mandatory = $true)]
    [string] $WildFly,

    [string] $VersionDriver = "42.7.4",
    [string] $UsuarioBase   = "mediconecta",
    [string] $ClaveBase     = "mediconecta",
    [string] $UrlBase       = "jdbc:postgresql://localhost:5432/mediconecta",
    [string] $ClaveDemo     = "Demo.2026",
    [switch] $SoloUsuarios,
    [switch] $SoloDatasource
)

$ErrorActionPreference = "Stop"

function Paso($texto) {
    Write-Host ""
    Write-Host "==> $texto" -ForegroundColor Cyan
}

if (-not (Test-Path $WildFly)) {
    throw "No existe la carpeta de WildFly: $WildFly"
}

$cli = Join-Path $WildFly "bin\jboss-cli.bat"
$addUser = Join-Path $WildFly "bin\add-user.bat"

# ---------------------------------------------------------------- 1 y 2 -----
if (-not $SoloUsuarios -and -not $SoloDatasource) {

    Paso "Descargando el driver de PostgreSQL $VersionDriver"

    $destino = Join-Path $env:TEMP "postgresql-$VersionDriver.jar"
    if (-not (Test-Path $destino)) {
        $url = "https://repo1.maven.org/maven2/org/postgresql/postgresql/$VersionDriver/postgresql-$VersionDriver.jar"
        Invoke-WebRequest -Uri $url -OutFile $destino -UseBasicParsing
    }
    Write-Host "   Driver en $destino"

    Paso "Instalando el modulo org.postgresql en WildFly (el servidor debe estar apagado)"

    $modulo = Join-Path $WildFly "modules\org\postgresql\main"
    if (Test-Path (Join-Path $modulo "module.xml")) {
        Write-Host "   El modulo ya estaba instalado."
    } else {
        & $cli "--command=module add --name=org.postgresql --resources=$destino --dependencies=jakarta.transaction.api,java.sql,java.logging"
        if ($LASTEXITCODE -ne 0) { throw "Fallo la instalacion del modulo." }
        Write-Host "   Modulo instalado en $modulo"
    }
}

# ------------------------------------------------------------------- 3 ------
if (-not $SoloUsuarios) {

    Paso "Creando el datasource java:/jdbc/MediConectaDS (el servidor debe estar ENCENDIDO)"

    $script = Join-Path $PSScriptRoot "configurar-datasource.cli"

    & $cli --connect `
        "-Dmediconecta.db.url=$UrlBase" `
        "-Dmediconecta.db.usuario=$UsuarioBase" `
        "-Dmediconecta.db.clave=$ClaveBase" `
        "--file=$script"

    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo crear el datasource. Verifica que WildFly este levantado y que PostgreSQL responda."
    }
}

# ------------------------------------------------------------------- 4 ------
if (-not $SoloDatasource) {

    Paso "Creando los usuarios de demostracion"

    $usuarios = @(
        @{ Usuario = "paciente";  Rol = "PACIENTE"  },
        @{ Usuario = "recepcion"; Rol = "RECEPCION" },
        @{ Usuario = "medico";    Rol = "MEDICO"    },
        @{ Usuario = "admin";     Rol = "ADMIN"     }
    )

    foreach ($u in $usuarios) {
        & $addUser -a -u $u.Usuario -p $ClaveDemo -g $u.Rol | Out-Null
        Write-Host ("   {0,-10} rol {1}" -f $u.Usuario, $u.Rol)
    }

    Write-Host ""
    Write-Host "   Son usuarios locales ficticios, solo para la defensa." -ForegroundColor Yellow
    Write-Host "   Quedan en standalone\configuration\application-users.properties" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "WildFly configurado. Ya se puede copiar target\mediconecta.war a standalone\deployments\." -ForegroundColor Green
