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

/**
 * Client for interacting with the Model Serving Platform (MSP)
 * This class handles communication with Ollama or other LLM services
 */
class MSPClient(private val context: Context) {
    private val tag = "MSPClient"
    // Lista de IPs posibles (vieja y nueva)
    private val possibleBaseUrls = listOf(
        "http://192.168.1.18:11434",   // IP vieja
        "http://192.168.1.158:11434",  // IP nueva
        "http://108.100.101.86:11434", // IP actual
        "http://192.168.71.181:11434", // IP nueva WiFi
        "http://108.100.108.200:11434", // IP nueva obtenida de ipconfig
        "http://192.168.81.181:11434",  // Nueva IP de ipconfig
        "http://108.100.105.159:11434", // Nueva IP WiFi de ipconfig
        "http://192.168.46.181:11434",  // Nueva IP WiFi de Windows
        "http://108.100.102.65:11434",  // Nueva IP de Windows (agregada)
        "http://192.168.10.95:11434",   // Nueva IP Wi-Fi
        "http://192.168.249.181:11434", // <-- Nueva IP agregada de ipconfig
        "http://172.20.10.4:11434",     // Otra IP agregada de ipconfig
        "http://192.168.1.17:11434",    // <-- NUEVA IP AGREGADA (Wi-Fi Windows)
        "http://192.168.1.7:11434",      // <-- NUEVA IP AGREGADA (Wi-Fi actual)
        "http://192.168.1.15:11434"
    )
    private val emulatorUrl = "http://10.0.2.2:11434"
    private val modelName = "llama3"

    private fun getBaseUrl(): String {
        if (isEmulator()) {
            Log.d(tag, "Using emulator URL: $emulatorUrl")
            return emulatorUrl
        }
        // Probar cada IP hasta que una responda
        for (url in possibleBaseUrls) {
            if (isServerRunning(url)) {
                Log.d(tag, "Using available base URL: $url")
                return url
            }
        }
        // Si ninguna responde, usar la primera por defecto
        Log.w(tag, "No Ollama server available, using default: ${possibleBaseUrls.first()}")
        return possibleBaseUrls.first()
    }

    // Cambia isServerRunning para aceptar un parámetro opcional de URL
    fun isServerRunning(urlToCheck: String? = null): Boolean {
        val url = urlToCheck ?: getBaseUrl()
        Log.d(tag, "Checking server status at: $url/api/tags")
        return try {
            val urlObj = URL("$url/api/tags")
            val connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.connect()
            val responseCode = connection.responseCode
            Log.d(tag, "Server check response code: $responseCode")
            connection.disconnect()
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            Log.e(tag, "No se pudo conectar al servidor Ollama en $url. Detalle: ${e.message}")
            false
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

    fun isServerRunning(): Boolean {
        val urlToCheck = getBaseUrl()
        Log.d(tag, "Checking server status at: $urlToCheck/api/tags")
        return try {
            val url = URL("$urlToCheck/api/tags")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.connect()
            val responseCode = connection.responseCode
            Log.d(tag, "Server check response code: $responseCode")
            connection.disconnect()
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            Log.e(tag, "No se pudo conectar al servidor Ollama en $urlToCheck. Se usará el modelo local. Detalle: ${e.message}")
            false
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
        // Use the local database context builder instead of fetchDatabaseContextFromServer()
        val dbContext = buildDatabaseContext()
        val enhancedPrompt = """
            Contexto de la Base de Datos (JSON):
            $dbContext
    
            Consulta del Usuario:
            $prompt
        """.trimIndent()
    
        var currentBaseUrl = ""
        var response = ""
        var success = false
        var lastError: Exception? = null
    
        // Try each possible base URL until one works
        for (baseUrl in possibleBaseUrls) {
            currentBaseUrl = baseUrl
            try {
                // Construct the request URL
                val url = URL("$baseUrl/api/generate")
                val connection = url.openConnection() as HttpURLConnection
    
                // Set up the connection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
    
                // Create the request body
                val requestBody = JSONObject().apply {
                    put("model", modelName)
                    put("prompt", enhancedPrompt) // Use the enhanced prompt here
                    put("stream", false)
    
                    // Add options for context handling
                    val options = JSONObject().apply {
                        put("include_history", includeHistory)
                        put("include_database_context", includeDatabaseContext)
                    }
                    put("options", options)
                }
    
                // Send the request
                val outputStream = OutputStreamWriter(connection.outputStream)
                outputStream.write(requestBody.toString())
                outputStream.flush()
    
                // Get the response
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseJson = JSONObject(reader.readText())
                    response = responseJson.getString("response")
                    success = true
                    break
                } else {
                    Log.e(tag, "Error response from $baseUrl: $responseCode")
                }
    
                connection.disconnect()
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
    // Only ONE definition of sendPromptInternal should exist:
    private suspend fun sendPromptInternal(prompt: String, isWarmup: Boolean = false): String = withContext(Dispatchers.IO) {
        if (isServerRunning()) {
            try {
                Log.d(tag, "Attempting to send prompt to Ollama server")
                val ollamaResponse = sendToOllama(prompt)
                if (ollamaResponse.isNotBlank() && !ollamaResponse.contains("error", ignoreCase = true)) {
                    Log.i(tag, "Received valid response from Ollama")
                    return@withContext ollamaResponse
                }
                Log.w(tag, "Ollama response was blank or contained 'error'. Response: $ollamaResponse")
            } catch (e: Exception) {
                Log.e(tag, "Error enviando prompt a Ollama. Se intentará con el modelo local. Detalle: ${e.message}")
            }
        }
        if (!isWarmup) {
            Log.w(tag, "Servidor Ollama no disponible. Usando modelo local.")
            try {
                val localLlamaService = LocalLlamaService(context)
                val localResponse = localLlamaService.generateResponse(prompt)
                Log.i(tag, "Respuesta obtenida del modelo local.")
                return@withContext localResponse
            } catch (e: Exception) {
                Log.e(tag, "Error usando el modelo local: ${e.message}")
                return@withContext "Error al procesar la consulta con el servicio local: ${e.message}"
            }
        } else {
            Log.w(tag, "Ollama server not running during warmup.")
            return@withContext "Ollama server not available for warmup."
        }
        Log.e(tag, "No se pudo obtener respuesta ni de Ollama ni del modelo local.")
        return@withContext "No se pudo conectar al servidor de Ollama ni al servicio local de Llama. Verifica las conexiones."
    }

    private suspend fun sendToOllama(prompt: String): String = withContext(Dispatchers.IO) {
        val url = URL("${getBaseUrl()}/api/generate")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 60000

            val jsonRequest = JSONObject().apply {
                put("model", modelName)
                put("prompt", prompt)
                put("stream", false)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonRequest.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(tag, "Ollama API response code: $responseCode")

            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                response.toString()
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(responseText)
                return@withContext jsonResponse.optString("response", "")
            } else {
                Log.e(tag, "Ollama API Error ($responseCode): $responseText")
                val errorMsg = try { JSONObject(responseText).optString("error", "Error desconocido del servidor") } catch (e: Exception) { responseText }
                return@withContext "Error del servidor Ollama ($responseCode): $errorMsg"
            }
        } finally {
            connection.disconnect()
        }
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
}