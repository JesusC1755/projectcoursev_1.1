@echo off
REM Script para verificar e instalar el modelo Granite en Ollama

echo =======================================================
echo  VERIFICADOR DE MODELO GRANITE PARA OLLAMA
echo =======================================================
echo.

REM Verificar si Ollama está instalado
where ollama >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Ollama no está instalado o no está en el PATH.
    echo Por favor, instala Ollama desde: https://ollama.com/download
    echo.
    pause
    exit /b 1
)

echo [INFO] Verificando si Ollama está ejecutándose...

REM Intentar conectar con Ollama
curl -s -f http://localhost:11434/api/tags 2>nul >nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] El servidor Ollama no está ejecutándose.
    echo Por favor, inicia el servidor Ollama y vuelve a ejecutar este script.
    echo.
    pause
    exit /b 1
)

echo [OK] Ollama está ejecutándose.
echo.
echo [INFO] Verificando si el modelo Granite está instalado...

REM Verificar si el modelo está instalado
curl -s http://localhost:11434/api/tags | findstr /C:"ibm-granite/granite-3b-code-instruct-128k" >nul
if %ERRORLEVEL% NEQ 0 (
    echo [AVISO] El modelo IBM Granite no está instalado.
    echo.
    
    choice /C SN /M "¿Deseas instalar el modelo ahora? (S/N)"
    if %ERRORLEVEL% EQU 1 (
        echo.
        echo [INFO] Instalando modelo IBM Granite. Esto puede tardar varios minutos...
        echo.
        
        ollama pull ibm-granite/granite-3b-code-instruct-128k
        
        if %ERRORLEVEL% NEQ 0 (
            echo.
            echo [ERROR] Error al instalar el modelo. Por favor, intenta instalarlo manualmente:
            echo ollama pull ibm-granite/granite-3b-code-instruct-128k
            echo.
            echo O descarga manualmente desde:
            echo https://huggingface.co/mradermacher/granite-3b-code-instruct-128k-GGUF/resolve/main/granite-3b-code-instruct-128k.Q4_K_M.gguf
            echo.
            pause
            exit /b 1
        )
        
        echo.
        echo [OK] Modelo IBM Granite instalado correctamente.
    ) else (
        echo.
        echo [INFO] La aplicación NO funcionará correctamente sin el modelo IBM Granite.
        echo Puedes instalarlo más tarde con el comando:
        echo ollama pull ibm-granite/granite-3b-code-instruct-128k
    )
) else (
    echo [OK] El modelo IBM Granite está correctamente instalado.
)

echo.
echo [INFO] Probando conexión con el modelo Granite...
echo.

REM Realizar prueba de conexión
curl -s -X POST http://localhost:11434/api/generate -d '{
  "model": "ibm-granite/granite-3b-code-instruct-128k",
  "prompt": "Responde solo con una palabra: Funciona",
  "stream": false
}' | findstr "response" >nul

if %ERRORLEVEL% EQU 0 (
    echo [OK] Conexión exitosa con el modelo IBM Granite.
    echo La aplicación debería funcionar correctamente ahora.
) else (
    echo [ERROR] No se pudo obtener respuesta del modelo.
    echo Verifica que el modelo esté correctamente instalado con:
    echo ollama list
)

echo.
echo =======================================================
echo.
pause
