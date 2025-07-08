#!/bin/bash
# TEST_OLLAMA_CONNECTION.sh
# Script para probar la conexión con Ollama y los modelos requeridos
# -----------------------------------------------------------------

# Colores para salida
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== TEST DE CONEXIÓN CON OLLAMA ===${NC}"
echo "Verificando configuración para la app de Android"
echo ""

# Comprobar si Ollama está instalado
if command -v ollama &> /dev/null; then
    echo -e "${GREEN}✅ Ollama está instalado${NC}"
else
    echo -e "${RED}❌ Ollama no está instalado${NC}"
    echo "Por favor, instala Ollama desde https://ollama.com/"
    exit 1
fi

# Comprobar si el servicio Ollama está ejecutándose
curl -s http://localhost:11434/api/tags > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Servicio Ollama está ejecutándose${NC}"
else
    echo -e "${RED}❌ Servicio Ollama no está ejecutándose${NC}"
    echo "Ejecuta el siguiente comando en una terminal:"
    echo "ollama serve"
    exit 1
fi

# Comprobar si el host está configurado correctamente
echo -e "${YELLOW}\nVerificando configuración de host...${NC}"
curl -s http://0.0.0.0:11434/api/tags > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Ollama está configurado para aceptar conexiones externas (0.0.0.0)${NC}"
else
    echo -e "${YELLOW}⚠️ Ollama podría no estar escuchando en 0.0.0.0${NC}"
    echo "Para aceptar conexiones desde Android, ejecuta:"
    echo "OLLAMA_HOST=0.0.0.0:11434 ollama serve"
fi

# Listar modelos disponibles
echo -e "${YELLOW}\nModelos disponibles:${NC}"
MODELS=$(curl -s http://localhost:11434/api/tags)
echo $MODELS | grep -o '"name":"[^"]*"' | sed 's/"name":"//g' | sed 's/"//g'

# Verificar si el modelo requerido está disponible
echo -e "${YELLOW}\nVerificando modelo principal (ibm-granite/granite-3b-code-instruct-128k)...${NC}"
if echo $MODELS | grep -q "ibm-granite/granite-3b-code-instruct-128k"; then
    echo -e "${GREEN}✅ Modelo ibm-granite/granite-3b-code-instruct-128k está instalado${NC}"
else
    echo -e "${RED}❌ Modelo ibm-granite/granite-3b-code-instruct-128k NO está instalado${NC}"
    echo "Este modelo es requerido para la funcionalidad completa del chat."
    echo "Para instalarlo, ejecuta:"
    echo "ollama pull ibm-granite/granite-3b-code-instruct-128k"
fi

# Verificar modelos alternativos
echo -e "${YELLOW}\nVerificando modelos alternativos...${NC}"
if echo $MODELS | grep -q "llama3"; then
    echo -e "${GREEN}✅ Modelo llama3 está instalado${NC}"
else
    echo -e "${YELLOW}⚠️ Modelo llama3 no está instalado (alternativa de respaldo)${NC}"
    echo "Para instalarlo, ejecuta: ollama pull llama3"
fi

# Probar conexión API básica
echo -e "${YELLOW}\nProbando API de Ollama...${NC}"
API_TEST=$(curl -s -X POST http://localhost:11434/api/generate -d '{"model":"llama3","prompt":"test","stream":false,"options":{"max_tokens":10}}' 2>/dev/null)

if [ $? -eq 0 ] && [ ! -z "$API_TEST" ]; then
    echo -e "${GREEN}✅ API de Ollama responde correctamente${NC}"
else
    echo -e "${RED}❌ Error al comunicarse con la API de Ollama${NC}"
    echo "Verifica que el servicio esté funcionando y el firewall permita conexiones"
fi

# Prueba de conexión desde la red local
echo -e "${YELLOW}\nPrueba de acceso desde la red:${NC}"
IP_ADDR=$(hostname -I | awk '{print $1}')
echo "Tu dirección IP local es: $IP_ADDR"
echo "Para probar manualmente desde otro dispositivo:"
echo "curl http://$IP_ADDR:11434/api/generate -d '{\"model\":\"llama3\",\"prompt\":\"test\",\"stream\":false}'"

# Resumen final
echo -e "${YELLOW}\n=== RESUMEN ===${NC}"
echo "Para que la app Android funcione correctamente:"
echo "1. Ollama debe estar ejecutándose con: OLLAMA_HOST=0.0.0.0:11434"
echo "2. El modelo ibm-granite/granite-3b-code-instruct-128k debe estar instalado"
echo "3. El puerto 11434 debe estar accesible desde tu dispositivo Android"
echo ""
echo -e "${YELLOW}Si la app no se conecta, revisa OLLAMA_SETUP.md para soluciones${NC}"
