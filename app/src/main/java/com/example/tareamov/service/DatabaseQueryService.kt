package com.example.tareamov.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import com.example.tareamov.data.AppDatabase
import org.json.JSONObject
import org.json.JSONArray
import com.google.gson.Gson // <-- Add this import

/**
 * Service that handles natural language queries to the database
 */
class DatabaseQueryService(private val context: Context) {
    private val tag = "DatabaseQueryService"
    // Use dependency injection or a singleton for MSPClient and LocalLlamaService if appropriate
    private val mspClient by lazy { MSPClient(context) } // Lazy initialization
    private val localLlamaService by lazy { LocalLlamaService(context) } // Lazy initialization
    private val database = AppDatabase.getDatabase(context)

    // Add schema definitions for Task and Subscription
    private val taskSchema = """
        {
            "tableName": "tasks",
            "columns": {
                "id": "INTEGER PRIMARY KEY AUTOINCREMENT",
                "topicId": "INTEGER NOT NULL (Foreign Key to topics.id)",
                "name": "TEXT NOT NULL",
                "description": "TEXT",
                "orderIndex": "INTEGER NOT NULL",
                "completed": "INTEGER NOT NULL DEFAULT 0"
            },
            "relationships": "Each Task belongs to a Topic (tasks.topicId -> topics.id)"
        }
    """.trimIndent()

    private val subscriptionSchema = """
        {
            "tableName": "subscriptions",
            "columns": {
                "subscriberUsername": "TEXT NOT NULL (Part of Primary Key)",
                "creatorUsername": "TEXT NOT NULL (Part of Primary Key)",
                "subscriptionDate": "LONG NOT NULL (timestamp)"
            },
            "relationships": "Connects a subscriber user to a creator user"
        }
    """.trimIndent()

    private val databaseJson = Mutex()
    @Volatile
    private var currentDatabaseJson: String = "{}"

    init {
        // Inicializa el JSON al arrancar
        updateDatabaseJson()
    }

    public fun updateDatabaseJson() {
        // Llama a la función de actualización en un hilo de fondo
        GlobalScope.launch(Dispatchers.IO) {
            val json = generateDatabaseJson()
            databaseJson.lock()
            try {
                currentDatabaseJson = json
            } finally {
                databaseJson.unlock()
            }
        }
    }

    suspend fun getDatabaseJson(): String {
        databaseJson.lock()
        try {
            return currentDatabaseJson
        } finally {
            databaseJson.unlock()
        }
    }

    /**
     * Process a natural language query and return a response
     */
    suspend fun processQuery(query: String): String = withContext(Dispatchers.IO) {
        val normalizedQuery = query.lowercase().trim()
        Log.i(tag, "Processing query: '$normalizedQuery'")

        try {
            // Always generate the latest database JSON directly from DAOs
            val dbJson = generateDatabaseJson() // <-- Always fresh data!
            // Pass the JSON context to the LLM
            return@withContext processNaturalLanguageQueryWithJson(normalizedQuery, dbJson)
        } catch (e: Exception) {
            Log.e(tag, "Error processing query '$query'", e)
            val fallbackResult = handleBasicDatabaseQuery(normalizedQuery)
            return@withContext "Error al procesar la consulta: ${e.message}. Intentando fallback básico...\n$fallbackResult"
        }
    }

