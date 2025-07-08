# Analizador de Archivos para Submisiones

Este componente permite convertir cualquier tipo de archivo subido por el usuario a formato JSON para ser analizado por un modelo de IA a través del servicio MSP.

## Características

- Soporte para múltiples formatos de archivo:
  - PDF (usando Apache PDFBox)
  - Microsoft Word (DOC, DOCX) usando Apache POI
  - Microsoft Excel (XLS, XLSX) usando Apache POI
  - Microsoft PowerPoint (PPT, PPTX) usando Apache POI
  - Archivos de texto (TXT, CSV, XML, JSON)
  - Otros formatos mediante Apache Tika

- Extracción de metadatos de archivos
- Detección automática del tipo de contenido
- Limitación de tamaño para evitar problemas de memoria
- Integración con el modelo de IA a través de MSPClient

## Cómo utilizar

### Ejemplo básico

```kotlin
// En una corrutina o AsyncTask
val submissionService = SubmissionAnalysisService(context)

// Analizar una submisión existente
val analysisResult = submissionService.analyzeSubmission(
    submissionId = 123, 
    question = "Revisa este archivo y proporciona retroalimentación"
)

// O para guardar y analizar un nuevo archivo
val fileUri = Uri.parse("content://path/to/file")
val submissionId = submissionService.saveAndAnalyzeSubmission(
    taskId = taskId,
    studentUsername = "estudiante123",
    fileUri = fileUri,
    fileName = "tarea_final.docx"
)
```

### Analizar directamente un archivo

```kotlin
// Desde TaskSubmission
val analysisResult = TaskSubmission.analyzeSubmissionFile(
    context = context,
    fileUri = fileUri,
    fileName = "informe.pdf",
    question = "Evalúa si este informe cumple con los criterios de la tarea"
)
```

### Procesar múltiples submisiones pendientes

```kotlin
val submissionService = SubmissionAnalysisService(context)

// Analizar todas las submisiones pendientes
val processed = submissionService.analyzeUnprocessedSubmissions()
Log.d("Análisis", "Se analizaron $processed submisiones pendientes")

// Re-analizar submisiones con análisis antiguo (por defecto más de 7 días)
val reanalyzed = submissionService.reanalyzeOutdatedSubmissions()
Log.d("Análisis", "Se re-analizaron $reanalyzed submisiones")
```

## Consideraciones

- El análisis de archivos grandes puede consumir mucha memoria
- Se recomienda ejecutar el análisis en un servicio en segundo plano
- El modelo de IA debe tener capacidad para procesar el tipo de archivo enviado
- Los tiempos de respuesta dependen del tamaño del archivo y la complejidad del análisis
