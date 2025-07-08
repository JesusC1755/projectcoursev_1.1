@echo off
echo ===============================
echo  INSTALADOR DEL MODELO GRANITE
echo ===============================
echo.
echo Este script instalara el modelo IBM Granite que es 
echo OBLIGATORIO para la aplicacion.
echo.
echo IMPORTANTE: Asegurate de que Ollama este ejecutandose.
echo.
pause

REM Verificar si Ollama está instalado
where ollama >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Ollama no esta instalado.
    echo Por favor, instala Ollama desde: https://ollama.com/download
    echo.
    pause
    exit /b 1
)

REM Verificar si el servidor Ollama está en ejecución
curl -s -f http://localhost:11434/api/tags >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] El servidor Ollama no esta ejecutandose.
    echo Por favor, ejecuta primero "ollama serve" en una terminal.
    echo.
    pause
    exit /b 1
)

echo [INFO] Instalando el modelo IBM Granite...
echo Este proceso puede tardar varios minutos dependiendo de tu conexion a internet.
echo.

ollama pull ibm-granite/granite-3b-code-instruct-128k

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Hubo un problema al instalar el modelo.
    echo Por favor, intenta ejecutar manualmente:
    echo ollama pull ibm-granite/granite-3b-code-instruct-128k
    echo.
    pause
    exit /b 1
)

echo.
echo [EXITO] El modelo IBM Granite ha sido instalado correctamente!
echo Ya puedes usar la aplicacion con todas sus funcionalidades.
echo.
pause