    // New function to always include JSON context
    private suspend fun processNaturalLanguageQueryWithJson(query: String, dbJson: String): String {
        val prompt = """
            Eres un asistente experto en bases de datos de una plataforma educativa.
            Aquí tienes el contexto completo de la base de datos en formato JSON:
            $dbJson

            Este JSON contiene información completa de todas las tablas de la base de datos:
            
            TABLAS PRINCIPALES:
            - usuarios: Todos los usuarios registrados en el sistema con sus credenciales
            - personas: Información personal detallada de los usuarios (nombres, apellidos, email, etc.)
            - videos: Cursos disponibles en la plataforma con sus metadatos
            - topics: Temas o módulos asociados a cada curso
            - contentItems: Elementos de contenido (documentos, videos, enlaces) asociados a cada tema
            - tasks: Tareas o actividades asignadas a los temas del curso
            - subscriptions: Relaciones de suscripción entre estudiantes y creadores de contenido
            - taskSubmissions: Entregas de tareas realizadas por los estudiantes con sus calificaciones
            - purchases: Registro de compras de cursos realizadas por los usuarios
            
            RELACIONES CLAVE:
            1. Un 'Usuario' está vinculado a una 'Persona' por 'persona_id'.
            2. Un 'Video' (Curso) puede tener múltiples 'Topics' asociados por 'courseId'.
            3. Un 'Topic' puede tener múltiples 'ContentItems' asociados por 'topicId'.
            4. Un 'Topic' puede tener múltiples 'Tasks' asociadas por 'topicId'.
            5. Una 'TaskSubmission' está vinculada a una 'Task' por 'taskId' y a un estudiante por 'studentUsername'.
            6. Una 'Subscription' conecta un 'subscriberUsername' con un 'creatorUsername'.
            7. Una 'Purchase' registra la compra de un curso (video) por un usuario.
            
            INSTRUCCIONES:
            - Usa ÚNICAMENTE la información proporcionada en el JSON para responder.
            - Si la información solicitada no está disponible en el JSON, indícalo claramente.
            - Para consultas sobre contenido de cursos, verifica tanto en 'videos' como en 'topics' y 'contentItems'.
            - Para consultas sobre tareas, verifica en 'tasks' y 'taskSubmissions'.
            - Para consultas sobre usuarios, verifica tanto en 'usuarios' como en 'personas'.
            
            Consulta del usuario:
            "$query"
            
            Por favor, proporciona una respuesta clara y completa basada en los datos disponibles.
        """.trimIndent()

        // Try MSPClient first, then LocalLlamaService, then fallback
        try {
            val response = mspClient.sendPrompt(prompt)
            if (response.isNotBlank() && !response.contains("Error:", ignoreCase = true)) {
                return response
            }
        } catch (_: Exception) {}
        try {
            val response = localLlamaService.generateResponse(prompt)
            if (response.isNotBlank() && !response.contains("Error:", ignoreCase = true)) {
                return response
            }
        } catch (_: Exception) {}
        return handleBasicDatabaseQuery(query)
    }

    /**
     * Checks if the query is a request for a graph and returns the request type or null.
     */
    private fun checkForGraphRequest(query: String): String? {
        // More robust graph detection
        val isGraphQuery = query.contains("gráfico") || query.contains("grafico") ||
                query.contains("visualiza") || query.contains("muestra") && (query.contains("gráficamente") || query.contains("visual"))

        if (!isGraphQuery) return null

        Log.d(tag, "Potential graph query detected: $query")

        // Specific graph types
        return when {
            query.contains("usuario") && query.contains("video") ||
                    query.contains("usuarios") && query.contains("videos") -> "GRAPH_REQUEST:USER_VIDEOS"

            query.contains("tema") && query.contains("contenido") ||
                    query.contains("temas") && query.contains("contenidos") -> "GRAPH_REQUEST:TOPIC_CONTENT"

            query.contains("curso") && query.contains("tema") ||
                    query.contains("video") && query.contains("tema") ||
                    query.contains("videos") && query.contains("temas") -> "GRAPH_REQUEST:COURSE_TOPICS"

            query.contains("persona") && query.contains("usuario") ||
                    query.contains("personas") && query.contains("usuarios") -> "GRAPH_REQUEST:PERSONAS_USERS"

            // Add more specific graph types here if needed

            // Generic interactive graph request
            query.contains("interactivo") || query.contains("dinámico") -> "GRAPH_REQUEST:INTERACTIVE"

            // Fallback for unrecognized graph queries
            else -> {
                Log.d(tag, "Unrecognized graph type for query: $query")
                // Return null to let it be processed by LLM or basic fallback
                null
                // Or return a specific message:
                // "No se pudo identificar qué tipo de gráfico deseas. Prueba con:\n" +
                // "- Gráfico de usuarios por videos\n" +
                // "- Gráfico de temas por contenido\n" +
                // "- Gráfico de cursos por temas\n" +
                // "- Gráfico de personas y usuarios"
            }
        }
    }

