$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
& .\mvnw.cmd -B -ntp compile dependency:build-classpath '-Dmdep.outputFile=target/afiliados-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel compilar o cliente do Hub.' }
New-Item -ItemType Directory -Force -Path target/tmp | Out-Null
$hubTmp = (Join-Path $PSScriptRoot 'target/tmp').Replace('\', '/')
$hubClasspath = 'target/classes;' + (Get-Content target/afiliados-classpath.txt -Raw).Trim()
& java '-Djava.net.preferIPv4Stack=true' "-Djdk.net.unixdomain.tmpdir=$hubTmp" `
    --class-path $hubClasspath br.com.akesofertas.afiliados.ofertas.ConsultarOfertasHub
if ($LASTEXITCODE -ne 0) { throw 'Consulta interrompida. Verifique o acesso manual ao Hub antes de executar novamente.' }
