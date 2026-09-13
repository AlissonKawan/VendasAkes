$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $parts = $line.Split('=', 2)
            $key = $parts[0].Trim()
            $value = $parts[1].Trim()
            if (-not [Environment]::GetEnvironmentVariable($key)) {
                [Environment]::SetEnvironmentVariable($key, $value)
            }
        }
    }
}

& .\mvnw.cmd -B -ntp compile dependency:build-classpath '-Dmdep.includeScope=runtime' '-Dmdep.outputFile=target/akes-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o Akes Ofertas.' }

New-Item -ItemType Directory -Force -Path target/tmp | Out-Null
$akesTmp = (Join-Path $PSScriptRoot 'target/tmp').Replace('\', '/')
$akesClasspath = 'target/classes;' + (Get-Content target/akes-classpath.txt -Raw).Trim()
$segredo = $null
try {
    if (-not $env:TELEGRAM_BOT_TOKEN) {
        $segredo = Read-Host 'Cole o token do Telegram (BotFather)' -AsSecureString
        $env:TELEGRAM_BOT_TOKEN = [System.Net.NetworkCredential]::new('', $segredo).Password
    }
    if (-not $env:TELEGRAM_CHAT_ID) { $env:TELEGRAM_CHAT_ID = '@vendasakes' }
    if (-not $env:TELEGRAM_API_ID -or -not $env:TELEGRAM_API_HASH) {
        throw 'TELEGRAM_API_ID/TELEGRAM_API_HASH ausentes. Configure-os no arquivo .env.'
    }
    $env:TELEGRAM_LISTENER_ENABLED = 'true'
    $env:AKES_SCHEDULER_ENABLED = 'true'
    Write-Host 'Ouvinte do canal de cupons: ATIVO' -ForegroundColor Green
    & java '-Djava.net.preferIPv4Stack=true' "-Djdk.net.unixdomain.tmpdir=$akesTmp" `
        --class-path $akesClasspath br.com.akesofertas.AkesOfertasApplication
} finally {
    Remove-Item Env:AKES_SCHEDULER_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:TELEGRAM_LISTENER_ENABLED -ErrorAction SilentlyContinue
    if ($segredo) { $segredo.Dispose() }
}