    /**
     * Process a natural language query using the LLM, with fallback to local LLM and basic queries.
     */
    private suspend fun processNaturalLanguageQuery(query: String): String {
        val dbJson = getDatabaseJson()
        val jsonObj = JSONObject(dbJson)
        val tableNames = jsonObj.keys().asSequence()
            .filter { it != "schema" }
            .toList()
        val tableList = tableNames.mapIndexed { idx, name -> "${idx + 1}. `${name}`" }.joinToString("\n")
        val contextMessage = """
            Tengo contexto completo sobre las siguientes tablas de la base de datos:
            $tableList

            Puedes preguntarme cualquier cosa sobre estas tablas.
        """.trimIndent()
        Log.d(tag, "Attempting natural language query processing for: $query")

        return try {
            // Prepare database context for better LLM responses
            val databaseContext = getDatabaseContext()
            Log.v(tag, "Database Context for LLM:\n$databaseContext")

            // Construct a more robust prompt
            val systemPrompt = "Eres un asistente experto en bases de datos SQL. Tu tarea es responder preguntas sobre una base de datos específica basándote únicamente en el esquema y los datos proporcionados. Sé conciso y preciso. Si la pregunta es ambigua, pide aclaraciones. Si la información no está disponible, indícalo claramente. No inventes información."
            val fullPrompt = """
            $systemPrompt

            $contextMessage

            Contexto de la Base de Datos:
            $databaseContext

{{ edit_2 }}

            La tabla 'usuarios' contiene la lista completa de todos los usuarios registrados en el sistema.
            La tabla 'subscriptions' contiene información sobre las relaciones de suscripción entre usuarios, no el total de usuarios.

            Consulta del Usuario: $query
            Responde usando únicamente la información del JSON. Si la información no está disponible, indícalo claramente.
            """.trimIndent()

            // 1. Try MSPClient (Ollama)
            try {
                Log.d(tag, "Attempting query with MSPClient (Ollama)")
                val response = mspClient.sendPrompt(fullPrompt, includeHistory = true, includeDatabaseContext = false) // Context is already in the prompt
                Log.i(tag, "Response from MSPClient: $response")
                // Basic validation of response
                if (response.isNotBlank() && !response.contains("Error:", ignoreCase = true) && !response.contains("no se pudo conectar", ignoreCase = true)) {
                    return response
                }
                Log.w(tag, "MSPClient response was empty or indicated an error.")
                // Fall through to the next attempt if response is invalid
            } catch (e: Exception) {
                Log.e(tag, "Error querying MSPClient (Ollama). Trying LocalLlamaService.", e)
                // Fall through to the next attempt
            }

            // 2. Try LocalLlamaService
            try {
                Log.d(tag, "Attempting query with LocalLlamaService")
                val localResponse = localLlamaService.generateResponse(fullPrompt)
                Log.i(tag, "Response from LocalLlamaService: $localResponse")
                // Basic validation of response
                if (localResponse.isNotBlank() && !localResponse.contains("Error:", ignoreCase = true)) {
                    return localResponse
                }
                Log.w(tag, "LocalLlamaService response was empty or indicated an error.")
                // Fall through to basic fallback
            } catch (e: Exception) {
                Log.e(tag, "Error querying LocalLlamaService. Falling back to basic handler.", e)
                // Fall through to basic fallback
            }

            // 3. Fallback to basic database queries
            Log.w(tag, "LLM attempts failed. Falling back to basic database query handler.")
            handleBasicDatabaseQuery(query)

        } catch (e: Exception) {
            Log.e(tag, "Critical error during natural language query processing for '$query'", e)
            // Final fallback if even basic handler fails
            "Ocurrió un error inesperado al procesar tu consulta. Por favor, intenta de nuevo más tarde. Detalles: ${e.message}"
        }
    }

