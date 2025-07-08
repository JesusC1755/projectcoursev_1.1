# Configuración de Modelo Granite para Ollama

## Requisito obligatorio: Modelo IBM Granite

Esta aplicación **REQUIERE** el modelo IBM Granite para funcionar correctamente.

```
ibm-granite/granite-3b-code-instruct-128k
```

## Instrucciones de instalación

1. Asegúrate de tener Ollama instalado y ejecutándose en tu ordenador.
   - Descargar desde: [https://ollama.com/download](https://ollama.com/download)

2. Abre una terminal y ejecuta el siguiente comando:

```bash
ollama pull ibm-granite/granite-3b-code-instruct-128k
```

3. Espera a que se complete la descarga (puede tardar varios minutos dependiendo de tu conexión a internet).

4. Verifica que el modelo esté correctamente instalado:

```bash
ollama list
```

Deberías ver "ibm-granite/granite-3b-code-instruct-128k" en la lista de modelos.

## Solución de problemas

Si ves un error como este:
```
{"error":"model 'ibm-granite/granite-3b-code-instruct-128k' not found"}
```

Significa que el modelo no está instalado en tu servidor Ollama.

### ¿Por qué este modelo específicamente?

El modelo IBM Granite ha sido seleccionado por su rendimiento superior para análisis de código y respuestas técnicas. La aplicación está optimizada para usar este modelo exclusivamente y no funcionará correctamente con otros modelos.

### Descarga manual (alternativa)

Si tienes problemas con el comando `ollama pull`, puedes descargar el modelo manualmente desde:

[https://huggingface.co/mradermacher/granite-3b-code-instruct-128k-GGUF/resolve/main/granite-3b-code-instruct-128k.Q4_K_M.gguf](https://huggingface.co/mradermacher/granite-3b-code-instruct-128k-GGUF/resolve/main/granite-3b-code-instruct-128k.Q4_K_M.gguf)

Y luego importarlo con:

```bash
ollama create ibm-granite/granite-3b-code-instruct-128k -f ./granite-3b-code-instruct-128k.Q4_K_M.gguf
```

### Verificación de la conexión

Para verificar que tu instalación está correcta:

1. Asegúrate que el servidor Ollama esté ejecutándose
2. Abre la aplicación
3. El chat debería conectarse automáticamente al modelo IBM Granite
