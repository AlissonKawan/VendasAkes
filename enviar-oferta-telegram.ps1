$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# Execute entrar-afiliados.ps1 antes, faça login manual e feche a janela do perfil.
# Compila sem credenciais no processo Maven e usa somente classes de produção.
& .\mvnw.cmd -B -ntp compile dependency:build-classpath '-Dmdep.includeScope=runtime' '-Dmdep.outputFile=target/telegram-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o diagnóstico.' }
New-Item -ItemType Directory -Force -Path target/tmp | Out-Null
$telegramTmp = (Join-Path $PSScriptRoot 'target/tmp').Replace('\', '/')
$telegramClasspath = 'target/classes;' + (Get-Content target/telegram-classpath.txt -Raw).Trim()
$segredo = Read-Host 'Cole o NOVO token do Telegram (BotFather)' -AsSecureString
try {
    $env:TELEGRAM_BOT_TOKEN = [System.Net.NetworkCredential]::new('', $segredo).Password
    $env:TELEGRAM_CHAT_ID = '@vendasakes'
    & java '-Djava.net.preferIPv4Stack=true' "-Djdk.net.unixdomain.tmpdir=$telegramTmp" `
        --class-path $telegramClasspath br.com.akesofertas.telegram.EnviarOfertaTelegramDiagnostico
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Diagnóstico não concluído. Confira o resultado antes de repetir.' -ForegroundColor Yellow
        return
    }
} finally {
    Remove-Item Env:TELEGRAM_BOT_TOKEN -ErrorAction SilentlyContinue
    $segredo.Dispose()
}