    /**
     * Get comprehensive database context for better LLM responses
     */
    private suspend fun getDatabaseContext(): String = withContext(Dispatchers.IO) {
        val contextBuilder = StringBuilder()

        try {
            // Obtener datos completos de cada entidad
            val usuarios = database.usuarioDao().getAllUsuarios()
            val videos = database.videoDao().getAllVideos()
            val topics = database.topicDao().getAllTopics()
            val contentItems = database.contentItemDao().getAllContentItems()
            val personas = database.personaDao().getAllPersonasList()
            val tasks = database.taskDao().getAllTasks()
            val subscriptions = database.subscriptionDao().getAllSubscriptions()
            val taskSubmissions = database.taskSubmissionDao().getAllTaskSubmissions()
            val purchases = database.purchaseDao().getAllPurchases()

            contextBuilder.append("Resumen de Datos:\n")
            contextBuilder.append("- Total Usuarios: ${usuarios.size}\n")
            contextBuilder.append("- Total Videos (Cursos): ${videos.size}\n")
            contextBuilder.append("- Total Temas: ${topics.size}\n")
            contextBuilder.append("- Total Elementos de Contenido: ${contentItems.size}\n")
            contextBuilder.append("- Total Personas: ${personas.size}\n")
            contextBuilder.append("- Total Tareas: ${tasks.size}\n")
            contextBuilder.append("- Total Suscripciones: ${subscriptions.size}\n")
            contextBuilder.append("- Total Entregas de Tareas: ${taskSubmissions.size}\n")
            contextBuilder.append("- Total Compras: ${purchases.size}\n\n")

            // Esquema detallado de todas las tablas
            contextBuilder.append("Esquema de la Base de Datos (Tablas y Columnas Principales):\n")
            contextBuilder.append("- Tabla 'usuarios': id (INTEGER PRIMARY KEY), usuario (TEXT UNIQUE NOT NULL), contrasena (TEXT NOT NULL), persona_id (INTEGER, FK -> personas.id)\n")
            contextBuilder.append("- Tabla 'personas': id (INTEGER PRIMARY KEY), identificacion (TEXT), nombres (TEXT NOT NULL), apellidos (TEXT NOT NULL), email (TEXT UNIQUE), telefono (TEXT), direccion (TEXT), fechaNacimiento (TEXT), avatar (TEXT), esUsuario (INTEGER NOT NULL DEFAULT 0)\n")
            contextBuilder.append("- Tabla 'videos': id (INTEGER PRIMARY KEY), username (TEXT NOT NULL), description (TEXT), title (TEXT NOT NULL), videoUriString (TEXT), timestamp (INTEGER NOT NULL), localFilePath (TEXT)\n")
            contextBuilder.append("- Tabla 'topics': id (INTEGER PRIMARY KEY), courseId (INTEGER NOT NULL, FK -> videos.id ON DELETE CASCADE), name (TEXT NOT NULL), description (TEXT), orderIndex (INTEGER NOT NULL)\n")
            contextBuilder.append("- Tabla 'content_items': id (INTEGER PRIMARY KEY), topicId (INTEGER NOT NULL, FK -> topics.id ON DELETE CASCADE), name (TEXT NOT NULL), uriString (TEXT NOT NULL), contentType (TEXT NOT NULL), orderIndex (INTEGER NOT NULL)\n")
            contextBuilder.append("- Tabla 'tasks': id (INTEGER PRIMARY KEY AUTOINCREMENT), topicId (INTEGER NOT NULL, FK -> topics.id), name (TEXT NOT NULL), description (TEXT), orderIndex (INTEGER NOT NULL), completed (INTEGER NOT NULL DEFAULT 0)\n")
            contextBuilder.append("- Tabla 'subscriptions': subscriberUsername (TEXT NOT NULL), creatorUsername (TEXT NOT NULL), subscriptionDate (LONG NOT NULL)\n")
            contextBuilder.append("- Tabla 'task_submissions': id (INTEGER PRIMARY KEY AUTOINCREMENT), taskId (INTEGER NOT NULL, FK -> tasks.id), studentUsername (TEXT NOT NULL), submissionDate (LONG NOT NULL), fileUri (TEXT), grade (REAL), feedback (TEXT)\n")
            contextBuilder.append("- Tabla 'purchases': id (INTEGER PRIMARY KEY AUTOINCREMENT), username (TEXT NOT NULL), courseId (INTEGER NOT NULL, FK -> videos.id), purchaseDate (LONG NOT NULL), price (REAL NOT NULL)\n\n")

            // Relaciones clave
            contextBuilder.append("Relaciones Clave:\n")
            contextBuilder.append("- Un 'Usuario' está vinculado a una 'Persona' por 'persona_id'.\n")
            contextBuilder.append("- Un 'Video' (Curso) puede tener múltiples 'Topics' asociados por 'courseId'.\n")
            contextBuilder.append("- Un 'Topic' puede tener múltiples 'ContentItems' asociados por 'topicId'.\n")
            contextBuilder.append("- Una 'Tarea' pertenece a un 'Topic' por 'topicId'.\n")
            contextBuilder.append("- Una 'Entrega de Tarea' pertenece a una 'Tarea' por 'taskId'.\n")
            contextBuilder.append("- Una 'Suscripción' conecta un usuario suscriptor con un creador.\n")
            contextBuilder.append("- Una 'Compra' conecta un usuario con un curso (video).\n\n")

            // No agregar ejemplos de datos aquí
        } catch (e: Exception) {
            Log.e(tag, "Error al obtener contexto detallado de la base de datos", e)
            contextBuilder.append("\nError al obtener contexto de la base de datos: ${e.message}")
        }

        return@withContext contextBuilder.toString()
    }

