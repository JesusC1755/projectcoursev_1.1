#!/bin/bash

# Script para verificar e instalar el modelo Granite en Ollama

echo "======================================================="
echo " VERIFICADOR DE MODELO GRANITE PARA OLLAMA"
echo "======================================================="
echo

# Verificar si Ollama está instalado
if ! command -v ollama &> /dev/null; then
    echo "[ERROR] Ollama no está instalado o no está en el PATH."
    echo "Por favor, instala Ollama desde: https://ollama.com/download"
    echo
    read -p "Presiona Enter para continuar..."
    exit 1
fi

echo "[INFO] Verificando si Ollama está ejecutándose..."

# Intentar conectar con Ollama
if ! curl -s -f http://localhost:11434/api/tags &> /dev/null; then
    echo "[ERROR] El servidor Ollama no está ejecutándose."
    echo "Por favor, inicia el servidor Ollama y vuelve a ejecutar este script."
    echo
    read -p "Presiona Enter para continuar..."
    exit 1
fi

echo "[OK] Ollama está ejecutándose."
echo
echo "[INFO] Verificando si el modelo Granite está instalado..."

# Verificar si el modelo está instalado
if ! curl -s http://localhost:11434/api/tags | grep -q "ibm-granite/granite-3b-code-instruct-128k"; then
    echo "[AVISO] El modelo IBM Granite no está instalado."
    echo
    
    read -p "¿Deseas instalar el modelo ahora? (s/N): " choice
    if [[ "$choice" =~ ^[Ss]$ ]]; then
        echo
        echo "[INFO] Instalando modelo IBM Granite. Esto puede tardar varios minutos..."
        echo
        
        if ! ollama pull ibm-granite/granite-3b-code-instruct-128k; then
            echo
            echo "[ERROR] Error al instalar el modelo. Por favor, intenta instalarlo manualmente:"
            echo "ollama pull ibm-granite/granite-3b-code-instruct-128k"
            echo
            echo "O descarga manualmente desde:"
            echo "https://huggingface.co/mradermacher/granite-3b-code-instruct-128k-GGUF/resolve/main/granite-3b-code-instruct-128k.Q4_K_M.gguf"
            echo
            read -p "Presiona Enter para continuar..."
            exit 1
        fi
        
        echo
        echo "[OK] Modelo IBM Granite instalado correctamente."
    else
        echo
        echo "[INFO] La aplicación NO funcionará correctamente sin el modelo IBM Granite."
        echo "Puedes instalarlo más tarde con el comando:"
        echo "ollama pull ibm-granite/granite-3b-code-instruct-128k"
    fi
else
    echo "[OK] El modelo IBM Granite está correctamente instalado."
fi

echo
echo "[INFO] Probando conexión con el modelo Granite..."
echo

# Realizar prueba de conexión
if curl -s -X POST http://localhost:11434/api/generate -d '{
  "model": "ibm-granite/granite-3b-code-instruct-128k",
  "prompt": "Responde solo con una palabra: Funciona",
  "stream": false
}' | grep -q "response"; then
    echo "[OK] Conexión exitosa con el modelo IBM Granite."
    echo "La aplicación debería funcionar correctamente ahora."
else
    echo "[ERROR] No se pudo obtener respuesta del modelo."
    echo "Verifica que el modelo esté correctamente instalado con:"
    echo "ollama list"
fi

echo
echo "======================================================="
echo
read -p "Presiona Enter para continuar..."
