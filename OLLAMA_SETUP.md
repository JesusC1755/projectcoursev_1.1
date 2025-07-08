# Configuración de Ollama para la App Android

## ⚠️ REQUISITO OBLIGATORIO - MODELO GRANITE ⚠️

Esta aplicación REQUIERE el modelo IBM Granite para funcionar correctamente:

```
ibm-granite/granite-3b-code-instruct-128k
```

Si ves este error: `{"error":"model 'ibm-granite/granite-3b-code-instruct-128k' not found"}`, significa que el modelo no está instalado.

### Instalación del modelo IBM Granite:

```bash
# Asegúrate de que Ollama esté ejecutándose y luego ejecuta:
ollama pull ibm-granite/granite-3b-code-instruct-128k
```

> 📌 IMPORTANTE: La aplicación está diseñada para usar EXCLUSIVAMENTE el modelo IBM Granite y NO usará otros modelos como fallback.

Para una fácil verificación, usa el script incluido:
- Windows: `VERIFICAR_MODELO_GRANITE.bat`
- Linux/Mac: `VERIFICAR_MODELO_GRANITE.sh`

## ⚠️ SOLUCIÓN PARA ERRORES DE CONEXIÓN

### Si ves error: "failed to connect to /10.0.2.2 (port 11434)"

#### 1. Verificar que Ollama esté corriendo:

```bash
# Abrir PowerShell como administrador y ejecutar:
$env:OLLAMA_HOST = '0.0.0.0:11434'
ollama serve
```

#### 2. Verificar que el puerto esté abierto:

```bash
# En otra ventana de PowerShell:
netstat -an | findstr 11434
# Deberías ver: TCP 0.0.0.0:11434 0.0.0.0:0 LISTENING
```

#### 3. Probar con curl desde tu PC:

```bash
# Probar el endpoint:
curl http://localhost:11434/api/generate -d '{"model":"llama3.2","prompt":"test","stream":false}'
```

#### 4. Configurar Firewall de Windows:

```bash
# Ejecutar como administrador:
netsh advfirewall firewall add rule name="Ollama" dir=in action=allow protocol=TCP localport=11434
```

#### 5. Para dispositivo físico (no emulador):

1. Encuentra tu IP local:
```bash
ipconfig
# Busca la IP de tu adaptador de red (ej: 192.168.1.117)
```

2. En el dispositivo Android, la app detectará automáticamente la IP correcta entre:
   - `http://10.0.2.2:11434/` (emulador)
   - `http://localhost:11434/` (localhost)
   - `http://127.0.0.1:11434/` (loopback)
   - `http://192.168.1.117:11434/` (tu IP local)

## Pasos para configurar Ollama:

### 1. Instalar y configurar Ollama

```bash
# Descargar e instalar Ollama desde https://ollama.com/
# En Windows:
# Ejecutar como administrador en PowerShell:

# Configurar Ollama para aceptar conexiones externas
$env:OLLAMA_HOST = '0.0.0.0:11434'  # IMPORTANTE: usar 0.0.0.0, no localhost
ollama serve
```

### 2. Descargar modelo de IA

```bash
# En otra terminal/PowerShell:
ollama pull llama3.2
```

### 3. Verificar que funcione

```bash
# Probar el modelo:
ollama run llama3.2
# Escribir una pregunta de prueba como "Hola, ¿cómo estás?"
```

### 4. Verificar conectividad

1. Abrir la app y ir al ChatBot
2. La app probará automáticamente múltiples direcciones
3. Debería mostrar "✅ Conectado a Ollama exitosamente"
4. Si aparece "⚠️ No se pudo conectar", verificar:
   - Ollama está ejecutándose con `$env:OLLAMA_HOST = '0.0.0.0:11434'`
   - El firewall permite conexiones en el puerto 11434
   - No hay otros servicios usando el puerto 11434

### 5. Modelos recomendados

- `ibm-granite/granite-3b-code-instruct-128k` - **Modelo REQUERIDO para chat** 
- `llama3.2` - Modelo general balanceado (alternativa)
- `llama3.2:1b` - Modelo más pequeño y rápido (alternativa)
- `codellama` - Especializado en código (alternativa)

```bash
# IMPORTANTE: Para la funcionalidad completa de la app, descargar:
ollama pull ibm-granite/granite-3b-code-instruct-128k

# Para descargar modelos alternativos:
ollama pull llama3.2:1b
ollama pull codellama
```

## ⚠️ SOLUCIÓN PARA ERRORES DE ARCHIVOS

### Si ves error: "StorageFileLoadException[connection_failure]"

Esto significa que el archivo está en Google Drive y no se puede acceder directamente.

#### Soluciones:

1. **Descargar archivo localmente**: Descarga el archivo de Google Drive a tu dispositivo
2. **Usar archivos locales**: Sube archivos directamente desde el almacenamiento local
3. **Copiar y pegar**: Copia el contenido del archivo y pégalo como texto en el chat

### Troubleshooting

#### Error de conexión:
1. Verificar que Ollama esté corriendo: `ollama list`
2. Verificar puerto: `netstat -an | findstr 11434`
3. Verificar logs en Android Studio Logcat buscando "AIAnalysisService"
4. **IMPORTANTE**: Usar `0.0.0.0:11434` no `localhost:11434`

#### Respuestas lentas:
- Usar modelos más pequeños como `llama3.2:1b`
- Verificar recursos de sistema (RAM/GPU)

#### Sin GPU:
```bash
# Forzar uso de CPU:
$env:OLLAMA_CPU_ONLY = 'true'
ollama serve
```

#### Verificación completa:
```bash
# Ejecutar estos comandos paso a paso:

# 1. Configurar variable de entorno
$env:OLLAMA_HOST = '0.0.0.0:11434'

# 2. Iniciar Ollama
ollama serve

# 3. En otra ventana, verificar modelos
ollama list

# 4. Verificar puerto abierto
netstat -an | findstr 11434

# 5. Probar API
curl http://localhost:11434/api/generate -d '{"model":"llama3.2","prompt":"test","stream":false}'
```
