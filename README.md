# App de Evaluación de Tareas con IA

Esta aplicación Android permite analizar archivos de tareas usando modelos de IA locales a través de Ollama.

## Características

- Chatbot con IA local usando Ollama
- Análisis de archivos con detección automática de tipo
- Interfaz limpia y fácil de usar
- Configuración automática de endpoints
- Soporte para múltiples modelos de IA

## Requisitos

- Android 8.0+ (API 26)
- Ollama instalado en el ordenador
- Modelo IBM Granite instalado (`ibm-granite/granite-3b-code-instruct-128k`)

## Configuración de Ollama

Para la funcionalidad completa del chat, es necesario configurar Ollama correctamente:

1. Instala Ollama desde [ollama.com](https://ollama.com/)
2. Ejecuta Ollama en modo servidor:
   ```
   # Windows (PowerShell como administrador)
   $env:OLLAMA_HOST = '0.0.0.0:11434'
   ollama serve
   
   # Linux/Mac
   OLLAMA_HOST=0.0.0.0:11434 ollama serve
   ```
3. Descarga el modelo requerido:
   ```
   ollama pull ibm-granite/granite-3b-code-instruct-128k
   ```

## Prueba de Conexión

Para verificar que todo está configurado correctamente:

1. Ejecuta el script de prueba incluido:
   ```
   # Windows (PowerShell)
   .\TEST_OLLAMA_CONNECTION.ps1
   
   # Linux/Mac
   chmod +x ./TEST_OLLAMA_CONNECTION.sh
   ./TEST_OLLAMA_CONNECTION.sh
   ```
2. Verifica que el modelo IBM Granite esté instalado
3. Asegúrate de que Ollama está aceptando conexiones externas (0.0.0.0)

Para solucionar problemas comunes, consulta el archivo [OLLAMA_SETUP.md](OLLAMA_SETUP.md).

## Desarrollo

El proyecto está estructurado en:

- `service/`: Servicios de conexión con Ollama y análisis de archivos
- `ui/`: Fragmentos y actividades para la interfaz de usuario
- `data/`: Entidades, DAOs y modelos de datos

## Solución de Problemas

Si encuentras problemas de conexión:

1. Verifica que Ollama esté ejecutándose con `$env:OLLAMA_HOST = '0.0.0.0:11434'`
2. Asegúrate de que el firewall permite conexiones al puerto 11434
3. Comprueba que el modelo IBM Granite está instalado
4. Revisa los logs en Android Studio para más detalles

## Licencia

Este proyecto es de código abierto bajo la licencia MIT.
