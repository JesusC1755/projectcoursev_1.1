# TEST_OLLAMA_CONNECTION.ps1
# Script para probar la conexión con Ollama y los modelos requeridos
# ------------------------------------------------------------------

# Colores para salida
$Green = [ConsoleColor]::Green
$Red = [ConsoleColor]::Red
$Yellow = [ConsoleColor]::Yellow

Write-Host "=== TEST DE CONEXIÓN CON OLLAMA ===" -ForegroundColor $Yellow
Write-Host "Verificando configuración para la app de Android"
Write-Host ""

# Comprobar si Ollama está instalado
try {
    $ollamaVersion = ollama -v
    Write-Host "✅ Ollama está instalado" -ForegroundColor $Green
    Write-Host "   Versión: $ollamaVersion"
} catch {
    Write-Host "❌ Ollama no está instalado" -ForegroundColor $Red
    Write-Host "Por favor, instala Ollama desde https://ollama.com/"
    exit
}

# Comprobar si el servicio Ollama está ejecutándose
try {
    $response = Invoke-WebRequest -Uri "http://localhost:11434/api/tags" -UseBasicParsing -ErrorAction Stop
    if ($response.StatusCode -eq 200) {
        Write-Host "✅ Servicio Ollama está ejecutándose" -ForegroundColor $Green
    }
} catch {
    Write-Host "❌ Servicio Ollama no está ejecutándose" -ForegroundColor $Red
    Write-Host "Ejecuta el siguiente comando en PowerShell:"
    Write-Host '$env:OLLAMA_HOST = "0.0.0.0:11434"'
    Write-Host "ollama serve"
    exit
}

# Comprobar si el host está configurado correctamente
Write-Host "`nVerificando configuración de host..." -ForegroundColor $Yellow
try {
    $response = Invoke-WebRequest -Uri "http://0.0.0.0:11434/api/tags" -UseBasicParsing -ErrorAction SilentlyContinue
    Write-Host "✅ Ollama está configurado para aceptar conexiones externas (0.0.0.0)" -ForegroundColor $Green
} catch {
    Write-Host "⚠️ Ollama podría no estar escuchando en 0.0.0.0" -ForegroundColor $Yellow
    Write-Host "Para aceptar conexiones desde Android, ejecuta:"
    Write-Host '$env:OLLAMA_HOST = "0.0.0.0:11434"'
    Write-Host "ollama serve"
}

# Listar modelos disponibles
Write-Host "`nModelos disponibles:" -ForegroundColor $Yellow
try {
    $modelsJson = Invoke-WebRequest -Uri "http://localhost:11434/api/tags" -UseBasicParsing | ConvertFrom-Json
    $models = $modelsJson.models | ForEach-Object { $_.name }
    foreach ($model in $models) {
        Write-Host "- $model"
    }
} catch {
    Write-Host "❌ Error obteniendo la lista de modelos" -ForegroundColor $Red
}

# Verificar si el modelo requerido está disponible
Write-Host "`nVerificando modelo principal (ibm-granite/granite-3b-code-instruct-128k)..." -ForegroundColor $Yellow
if ($models -contains "ibm-granite/granite-3b-code-instruct-128k") {
    Write-Host "✅ Modelo ibm-granite/granite-3b-code-instruct-128k está instalado" -ForegroundColor $Green
} else {
    Write-Host "❌ Modelo ibm-granite/granite-3b-code-instruct-128k NO está instalado" -ForegroundColor $Red
    Write-Host "Este modelo es requerido para la funcionalidad completa del chat."
    Write-Host "Para instalarlo, ejecuta:"
    Write-Host "ollama pull ibm-granite/granite-3b-code-instruct-128k"
}

# Verificar modelos alternativos
Write-Host "`nVerificando modelos alternativos..." -ForegroundColor $Yellow
if ($models -contains "llama3") {
    Write-Host "✅ Modelo llama3 está instalado" -ForegroundColor $Green
} else {
    Write-Host "⚠️ Modelo llama3 no está instalado (alternativa de respaldo)" -ForegroundColor $Yellow
    Write-Host "Para instalarlo, ejecuta: ollama pull llama3"
}

# Probar conexión API básica
Write-Host "`nProbando API de Ollama..." -ForegroundColor $Yellow
try {
    $body = @{
        model = "llama3"
        prompt = "test"
        stream = $false
        options = @{
            max_tokens = 10
        }
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri "http://localhost:11434/api/generate" -Method Post -Body $body -ContentType "application/json" -UseBasicParsing
    Write-Host "✅ API de Ollama responde correctamente" -ForegroundColor $Green
} catch {
    Write-Host "❌ Error al comunicarse con la API de Ollama" -ForegroundColor $Red
    Write-Host "Verifica que el servicio esté funcionando y el firewall permita conexiones"
}

# Prueba de conexión desde la red local
Write-Host "`nPrueba de acceso desde la red:" -ForegroundColor $Yellow
$ipAddresses = @(Get-NetIPAddress | Where-Object {$_.AddressFamily -eq "IPv4" -and $_.PrefixOrigin -ne "WellKnown"})
Write-Host "Tus direcciones IP locales son:"
foreach ($ip in $ipAddresses) {
    Write-Host "- $($ip.IPAddress) ($($ip.InterfaceAlias))"
}

Write-Host "`nPara probar manualmente desde otro dispositivo:"
Write-Host "curl http://<TU-IP-LOCAL>:11434/api/generate -d '{`"model`":`"llama3`",`"prompt`":`"test`",`"stream`":false}'"

# Verificar puerto
Write-Host "`nVerificando si el puerto 11434 está abierto..." -ForegroundColor $Yellow
$portCheck = Get-NetTCPConnection -LocalPort 11434 -ErrorAction SilentlyContinue
if ($portCheck) {
    Write-Host "✅ Puerto 11434 está abierto y en uso" -ForegroundColor $Green
} else {
    Write-Host "❌ Puerto 11434 no parece estar en uso" -ForegroundColor $Red
    Write-Host "Asegúrate de que Ollama esté ejecutándose con '$env:OLLAMA_HOST = `"0.0.0.0:11434`"'"
}

# Resumen final
Write-Host "`n=== RESUMEN ===" -ForegroundColor $Yellow
Write-Host "Para que la app Android funcione correctamente:"
Write-Host "1. Ollama debe estar ejecutándose con: `$env:OLLAMA_HOST = '0.0.0.0:11434'"
Write-Host "2. El modelo ibm-granite/granite-3b-code-instruct-128k debe estar instalado"
Write-Host "3. El puerto 11434 debe estar accesible desde tu dispositivo Android"
Write-Host ""
Write-Host "Si la app no se conecta, revisa OLLAMA_SETUP.md para soluciones" -ForegroundColor $Yellow
