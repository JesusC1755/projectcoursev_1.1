package com.example.tareamov.service

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Client for interacting with the Model Serving Platform (MSP)
 * This class handles communication with Ollama or other LLM services
 */
class MSPClient(private val context: Context) {
    private val tag = "MSPClient"
    // Lista de IPs posibles (ordenadas por prioridad)
    private val possibleBaseUrls = listOf(
        "http://192.168.1.158:11435",   // Current IP from ipconfig - Highest priority
        "http://localhost:11435",       // Localhost - High priority
        "http://127.0.0.1:11435",       // Loopback - High priority
        "http://0.0.0.0:11435",         // Bind address from Ollama logs
        "http://172.17.112.1:11435",    // WSL IP from ipconfig
        "http://192.168.1.254:11435"    // Gateway IP from ipconfig
    )
    private val emulatorUrl = "http://10.0.2.2:11435"
    private val modelName = "llama3"

    // Enhanced OkHttpClient with better timeout handling for large payloads
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // Increased read timeout for large responses
            .writeTimeout(60, TimeUnit.SECONDS)  // Increased write timeout for large requests
            .build()
    }

    private fun getBaseUrl(): String {
        if (isEmulator()) {
            Log.d(tag, "Using emulator URL: $emulatorUrl")
            return emulatorUrl
        }
        
        // Always try the current IP first (from ipconfig)
        if (isServerRunning("http://192.168.1.158:11435")) {
            Log.d(tag, "Connected to Ollama at current IP URL: http://192.168.1.158:11435")
            return "http://192.168.1.158:11435"
        }
        
        // Try each other URL in order of priority
        for (url in possibleBaseUrls.drop(1)) { // Skip the first one as we already tried it
            if (isServerRunning(url)) {
                Log.d(tag, "Connected to Ollama at URL: $url")
                return url
            }
        }
        
        // If none respond, use the current IP as fallback for future attempts
        Log.w(tag, "No Ollama server found, using current IP as fallback")
        return "http://192.168.1.158:11435"
    }

    // Improved server running check with better connection handling
    fun isServerRunning(urlToCheck: String? = null): Boolean {
        val url = urlToCheck ?: getBaseUrl()
        Log.d(tag, "Checking server status at: $url/api/tags")
        var connection: HttpURLConnection? = null
        
        return try {
            val urlObj = URL("$url/api/tags")
            connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000  // Increased timeout for more reliable checking
            connection.readTimeout = 5000     // Increased timeout for more reliable checking
            connection.connect()
            
            val responseCode = connection.responseCode
            Log.d(tag, "Server check response code: $responseCode for $url")
            val isOk = responseCode == HttpURLConnection.HTTP_OK
            
            if (isOk) {
                Log.i(tag, "Successfully connected to Ollama server at $url")
            } else {
                Log.w(tag, "Server at $url returned non-OK response: $responseCode")
            }
            
            isOk
        } catch (e: Exception) {
            Log.e(tag, "Failed to connect to Ollama server at $url: ${e.message}")
            false
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                Log.e(tag, "Error disconnecting from $url", e)
            }
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("sdk_gphone64_arm64")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator"))
    }

    suspend fun preloadLocalModel() = withContext(Dispatchers.IO) {
        try {
            val warmupPrompt = "Hola, responde con 'Modelo listo'."
            sendPromptInternal(warmupPrompt, isWarmup = true)
            Log.d(tag, "Local Llama 3 model preloaded/warmed up successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to preload/warmup local Llama 3 model", e)
        }
    }

    /**
     * Send a prompt to the LLM and get a response
     */
    // Only ONE definition of sendPrompt should exist:
    suspend fun sendPrompt(
        prompt: String,
        includeHistory: Boolean = false,
        includeDatabaseContext: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        // Calculate prompt size and log
        val promptSize = prompt.length * 2  // Rough estimation of bytes
        Log.d(tag, "Prompt size: approximately ${promptSize / 1024}KB")
        
        // Check if prompt is too large
        val maxAllowedSize = 8 * 1024 * 1024  // 8MB limit
        if (promptSize > maxAllowedSize) {
            Log.w(tag, "Prompt exceeds maximum allowed size. Truncating...")
            // Handle large prompt by using a summarized database context
            return@withContext sendPromptWithTruncation(prompt, includeHistory, includeDatabaseContext)
        }
        
        // For regular sized prompts, use the standard database context
        val dbContext = if (includeDatabaseContext) buildDatabaseContext() else ""
        val enhancedPrompt = if (includeDatabaseContext) {
            """
            Contexto de la Base de Datos (JSON):
            $dbContext
    
            Consulta del Usuario:
            $prompt
            """.trimIndent()
        } else {
            prompt
        }
    
        var currentBaseUrl = ""
        var response = ""
        var success = false
        var lastError: Exception? = null
    
        // Try each possible base URL until one works
        for (baseUrl in possibleBaseUrls) {
            currentBaseUrl = baseUrl
            try {
                Log.d(tag, "Trying to connect to $baseUrl...")
                
                // Construct the request URL
                val url = URL("$baseUrl/api/generate")
                val connection = url.openConnection() as HttpURLConnection
    
                // Set up the connection with increased timeouts
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 15000  // 15 seconds
                connection.readTimeout = 60000    // 60 seconds
                connection.doOutput = true
    
                // Create the request body
                val requestBody = JSONObject().apply {
                    put("model", modelName)
                    put("prompt", enhancedPrompt)
                    put("stream", false)
    
                    // Add options for context handling
                    val options = JSONObject().apply {
                        put("include_history", includeHistory)
                        put("include_database_context", false)  // We've already added it if needed
                    }
                    put("options", options)
                }
    
                try {
                    // Send the request
                    val outputStream = OutputStreamWriter(connection.outputStream)
                    outputStream.write(requestBody.toString())
                    outputStream.flush()
                    outputStream.close()
        
                    // Get the response
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val responseJson = JSONObject(reader.readText())
                        response = responseJson.getString("response")
                        success = true
                        connection.disconnect()
                        break
                    } else {
                        Log.e(tag, "Error response from $baseUrl: $responseCode")
                        // Try to read error message if available
                        try {
                            val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                            val errorResponse = errorReader.readText()
                            Log.e(tag, "Error details: $errorResponse")
                        } catch (e: Exception) {
                            Log.e(tag, "Could not read error details", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error sending request to $baseUrl", e)
                    lastError = e
                } finally {
                    connection.disconnect()
                }
            } catch (e: ConnectException) {
                Log.e(tag, "Failed to connect to $baseUrl", e)
                lastError = e
            } catch (e: Exception) {
                Log.e(tag, "Error sending prompt to $baseUrl", e)
                lastError = e
            }
        }
        
        if (!success) {
            Log.e(tag, "All base URLs failed", lastError)
            return@withContext "Error: No se pudo conectar al servidor LLM. ${lastError?.message ?: ""}"
        }
        
        return@withContext response
    }

    /**
     * Handles large prompts by truncating and summarizing
     */
    private suspend fun sendPromptWithTruncation(
        originalPrompt: String, 
        includeHistory: Boolean,
        includeDatabaseContext: Boolean
    ): String = withContext(Dispatchers.IO) {
        // Create a simplified database context
        val simplifiedContext = """
            La base de datos contiene tablas para usuarios, personas, videos, topics, tasks y subscriptions.
            El sistema es una plataforma educativa donde los usuarios pueden suscribirse a creadores
            y acceder a sus contenidos organizados en topics con tareas asociadas.
        """.trimIndent()
        
        // Create a shorter prompt
        val truncatedPrompt = """
            $simplifiedContext
            
            INSTRUCCIONES: Responde de manera concisa a la siguiente consulta basándote en tu conocimiento general
            sobre sistemas educativos y bases de datos con las tablas mencionadas.
            
            CONSULTA DEL USUARIO:
            ${originalPrompt.take(2000)}
        """.trimIndent()
        
        // Now send this truncated prompt
        var response = ""
        var success = false
        var lastError: Exception? = null
        
        // Try each possible base URL until one works
        for (baseUrl in possibleBaseUrls) {
            try {
                Log.d(tag, "Trying truncated prompt on $baseUrl...")
                val url = URL("$baseUrl/api/generate")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.doOutput = true
                
                val requestBody = JSONObject().apply {
                    put("model", modelName)
                    put("prompt", truncatedPrompt)
                    put("stream", false)
                    
                    val options = JSONObject().apply {
                        put("include_history", includeHistory)
                        put("include_database_context", false)
                    }
                    put("options", options)
                }
                
                try {
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(requestBody.toString())
                        writer.flush()
                    }
                    
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val responseJson = JSONObject(reader.readText())
                        response = responseJson.optString("response", "")
                        
                        // Add a note about truncation
                        response = """
                            [Nota: Debido al tamaño de la consulta, se utilizó una versión resumida del contexto]
                            
                            $response
                        """.trimIndent()
                        
                        success = true
                        break
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error in request to $baseUrl: ${e.message}")
                    lastError = e
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error sending truncated prompt to $baseUrl", e)
                lastError = e
            }
        }
        
        if (!success) {
            return@withContext "Error: La consulta es demasiado grande para procesar. Por favor, simplifica tu pregunta o especifica exactamente qué información necesitas. ${lastError?.message ?: ""}"
        }
        
        return@withContext response
    }

    suspend fun buildDatabaseContext(): String = withContext(Dispatchers.IO) {
        val db = com.example.tareamov.data.AppDatabase.getDatabase(context)
        val personaDao = db.personaDao()
        val usuarioDao = db.usuarioDao()
        val videoDao = db.videoDao()
        val topicDao = db.topicDao()
        val taskDao = db.taskDao()
        val subscriptionDao = db.subscriptionDao()
        val contentItemDao = db.contentItemDao()

        // Fetch data with limits to avoid memory issues
        val personas = personaDao.getAllPersonasList().take(100)
        val usuarios = usuarioDao.getAllUsuarios().take(100)
        val videos = videoDao.getAllVideos().take(100)
        val topics = topicDao.getAllTopics().take(100)
        val tasks = taskDao.getAllTasks().take(100)
        val subscriptions = subscriptionDao.getAllSubscriptions().take(100)
        val contentItems = contentItemDao.getAllContentItems().take(100)

        return@withContext buildString {
            // 1. Database Schema Overview
            appendLine("# ESQUEMA DE LA BASE DE DATOS")
            appendLine("## Tablas y Relaciones")
            appendLine("- Personas: Información personal de los usuarios")
            appendLine("- Usuarios: Credenciales y datos de autenticación")
            appendLine("- Topics: Categorías o temas de contenido")
            appendLine("- Tasks: Tareas asociadas a los topics")
            appendLine("- Videos: Contenido multimedia")
            appendLine("- Subscriptions: Relaciones de suscripción entre usuarios")
            appendLine("- ContentItems: Elementos de contenido adicionales")
            appendLine()
            appendLine("## Relaciones Principales")
            appendLine("- Usuario -> Persona (1:1)")
            appendLine("- Topic -> ContentItems (1:N)")
            appendLine("- Topic -> Tasks (1:N)")
            appendLine("- Subscription conecta Usuarios (suscriptor -> creador)")
            appendLine()
            appendLine("## Esquema Detallado")
            
            // 2. Table Schemas
            appendLine("### Personas")
            appendLine("- id: Identificador único")
            appendLine("- nombre: Nombre completo")
            appendLine("- email: Correo electrónico")
            appendLine("- telefono: Número de teléfono")
            appendLine()
            
            appendLine("### Usuarios")
            appendLine("- username: Nombre de usuario (único)")
            appendLine("- password: Contraseña (hash)")
            appendLine("- role: Rol del usuario (estudiante, profesor, admin)")
            appendLine()
            
            appendLine("### Topics")
            appendLine("- id: Identificador único")
            appendLine("- name: Nombre del tema")
            appendLine("- description: Descripción detallada")
            appendLine("- courseId: ID del curso relacionado")
            appendLine()
            
            appendLine("### Tasks")
            appendLine("- id: Identificador único")
            appendLine("- topicId: ID del tema relacionado")
            appendLine("- name: Nombre de la tarea")
            appendLine("- description: Descripción detallada")
            appendLine("- orderIndex: Orden de visualización")
            appendLine()
            
            appendLine("### Subscriptions")
            appendLine("- subscriberUsername: Usuario que se suscribe")
            appendLine("- creatorUsername: Creador al que se suscribe")
            appendLine("- subscriptionDate: Fecha de suscripción")
            
            // 3. Data Summary
            appendLine("\n## RESUMEN DE DATOS")
            appendLine("- Personas: ${personas.size} registros")
            appendLine("- Usuarios: ${usuarios.size} registros")
            appendLine("- Topics: ${topics.size} registros")
            appendLine("- Tasks: ${tasks.size} registros")
            appendLine("- Videos: ${videos.size} registros")
            appendLine("- Subscripciones: ${subscriptions.size} registros")
            appendLine("- Elementos de contenido: ${contentItems.size} registros")
            
            // 4. Recent/Important Data
            appendLine("\n## DATOS RECIENTES/IMPORTANTES")
            
            // Recent Topics
            if (topics.isNotEmpty()) {
                appendLine("\n### Últimos 3 Temas:")
                topics.take(3).forEach { topic ->
                    appendLine("- ${topic.name} (ID: ${topic.id}): ${topic.description.take(50)}...")
                }
            }
            
            // Recent Tasks
            if (tasks.isNotEmpty()) {
                appendLine("\n### Últimas Tareas:")
                tasks.take(5).forEach { task ->
                    appendLine("- ${task.name} (Tema ID: ${task.topicId}): ${task.description?.take(50) ?: "Sin descripción"}...")
                }
            }
            
            // Recent Subscriptions
            if (subscriptions.isNotEmpty()) {
                appendLine("\n### Suscripciones Recientes:")
                subscriptions.take(3).forEach { sub ->
                    appendLine("- ${sub.subscriberUsername} → ${sub.creatorUsername} (${sub.subscriptionDate})")
                }
            }
        }
    }
    suspend fun sendPromptWithDatabaseContext(prompt: String): String {
        val dbContext = buildDatabaseContext()
        val fullPrompt = """
            Contexto de la base de datos:
            $dbContext
    
            Pregunta del usuario:
            $prompt
        """.trimIndent()
        return sendPrompt(fullPrompt)
    }

    /**
     * Internal implementation of prompt sending with size validation
     * Used by preloadLocalModel for warmup and other internal operations
     */
    private suspend fun sendPromptInternal(prompt: String, isWarmup: Boolean = false): String = withContext(Dispatchers.IO) {
        // Don't log the full prompt if it's a warmup to avoid log spam
        if (isWarmup) {
            Log.d(tag, "Sending internal warmup prompt")
        } else {
            Log.d(tag, "Sending internal prompt: ${prompt.take(50)}...")
        }
        
        for (baseUrl in possibleBaseUrls) {
            try {
                Log.d(tag, "Trying internal prompt on $baseUrl...")
                val url = URL("$baseUrl/api/generate")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 5000   // Shorter timeout for warmup
                connection.readTimeout = 10000     // Shorter timeout for warmup
                connection.doOutput = true
                
                val requestBody = JSONObject().apply {
                    put("model", modelName)
                    put("prompt", prompt)
                    put("stream", false)
                }
                
                try {
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(requestBody.toString())
                        writer.flush()
                    }
                    
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val responseJson = JSONObject(reader.readText())
                        Log.d(tag, "Internal prompt successful on $baseUrl")
                        return@withContext responseJson.optString("response", "")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error in internal request to $baseUrl: ${e.message}")
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error sending internal prompt to $baseUrl: ${e.message}")
            }
        }
        
        return@withContext "Error: No se pudo conectar a ningún servidor disponible."
    }
}