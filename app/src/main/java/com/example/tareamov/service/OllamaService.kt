package com.example.tareamov.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import com.example.tareamov.data.AppDatabase

/**
 * Service to manage Ollama LLM initialization and connection
 */
class OllamaService : Service() {
    private val TAG = "OllamaService"
    private val isRunning = AtomicBoolean(false)
    private lateinit var mspClient: MSPClient
    private val maxRetries = 5 // Aumentamos el número de reintentos
    private val retryDelayMs = 0 // 3 segundos entre reintentos
    private lateinit var database: AppDatabase

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OllamaService created")
        mspClient = MSPClient(this) // Initialize with context
        database = AppDatabase.getDatabase(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OllamaService started")

        if (!isRunning.getAndSet(true)) {
            CoroutineScope(Dispatchers.IO).launch {
                // Try to start Ollama if it's not already running
                tryStartOllama()

                // Then initialize the connection
                initializeOllama()
            }
        }

        return START_STICKY
    }

    private suspend fun tryStartOllama() {
        try {
            // Check if Ollama is already running
            if (mspClient.isServerRunning()) {
                Log.d(TAG, "Ollama is already running")
                return
            }

            // On Android, we can't directly start Ollama on the host machine,
            // but we can log a message suggesting the user to start it
            Log.d(TAG, "Attempting to use local fallback since Ollama is not running")

            // Broadcast a notification that Ollama needs to be started
            val broadcastIntent = Intent("com.example.tareamov.OLLAMA_STATUS")
            broadcastIntent.putExtra("status", "not_running")
            broadcastIntent.putExtra("message", "Ollama no está ejecutándose. Usando modelo local como respaldo.")
            sendBroadcast(broadcastIntent)

            // Wait a bit to see if the user starts Ollama
            delay(0)
        } catch (e: Exception) {
            Log.e(TAG, "Error trying to start Ollama", e)
        }
    }

    private suspend fun initializeOllama() {
        // Try multiple times to connect to Ollama
        var retryCount = 0
        var connected = false

        while (retryCount < maxRetries && !connected) {
            // Check if the local Ollama server is accessible
            val apiStatus = mspClient.isServerRunning()
            Log.d(TAG, "Local Ollama status (attempt ${retryCount + 1}): ${if (apiStatus) "accessible" else "not accessible"}")

            if (apiStatus) {
                connected = true
                // If Ollama is accessible, perform a simple test query to load the model
                try {
                    Log.d(TAG, "Sending test prompt to initialize model...")

                    // Build enhanced context including Task and Subscription data
                    val enhancedContext = buildEnhancedDatabaseContext()

                    val testResponse = mspClient.sendPrompt(
                        "Eres un asistente de base de datos para una aplicación Android llamada TareaMov. " +
                                "Tienes acceso a la siguiente información de la base de datos:\n\n" +
                                enhancedContext + "\n\n" +
                                "Por favor responde con 'Modelo llama3 inicializado correctamente y listo para consultas de base de datos.'",
                        includeHistory = false,
                        includeDatabaseContext = false
                    )
                    Log.d(TAG, "Model initialization response: $testResponse")

                    // Broadcast that the model is ready
                    val broadcastIntent = Intent("com.example.tareamov.OLLAMA_READY")
                    broadcastIntent.putExtra("status", "ready")
                    broadcastIntent.putExtra("message", "Modelo Llama3 inicializado correctamente")
                    sendBroadcast(broadcastIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during model initialization", e)
                    connected = false
                    retryCount++
                }
            } else {
                // Wait before retrying
                Log.d(TAG, "Waiting before retry...")
                delay(retryDelayMs.toLong()) // Delay between retries
                retryCount++
            }
        }

        if (!connected) {
            Log.e(TAG, "Failed to connect to Ollama after $maxRetries attempts")
            // Broadcast that the model failed to initialize
            val broadcastIntent = Intent("com.example.tareamov.OLLAMA_READY")
            broadcastIntent.putExtra("status", "failed")
            broadcastIntent.putExtra("message", "No se pudo conectar al servidor Ollama después de $maxRetries intentos")
            sendBroadcast(broadcastIntent)
        }
    }

    /**
     * Build enhanced database context including Task and Subscription data
     */
    private suspend fun buildEnhancedDatabaseContext(): String {
        return try {
            // Use the MSPClient to get the context directly
            val mspClient = MSPClient(this@OllamaService)
            mspClient.buildDatabaseContext()
        } catch (e: Exception) {
            Log.e(TAG, "Error building enhanced database context", e)
            "Error al obtener contexto de base de datos: ${e.message}"
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "OllamaService destroyed")
        isRunning.set(false)
        super.onDestroy()
    }
}