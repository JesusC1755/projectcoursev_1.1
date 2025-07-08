# Convertidor de Archivos para Análisis con IA

Este documento explica el sistema mejorado de conversión de archivos para análisis con IA integrado en la aplicación.

## Características principales

- **Soporte para múltiples formatos de archivo**:
  - Documentos de texto (TXT, MD)
  - Documentos de Microsoft Office (DOC, DOCX, XLS, XLSX, PPT, PPTX)
  - PDF
  - Archivos de código fuente (Java, Kotlin, Python, etc.)
  - Archivos JSON, XML, HTML
  - Archivos comprimidos (ZIP)
  - Imágenes, audio y video (metadatos)

- **Procesamiento inteligente**:
  - Extracción de texto con preservación de estructura
  - Detección de secciones y títulos
  - Conservación de metadatos relevantes
  - Organización jerárquica del contenido

- **Integración con modelos de IA**:
  - Formato optimizado para modelos como Granite
  - Estructuración automática para mejores respuestas
  - Compatible con distintos tipos de consultas
  - Respuestas contextualizadas y no solo la estructura JSON

- **Acceso a archivos**:
  - Soporte para almacenamiento local
  - Compatibilidad con Google Drive (requiere autenticación)
  - Manejo de errores robusto

## Cómo funciona

1. El usuario selecciona un archivo para analizar
2. El `FileConverterService` detecta el tipo de archivo basado en su extensión
3. Se extrae el contenido del archivo usando la biblioteca adecuada:
   - Apache PDFBox para archivos PDF
   - Apache POI para documentos de Office (Word, Excel, PowerPoint)
4. El contenido se convierte a formato JSON estructurado con secciones, metadatos y jerarquía
5. El JSON se envía al modelo de IA a través del protocolo MCP
6. El usuario puede hacer preguntas generales sobre el contenido del archivo
7. El modelo responde en lenguaje natural, no solo con la estructura JSON

## Mejoras en la nueva versión

- **Estructura de datos mejorada**: El contenido ahora se organiza en secciones con títulos y niveles jerárquicos
- **Análisis más profundo**: Extracción de metadatos específicos según el tipo de archivo
- **Integración con AIAnalysisService**: Nuevo método `analyzeDocumentWithAI` que permite preguntas generales
- **Preguntas ilimitadas**: El usuario puede hacer cualquier tipo de pregunta sobre el contenido del archivo
- **Respuestas en lenguaje natural**: El modelo responde directamente con información relevante, no con JSON

## Formato de salida

El archivo convertido tendrá una estructura similar a esta:

```json
{
  "fileName": "ejemplo.pdf",
  "fileType": "pdf",
  "metadata": {
    "pageCount": 5,
    "author": "Autor del documento",
    "title": "Título del documento"
  },
  "sections": [
    {
      "title": "Página 1",
      "content": "Contenido de la página 1...",
      "type": "page"
    },
    {
      "title": "Página 2",
      "content": "Contenido de la página 2...",
      "type": "page"
    }
  ],
  "fullContent": "Texto completo del documento..."
}
```

## Requisitos técnicos

- **Bibliotecas**:
  - Apache PDFBox para archivos PDF
  - Apache POI para documentos de Office (Word, Excel, PowerPoint)
  - OkHttp y Retrofit para comunicación con servicios
  - Gson para procesamiento JSON

- **Permisos**:
  - Acceso a almacenamiento externo
  - Acceso a Internet (para Google Drive)

## Ejemplos de preguntas posibles

Con esta nueva implementación, los usuarios pueden hacer preguntas como:

- "¿Cuáles son los puntos principales de este documento?"
- "Explica la tabla que aparece en la página 3"
- "¿Cuáles son las conclusiones de esta presentación?"
- "Resúmeme este archivo en 5 puntos clave"
- "¿Qué valores aparecen en la columna B de la hoja de cálculo?"
- "¿Quién es el autor de este documento y cuándo fue creado?"
- "Explica los conceptos clave mencionados en la sección 2"

## Limitaciones actuales

- El tamaño máximo recomendado de archivo es de 10MB
- Los archivos con contenido principalmente visual tendrán conversión limitada
- Los archivos protegidos con contraseña no pueden ser procesados
- Para archivos de Google Drive, es posible que se necesite descargarlos localmente primero

## Componentes principales

### 1. FileConverterService

Servicio principal que maneja la conversión de archivos. Utiliza diferentes métodos de extracción según el tipo de archivo.

### 2. AIAnalysisService

Servicio que conecta con el modelo de IA (Ollama) para analizar el contenido del archivo convertido.

### 3. MCPService

Servicio que proporciona acceso a archivos y coordina el proceso de análisis.

## Ejemplos de uso

### Analizar un documento Word

```kotlin
// En un fragmento o actividad
val fileUri = Uri.parse("content://...")
val fileName = "documento.docx"

// Usar MCPService para procesar el archivo
val fileContext = mcpService.processFileLocally(fileUri, fileName)

// Analizar con IA
val aiService = AIAnalysisService(context)
val question = "Analiza este documento"
val result = aiService.analyzeFileWithAI(fileContext, question)

// Mostrar resultado
showAnalysisResult(result.analysis)
```

### Análisis de código fuente

```kotlin
// Analizar un archivo de código
val fileUri = Uri.parse("content://...")
val fileName = "MiClase.java"

// Procesar y analizar
val fileContext = mcpService.processFileLocally(fileUri, fileName)
val question = "Explica este código y sugiere mejoras"
val result = aiAnalysisService.analyzeFileWithAI(fileContext, question)
```

## Bibliotecas utilizadas

- Apache POI: Para documentos de Microsoft Office
- PDFBox: Para archivos PDF
- iText: Procesamiento avanzado de PDF
- Gson: Conversión a JSON

## Limitaciones

- Archivos muy grandes pueden ser truncados para evitar problemas de memoria
- Algunos formatos complejos pueden no extraerse perfectamente
- Los archivos de Google Drive deben descargarse primero
- El análisis depende de la calidad del modelo de IA utilizado

## Información para desarrolladores

Las clases principales que implementan esta funcionalidad son:

- `FileConverterService.kt`: Servicio principal de conversión
- `AIAnalysisService.kt`: Análisis con IA
- `MCPService.kt`: Coordinación y procesamiento
- `FileAnalysisService.kt`: Análisis de archivos específicos

La implementación permite fácilmente añadir soporte para nuevos tipos de archivo en el futuro.
