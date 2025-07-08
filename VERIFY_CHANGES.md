# Verificación de Cambios en el ChatBot

Esta guía te ayudará a verificar que los cambios realizados en la app están funcionando correctamente.

## ✅ Lista de Verificación

1. [ ] Ollama está correctamente instalado y configurado
2. [ ] El modelo IBM Granite está instalado
3. [ ] La app detecta el endpoint de Ollama
4. [ ] El chat responde usando el modelo IBM Granite
5. [ ] El sistema muestra mensajes de estado claros

## Pasos para Verificar

### 1. Preparar Ollama

1. Ejecuta los siguientes comandos en PowerShell como administrador:
   ```powershell
   $env:OLLAMA_HOST = '0.0.0.0:11434'
   ollama serve
   ```

2. En otra ventana de PowerShell, verifica que el modelo IBM Granite esté instalado:
   ```powershell
   ollama list
   ```

3. Si no aparece `ibm-granite/granite-3b-code-instruct-128k`, instálalo:
   ```powershell
   ollama pull ibm-granite/granite-3b-code-instruct-128k
   ```

### 2. Ejecutar el Script de Prueba

1. Ejecuta el script de verificación:
   ```powershell
   .\TEST_OLLAMA_CONNECTION.ps1
   ```

2. Comprueba que todos los tests pasan con ✅

### 3. Probar la App

1. Ejecuta la app en un emulador o dispositivo físico
2. Ve a la sección ChatBot
3. Observa el mensaje de estado:
   - Debería mostrar "✅ Conectado a Ollama exitosamente"
   - Debería indicar "🧠 Modelo: `ibm-granite/granite-3b-code-instruct-128k`"

4. Envía un mensaje de prueba como:
   ```
   Hola, ¿qué modelo de IA estás usando para responderme?
   ```

5. Verifica que la respuesta mencione el modelo IBM Granite

### 4. Probar Robustez

1. Detén Ollama (Ctrl+C en la ventana de PowerShell)
2. Envía un mensaje en el chat
3. Verifica que la app muestre un mensaje de error claro
4. Reinicia Ollama con:
   ```powershell
   $env:OLLAMA_HOST = '0.0.0.0:11434'
   ollama serve
   ```
5. Envía otro mensaje y verifica que el chat vuelva a funcionar

### 5. Probar con Archivos

1. Sube un archivo de código (por ejemplo, un archivo .java o .kt)
2. Haz una pregunta sobre el archivo
3. Verifica que la respuesta analice correctamente el código

## ✅ Verificación Completa

Si todos los pasos anteriores funcionan correctamente, los cambios están implementados con éxito. La app ahora:

- Detecta automáticamente el endpoint de Ollama
- Utiliza el modelo IBM Granite para respuestas
- Proporciona fallbacks a otros modelos si es necesario
- Muestra mensajes de estado claros
- Mantiene una conexión robusta con el servidor Ollama

## 🔍 Resolución de Problemas

Si encuentras algún problema, consulta:

- Archivo `OLLAMA_SETUP.md` para soluciones comunes
- Logs de Android Studio para detalles de errores
- Script `TEST_OLLAMA_CONNECTION.ps1` para diagnóstico de Ollama
