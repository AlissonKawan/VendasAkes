param([switch]$Diagnostico)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if ($Diagnostico) {
    throw 'O modo Diagnostico exige Playwright e nao deve ser usado durante o login. Execute sem -Diagnostico.'
}

# O login precisa acontecer em um Chrome normal. Google e outros provedores podem
# recusar autenticação quando a janela foi iniciada diretamente pelo Playwright.
# Depois que esta janela for fechada, o Akes reutiliza o mesmo perfil persistente.
$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
if (-not (Test-Path -LiteralPath $chrome)) {
    throw "Chrome nao encontrado em: $chrome"
}

$profile = Join-Path $PSScriptRoot '.local\mercadolivre'
New-Item -ItemType Directory -Force -Path $profile | Out-Null

Write-Host 'Abrindo o perfil exclusivo do Akes em um Chrome normal.'
Write-Host 'Entre manualmente no Mercado Livre e confirme o acesso ao portal de afiliados.'
Write-Host 'Depois, feche TODA esta janela do Chrome antes de iniciar o Akes.'

$arguments = @(
    "--user-data-dir=$profile"
    '--no-first-run'
    '--no-default-browser-check'
    '--new-window'
    'https://www.mercadolivre.com.br/afiliados/hub?is_affiliate=true#menu-user'
)
$process = Start-Process -FilePath $chrome -ArgumentList $arguments -PassThru
$process.WaitForExit()

Write-Host 'Chrome encerrado. A sessao foi mantida no perfil .local/mercadolivre.'
