$ErrorActionPreference = 'Stop'

foreach ($arquivo in @('oferta-teste-mouse.json', 'oferta-teste-fone.json')) {
    $json = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot $arquivo))
    $resposta = Invoke-RestMethod -Method Post `
        -Uri 'http://127.0.0.1:8080/api/ofertas/publicar' `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
    $resposta | ConvertTo-Json
    Start-Sleep -Seconds 2
}
