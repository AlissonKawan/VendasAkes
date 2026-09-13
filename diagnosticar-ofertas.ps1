param(
    [string]$UrlOfertas = 'https://www.mercadolivre.com.br/afiliados/hub?is_affiliate=true#menu-user',
    [ValidateRange(30, 1800)][int]$Segundos = 600
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# Observador manual separado do Spring. Nunca exporta a sessao do perfil privado.
& .\mvnw.cmd -B -ntp compile dependency:build-classpath '-Dmdep.outputFile=target/afiliados-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel compilar o diagnostico.' }
New-Item -ItemType Directory -Force -Path target/tmp | Out-Null
$diagnosticoTmp = (Join-Path $PSScriptRoot 'target/tmp').Replace('\', '/')
$diagnosticoClasspath = 'target/classes;' + (Get-Content target/afiliados-classpath.txt -Raw).Trim()
& java '-Djava.net.preferIPv4Stack=true' "-Djdk.net.unixdomain.tmpdir=$diagnosticoTmp" `
    --class-path $diagnosticoClasspath br.com.akesofertas.diagnostico.DiagnosticoOfertasNetwork $UrlOfertas $Segundos
if ($LASTEXITCODE -ne 0) { throw 'O diagnostico nao concluiu. Confira o navegador e o relatorio privado.' }
