$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$envFile = Join-Path $PSScriptRoot '.env'

# 1. Carrega variáveis de ambiente existentes do arquivo .env
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $parts = $line.Split('=', 2)
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            if (-not [System.Environment]::GetEnvironmentVariable($key)) {
                [System.Environment]::SetEnvironmentVariable($key, $val)
            }
        }
    }
}

function Salvar-NoEnv($chave, $valor) {
    if (-not (Test-Path $envFile)) {
        New-Item -ItemType File -Path $envFile -Force | Out-Null
    }
    $linhas = Get-Content $envFile -ErrorAction SilentlyContinue
    $encontrou = $false
    $novasLinhas = @()
    if ($linhas) {
        foreach ($l in $linhas) {
            if ($l.StartsWith("$chave=")) {
                $novasLinhas += "$chave=$valor"
                $encontrou = $true
            } else {
                $novasLinhas += $l
            }
        }
    }
    if (-not $encontrou) {
        $novasLinhas += "$chave=$valor"
    }
    $novasLinhas | Set-Content $envFile
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       AKES OFERTAS - OUVINTE TELEGRAM 100% JAVA          " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$salvouNovo = $false

# 1. Credenciais do Telegram Client (my.telegram.org)
if (-not $env:TELEGRAM_API_ID -or $env:TELEGRAM_API_ID -eq "0") {
    $valor = Read-Host "Digite o seu TELEGRAM_API_ID (my.telegram.org)"
    $env:TELEGRAM_API_ID = $valor.Trim()
    Salvar-NoEnv "TELEGRAM_API_ID" $env:TELEGRAM_API_ID
    $salvouNovo = $true
}

if (-not $env:TELEGRAM_API_HASH) {
    $valor = Read-Host "Digite o seu TELEGRAM_API_HASH (my.telegram.org)"
    $env:TELEGRAM_API_HASH = $valor.Trim()
    Salvar-NoEnv "TELEGRAM_API_HASH" $env:TELEGRAM_API_HASH
    $salvouNovo = $true
}

# 2. Token do Bot para publicar no seu canal (@BotFather)
if (-not $env:TELEGRAM_BOT_TOKEN) {
    $segredo = Read-Host "Cole o Token do seu Bot (@BotFather)" -AsSecureString
    $tokenTexto = [System.Net.NetworkCredential]::new('', $segredo).Password
    $env:TELEGRAM_BOT_TOKEN = $tokenTexto.Trim()
    Salvar-NoEnv "TELEGRAM_BOT_TOKEN" $env:TELEGRAM_BOT_TOKEN
    $salvouNovo = $true
}

# 3. ID do seu canal de ofertas
if (-not $env:TELEGRAM_CHAT_ID) {
    $canal = Read-Host "Digite o @ do seu canal (padrao: @vendasakes)"
    $env:TELEGRAM_CHAT_ID = if ($canal) { $canal.Trim() } else { "@vendasakes" }
    Salvar-NoEnv "TELEGRAM_CHAT_ID" $env:TELEGRAM_CHAT_ID
    $salvouNovo = $true
}

if ($salvouNovo) {
    Write-Host "💾 Credenciais salvas permanentemente no arquivo .env (ignorado pelo Git)!" -ForegroundColor Yellow
} else {
    Write-Host "✅ Credenciais carregadas automaticamente do arquivo .env!" -ForegroundColor Green
}

# 4. Configurações de execução
$env:TELEGRAM_LISTENER_ENABLED = 'true'
$env:AKES_SCHEDULER_ENABLED = 'true'

Write-Host "`n🚀 Iniciando Akes Ofertas..." -ForegroundColor Green
Write-Host "-> Motor nativo TDLight Java ativo" -ForegroundColor Gray
Write-Host "-> Fluxo 1: Varredura e salvamento de cupons válidos no banco" -ForegroundColor Gray
Write-Host "-> Fluxo 2: Navegação Playwright no link de cada cupom para coletar produtos" -ForegroundColor Gray
Write-Host "-> Monitoramento em tempo real ativo`n" -ForegroundColor Gray

.\mvnw.cmd spring-boot:run