    /**
     * Handle basic database queries when LLM is not available or fails.
     * Tries to interpret the query and provide a direct answer from the database.
     * This acts as a robust fallback mechanism.
     */
    /**
     * Fallback handler for basic, direct database queries based on keywords.
     * This is used when LLM attempts fail.
     */
    private suspend fun handleBasicDatabaseQuery(query: String): String = withContext(Dispatchers.IO) {
        Log.d(tag, "Executing basic database query handler for: '$query'")
        val normalizedQuery = query.lowercase().trim()

        return@withContext try {
            // --- List Queries ---
            if (normalizedQuery.startsWith("lista") || normalizedQuery.startsWith("muestra") || normalizedQuery.startsWith("dame")) {
                when {
                    normalizedQuery.contains("usuario") || normalizedQuery.contains("usuarios") -> {
                        val users = database.usuarioDao().getAllUsuarios()
                        if (users.isEmpty()) {
                            "No hay usuarios registrados."
                        } else {
                            "Usuarios:\n" + users.joinToString("\n") { it.toString() }
                        }
                    }
                    normalizedQuery.contains("video") || normalizedQuery.contains("videos") || normalizedQuery.contains("curso") || normalizedQuery.contains("cursos") -> {
                        val videos = database.videoDao().getAllVideos()
                        if (videos.isEmpty()) {
                            "No hay videos (cursos) registrados."
                        } else {
                            "Lista de Videos (Cursos):\n" + videos.joinToString("\n") { it.toString() }
                        }
                    }
                    normalizedQuery.contains("tema") || normalizedQuery.contains("temas") -> {
                        val topics = database.topicDao().getAllTopics()
                        if (topics.isEmpty()) {
                            "No hay temas registrados."
                        } else {
                            "Lista de Temas:\n" + topics.joinToString("\n") { it.toString() }
                        }
                    }
                    normalizedQuery.contains("contenido") || normalizedQuery.contains("contenidos") -> {
                        val items = database.contentItemDao().getAllContentItems()
                        if (items.isEmpty()) {
                            "No hay elementos de contenido."
                        } else {
                            "Lista de Contenido:\n" + items.joinToString("\n") { it.toString() }
                        }
                    }
                    normalizedQuery.contains("persona") || normalizedQuery.contains("personas") -> {
                        val personas = database.personaDao().getAllPersonasList()
                        if (personas.isEmpty()) {
                            "No hay personas registradas."
                        } else {
                            "Lista de Personas:\n" + personas.joinToString("\n") { it.toString() }
                        }
                    }
                    // Agrega aquí más tablas si lo necesitas
                    else -> "No puedo listar eso específicamente. Por favor, sé más claro (ej: 'lista de usuarios')."
                }
            }
            // Si no es una consulta de lista, puedes manejar otros casos aquí o devolver un mensaje por defecto
            "No se reconoció la consulta como una petición de lista."
        } catch (e: Exception) {
            Log.e(tag, "Error executing basic database query for '$query'", e)
            "Ocurrió un error al intentar consultar la base de datos directamente: ${e.message}"
        }
    }


