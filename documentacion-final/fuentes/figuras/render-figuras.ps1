# Rasteriza las figuras HTML a PNG con Microsoft Edge headless (sin dependencias extra).
$edge = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
$dir = $PSScriptRoot; $out = Join-Path (Split-Path $dir) "img"
New-Item -ItemType Directory -Force $out | Out-Null
$tam = @{ "arquitectura"="1200,760"; "modelo-datos"="1200,700"; "mensajeria"="1200,520"; "ia-gateway"="1200,380"; "pipeline"="1200,340"; "grafico-metricas"="1260,470"; "grafico-paquetes"="1260,610" }
foreach ($n in $tam.Keys) {
  $src = "file:///" + (Join-Path $dir "$n.html").Replace([char]92, [char]47)
  & $edge --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 "--window-size=$($tam[$n])" "--screenshot=$(Join-Path $out "$n.png")" $src 2>$null | Out-Null
}
Get-ChildItem $out -Filter *.png | Select-Object Name, Length
