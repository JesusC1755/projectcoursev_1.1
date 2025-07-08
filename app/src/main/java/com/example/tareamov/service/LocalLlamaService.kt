package com.example.tareamov.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servicio para ejecutar Llama 3 localmente en el dispositivo Android
 */
class LocalLlamaService(private val context: Context) {
    private val TAG = "LocalLlamaService"
    private val isModelLoaded = AtomicBoolean(false)
    private val modelFileName = "llama3-8b-q4_0.gguf"

    /**
     * Inicializa el modelo Llama 3
     */
    suspend fun initializeModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded.get()) return@withContext true

        try {
            // Verificar si el modelo existe en el almacenamiento interno
            val modelFile = File(context.filesDir, modelFileName)

            if (!modelFile.exists()) {
                Log.e(TAG, "Modelo no encontrado. Debe copiarse el archivo $modelFileName al directorio de la aplicación")
                return@withContext false
            }

            // Aquí iría la inicialización real del modelo con llama.cpp
            // Por ahora, simulamos que el modelo se cargó correctamente
            Log.d(TAG, "Simulando inicialización del modelo Llama 3")
            isModelLoaded.set(true)

            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar el modelo Llama 3", e)
            return@withContext false
        }
    }

    /**
     * Envía un prompt al modelo local y obtiene una respuesta
     */
    private var databaseContext: String = ""

    /**
     * Set the database context for better LLM responses
     */
    fun setDatabaseContext(context: String) {
        databaseContext = context
        Log.d(TAG, "Database context set for LocalLlamaService (${context.length} chars)")
    }

    /**
     * Generate a response using the local Llama model
     */
    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded.get()) {
            val initialized = initializeModel()
            if (!initialized) {
                return@withContext "Error: El modelo Llama 3 no está inicializado."
            }
        }

        try {
            val enhancedPrompt = if (databaseContext.isNotBlank()) {
                """
                Contexto de la Base de Datos:
                $databaseContext
                
                Esquema adicional:
                - Task: id (PK), topicId (FK a Topic.id), name, description, orderIndex
                - Subscription: subscriberUsername (PK), creatorUsername (PK), subscriptionDate
                
                Relaciones:
                - Task.topicId → Topic.id (Las tareas pertenecen a temas)
                - Subscription conecta usuarios (subscriberUsername y creatorUsername)
                
                Consulta del Usuario:
                $prompt
                """.trimIndent()
            } else {
                prompt
            }

            // Here would be the actual call to the Llama model
            // For now, we'll simulate a response
            Log.d(TAG, "Generating response for prompt: ${enhancedPrompt.take(100)}...")

            // Return a simulated response
            "Respuesta simulada de Llama 3 para la consulta: ${prompt.take(50)}..."
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Libera recursos del modelo
     */
    fun releaseModel() {
        if (isModelLoaded.get()) {
            try {
                // Aquí iría la liberación real de recursos
                isModelLoaded.set(false)
                Log.d(TAG, "Modelo Llama 3 liberado correctamente")
            } catch (e: Exception) {
                Log.e(TAG, "Error al liberar el modelo Llama 3", e)
            }
        }
    }

    /**
     * Worker para descargar el modelo en segundo plano
     */
    class ModelDownloadWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            // URL oficial del modelo GGUF (Q4_0) desde Hugging Face
            val modelUrl = "https://huggingface.co/QuantFactory/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct.Q4_0.gguf"
            val modelFile = File(applicationContext.filesDir, "llama3-8b-instruct-q4_0.gguf")
            val maxRetries = 3
            var attempt = 0
            while (attempt < maxRetries) {
                try {
                    val url = java.net.URL(modelUrl)
                    url.openStream().use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (modelFile.exists() && modelFile.length() > 0) {
                        return Result.success()
                    }
                } catch (e: Exception) {
                    Log.e("ModelDownloadWorker", "Error descargando el modelo (intento ${attempt + 1})", e)
                    if (modelFile.exists()) modelFile.delete()
                }
                attempt++
            }
            return Result.failure()
        }
    }

    /**
     * Inicia la descarga del modelo si no existe
     */
    fun downloadModelIfNeeded() {
        val modelFile = File(context.filesDir, modelFileName)
        if (!modelFile.exists()) {
            val downloadWorkRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .build()
            WorkManager.getInstance(context).enqueue(downloadWorkRequest)
        }
    }
}