    /**
     * Generates a fresh JSON snapshot of the database (always up-to-date)
     */
    // Change this:
    // private suspend fun generateDatabaseJson(): String = withContext(Dispatchers.IO) {
    // To this:
    suspend fun generateDatabaseJson(): String = withContext(Dispatchers.IO) {
        try {
            val gson = Gson()
            val usuarios = database.usuarioDao().getAllUsuarios()
            val videos = database.videoDao().getAllVideos()
            val topics = database.topicDao().getAllTopics()
            val contentItems = database.contentItemDao().getAllContentItems()
            val personas = database.personaDao().getAllPersonasList()
            val tasks = database.taskDao().getAllTasks()
            val subscriptions = database.subscriptionDao().getAllSubscriptions()
            val taskSubmissions = database.taskSubmissionDao().getAllTaskSubmissions()
            val purchases = database.purchaseDao().getAllPurchases()

            val json = JSONObject()
            json.put("usuarios", JSONArray(gson.toJsonTree(usuarios).asJsonArray.toString()))
            json.put("videos", JSONArray(gson.toJsonTree(videos).asJsonArray.toString()))
            json.put("topics", JSONArray(gson.toJsonTree(topics).asJsonArray.toString()))
            json.put("contentItems", JSONArray(gson.toJsonTree(contentItems).asJsonArray.toString()))
            json.put("personas", JSONArray(gson.toJsonTree(personas).asJsonArray.toString()))
            json.put("tasks", JSONArray(gson.toJsonTree(tasks).asJsonArray.toString()))
            json.put("subscriptions", JSONArray(gson.toJsonTree(subscriptions).asJsonArray.toString()))
            json.put("taskSubmissions", JSONArray(gson.toJsonTree(taskSubmissions).asJsonArray.toString()))
            json.put("purchases", JSONArray(gson.toJsonTree(purchases).asJsonArray.toString()))

            // Optional: add schema
            val schema = JSONObject()
            schema.put("usuarios", "id, usuario, contrasena, persona_id")
            schema.put("personas", "id, identificacion, nombres, apellidos, email, telefono, direccion, fechaNacimiento, avatar, esUsuario")
            schema.put("videos", "id, username, description, title, videoUriString, timestamp, localFilePath")
            schema.put("topics", "id, courseId, name, description, orderIndex")
            schema.put("contentItems", "id, topicId, name, uriString, contentType, orderIndex")
            schema.put("tasks", "id, topicId, name, description, orderIndex, completed")
            schema.put("subscriptions", "subscriberUsername, creatorUsername, subscriptionDate")
            schema.put("taskSubmissions", "id, taskId, studentUsername, submissionDate, fileUri, grade, feedback")
            schema.put("purchases", "id, username, courseId, purchaseDate, price")
            json.put("schema", schema)

            return@withContext json.toString()
        } catch (e: Exception) {
            Log.e(tag, "Error generating database JSON", e)
            return@withContext "{}"
        }
    }
}