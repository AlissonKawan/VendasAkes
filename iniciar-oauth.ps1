$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

# Recupera variáveis salvas no Windows, sem imprimir as credenciais.
foreach ($name in @('MERCADOLIVRE_APP_ID', 'MERCADOLIVRE_CLIENT_SECRET')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        foreach ($scope in @('User', 'Machine')) {
            $value = [Environment]::GetEnvironmentVariable($name, $scope)
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                [Environment]::SetEnvironmentVariable($name, $value, 'Process')
                break
            }
        }
    }
}

if ([string]::IsNullOrWhiteSpace($env:MERCADOLIVRE_APP_ID)) {
    $env:MERCADOLIVRE_APP_ID = Read-Host 'App ID do Mercado Livre'
}
if ([string]::IsNullOrWhiteSpace($env:MERCADOLIVRE_CLIENT_SECRET)) {
    # A digitação fica oculta e o segredo é passado apenas ao processo Java.
    $secret = Read-Host 'Client Secret do Mercado Livre' -AsSecureString
    $env:MERCADOLIVRE_CLIENT_SECRET = [System.Net.NetworkCredential]::new('', $secret).Password
    $secret.Dispose()
}
if ([string]::IsNullOrWhiteSpace($env:MERCADOLIVRE_APP_ID) -or
    [string]::IsNullOrWhiteSpace($env:MERCADOLIVRE_CLIENT_SECRET)) {
    throw 'App ID e Client Secret são obrigatórios.'
}

if ([string]::IsNullOrWhiteSpace($env:MERCADOLIVRE_REDIRECT_URI)) {
    $env:MERCADOLIVRE_REDIRECT_URI = 'https://droop-juncture-june.ngrok-free.dev/mercadolivre/callback'
}
if ([string]::IsNullOrWhiteSpace($env:MERCADOLIVRE_PKCE_ENABLED)) {
    $env:MERCADOLIVRE_PKCE_ENABLED = 'true'
}

New-Item -ItemType Directory -Force target/tmp | Out-Null
$oauthTmp = ((Resolve-Path target/tmp).Path).Replace('\', '/')
Write-Host ('Callback: ' + $env:MERCADOLIVRE_REDIRECT_URI)
Write-Host 'Mantenha o ngrok aberto. Inicie a autorização pelo domínio HTTPS do callback.'
& .\mvnw.cmd -B -ntp '-Dspring-boot.run.profiles=oauth' "-Dspring-boot.run.jvmArguments=-Djava.net.preferIPv4Stack=true -Djdk.net.unixdomain.tmpdir=$oauthTmp" spring-boot:run
