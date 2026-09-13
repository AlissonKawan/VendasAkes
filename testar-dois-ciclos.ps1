$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

& .\mvnw.cmd -B -ntp compile dependency:build-classpath '-Dmdep.includeScope=runtime' '-Dmdep.outputFile=target/akes-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o teste controlado.' }

New-Item -ItemType Directory -Force -Path target/tmp | Out-Null
$akesTmp = (Join-Path $PSScriptRoot 'target/tmp').Replace('\', '/')
$akesClasspath = 'target/classes;' + (Get-Content target/akes-classpath.txt -Raw).Trim()
$segredo = Read-Host 'Cole o NOVO token do Telegram (BotFather)' -AsSecureString
try {
    $env:TELEGRAM_BOT_TOKEN = [System.Net.NetworkCredential]::new('', $segredo).Password
    $env:TELEGRAM_CHAT_ID = '@vendasakes'
    $env:AKES_DIAGNOSTICO_INTERVAL_MS = '10000'
    & java '-Djava.net.preferIPv4Stack=true' "-Djdk.net.unixdomain.tmpdir=$akesTmp" `
        --class-path $akesClasspath br.com.akesofertas.scheduler.ExecutarDoisCiclosDiagnostico
} finally {
    Remove-Item Env:TELEGRAM_BOT_TOKEN -ErrorAction SilentlyContinue
    Remove-Item Env:AKES_DIAGNOSTICO_INTERVAL_MS -ErrorAction SilentlyContinue
    $segredo.Dispose()
}
