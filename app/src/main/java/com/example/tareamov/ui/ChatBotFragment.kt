package com.example.tareamov.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tareamov.R
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.ChatMessage
import com.example.tareamov.data.entity.FileContext
import com.example.tareamov.service.AIAnalysisService
import com.example.tareamov.service.FileAnalysisService
import com.example.tareamov.ui.adapter.ChatMessageAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ChatBotFragment : Fragment() {

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var clearChatButton: ImageButton
    private lateinit var loadingProgressBar: ProgressBar
    
    private lateinit var chatAdapter: ChatMessageAdapter
    private lateinit var database: AppDatabase
    private lateinit var aiAnalysisService: AIAnalysisService
    private lateinit var fileAnalysisService: FileAnalysisService
    
    private val sessionId = UUID.randomUUID().toString()
    private var currentFileContext: FileContext? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chatbot, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        database = AppDatabase.getDatabase(requireContext())
        aiAnalysisService = AIAnalysisService(requireContext())
        fileAnalysisService = FileAnalysisService(requireContext())
        
        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadMessages()
        loadFileContextFromArguments()
        
        // Probar conexión con Ollama al iniciar
        testOllamaConnectionOnStart()
    }

    private fun initializeViews(view: View) {
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView)
        messageEditText = view.findViewById(R.id.messageEditText)
        sendButton = view.findViewById(R.id.sendButton)
        backButton = view.findViewById(R.id.backButton)
        clearChatButton = view.findViewById(R.id.clearChatButton)
        loadingProgressBar = view.findViewById(R.id.loadingProgressBar)
        
        database = AppDatabase.getDatabase(requireContext())
    }

    private fun loadFileContextFromArguments() {
        arguments?.let { args ->
            val submissionId = args.getLong("submissionId", -1L)
            val errorMessage = args.getString("errorMessage")
            val fileName = args.getString("fileName")
            
            if (errorMessage != null) {
                // Mostrar mensaje de error del archivo
                lifecycleScope.launch {
                    val errorChatMessage = ChatMessage(
                        message = "⚠️ **Error con el archivo**\n\n" +
                                "📁 Archivo: ${fileName ?: "desconocido"}\n" +
                                "❌ Error: $errorMessage\n\n" +
                                "💬 Puedes seguir usando el chat, pero sin el contexto completo del archivo.\n" +
                                "Para obtener mejor ayuda, intenta subir el archivo localmente.",
                        isFromUser = false,
                        sessionId = sessionId
                    )
                    
                    withContext(Dispatchers.IO) {
                        database.chatMessageDao().insertMessage(errorChatMessage)
                    }
                }
                
                // A pesar del error, intentamos cargar el contexto si existe
                if (submissionId != -1L) {
                    loadFileContextById(submissionId, true)
                }
                
                return
            }
            
            if (submissionId != -1L) {
                loadFileContextById(submissionId, false)
            }
        }
    }
    
    /**
     * Carga el contexto del archivo por ID y muestra mensajes apropiados
     */
    private fun loadFileContextById(submissionId: Long, hasError: Boolean) {
        lifecycleScope.launch {
            currentFileContext = withContext(Dispatchers.IO) {
                database.fileContextDao().getFileContextBySubmission(submissionId)
            }
            
            if (currentFileContext != null) {
                // Verificar si es un error específico de Google Drive
                val isGoogleDriveError = currentFileContext!!.fileType == "google_drive_error"
                
                // Mostrar mensaje inicial con contexto del archivo
                val contextMessage = if (isGoogleDriveError) {
                    ChatMessage(
                        message = "📱 **Archivo de Google Drive detectado**\n\n" +
                                "📄 Nombre: ${currentFileContext!!.fileName}\n" +
                                "⚠️ **No se puede acceder directamente a este archivo**\n\n" +
                                "Para poder analizar este archivo, necesitas:\n" +
                                "1. Abrir Google Drive\n" +
                                "2. Descargar el archivo a tu dispositivo\n" +
                                "3. Volver a subir el archivo desde tu almacenamiento local\n\n" +
                                "Mientras tanto, puedo ayudarte con preguntas generales.",
                        isFromUser = false,
                        sessionId = sessionId
                    )
                } else if (hasError) {
                    ChatMessage(
                        message = "📄 **Archivo parcialmente accesible**\n\n" +
                                "📁 Nombre: ${currentFileContext!!.fileName}\n" +
                                "🔧 Tipo: ${currentFileContext!!.fileType}\n" +
                                "⚠️ El archivo tiene problemas de acceso, pero intentaré ayudarte con la información disponible.\n\n" +
                                "Puedes hacerme preguntas y haré lo mejor posible con los datos limitados.",
                        isFromUser = false,
                        sessionId = sessionId
                    )
                } else {
                    ChatMessage(
                        message = "📁 **Archivo cargado exitosamente**\n\n" +
                                "📄 Nombre: ${currentFileContext!!.fileName}\n" +
                                "🔧 Tipo: ${currentFileContext!!.fileType}\n" +
                                "📊 Contenido: ${currentFileContext!!.fileContent.length} caracteres\n\n" +
                                "✅ Puedes hacerme preguntas sobre este archivo y te ayudaré con el análisis.",
                        isFromUser = false,
                        sessionId = sessionId
                    )
                }
                
                withContext(Dispatchers.IO) {
                    database.chatMessageDao().insertMessage(contextMessage)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatMessageAdapter()
        messagesRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true // Start from bottom
            }
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        clearChatButton.setOnClickListener {
            clearChat()
        }

        messageEditText.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            database.chatMessageDao().getAllMessages().collect { messages ->
                chatAdapter.submitList(messages) {
                    // Scroll to bottom when new messages are added
                    if (messages.isNotEmpty()) {
                        messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val messageText = messageEditText.text.toString().trim()
        if (messageText.isEmpty()) return

        lifecycleScope.launch {
            // Clear input field
            messageEditText.text.clear()
            
            // Save user message
            val userMessage = ChatMessage(
                message = messageText,
                isFromUser = true,
                sessionId = sessionId
            )
            
            withContext(Dispatchers.IO) {
                database.chatMessageDao().insertMessage(userMessage)
            }
            
            // Show loading indicator
            loadingProgressBar.visibility = View.VISIBLE
            
            try {
                // Generate AI response using Ollama
                val botResponse = withContext(Dispatchers.IO) {
                    // Usar la función que prueba múltiples modelos
                    tryMultipleModels(
                        userMessage = messageText,
                        fileContext = currentFileContext
                    )
                }
                
                val botMessage = ChatMessage(
                    message = botResponse,
                    isFromUser = false,
                    sessionId = sessionId
                )
                
                withContext(Dispatchers.IO) {
                    database.chatMessageDao().insertMessage(botMessage)
                }
                
            } catch (e: Exception) {
                // Fallback response si hay error
                val errorMessage = ChatMessage(
                    message = "Lo siento, tuve un problema al procesar tu mensaje. ${generateFallbackResponse(messageText)}",
                    isFromUser = false,
                    sessionId = sessionId
                )
                
                withContext(Dispatchers.IO) {
                    database.chatMessageDao().insertMessage(errorMessage)
                }
            } finally {
                // Hide loading indicator
                loadingProgressBar.visibility = View.GONE
            }
        }
    }

    private fun generateFallbackResponse(userMessage: String): String {
        // Simple bot responses - you can enhance this with actual AI integration
        return when {
            userMessage.contains("hola", ignoreCase = true) || 
            userMessage.contains("buenos días", ignoreCase = true) ||
            userMessage.contains("buenas tardes", ignoreCase = true) -> {
                "¡Hola! Soy tu asistente virtual. ¿En qué puedo ayudarte con tus tareas y estudios?"
            }
            userMessage.contains("tarea", ignoreCase = true) -> {
                "Puedo ayudarte con información sobre tus tareas. ¿Qué necesitas saber específicamente?"
            }
            userMessage.contains("calificación", ignoreCase = true) ||
            userMessage.contains("nota", ignoreCase = true) -> {
                "Para consultar tus calificaciones, revisa la sección de entregas en cada curso. ¿Hay alguna calificación específica que te preocupe?"
            }
            userMessage.contains("curso", ignoreCase = true) -> {
                "Puedo proporcionarte información sobre los cursos disponibles. ¿Qué curso te interesa?"
            }
            userMessage.contains("ayuda", ignoreCase = true) -> {
                "Estoy aquí para ayudarte. Puedo responder preguntas sobre:\n• Tareas y entregas\n• Calificaciones\n• Cursos disponibles\n• Navegación en la aplicación\n\n¿Qué necesitas?"
            }
            userMessage.contains("gracias", ignoreCase = true) -> {
                "¡De nada! Estoy aquí cuando me necesites. ¿Hay algo más en lo que pueda ayudarte?"
            }
            userMessage.contains("adiós", ignoreCase = true) ||
            userMessage.contains("hasta luego", ignoreCase = true) -> {
                "¡Hasta luego! Que tengas un excelente día de estudios. 📚"
            }
            else -> {
                val responses = listOf(
                    "Entiendo tu consulta. ¿Podrías ser más específico para poder ayudarte mejor?",
                    "Interesante pregunta. Te sugiero que revises la documentación del curso o contactes a tu profesor para más detalles.",
                    "Puedo ayudarte con eso. ¿Podrías proporcionarme más contexto sobre lo que necesitas?",
                    "Esa es una buena pregunta. Te recomiendo explorar los recursos del curso o buscar en la biblioteca digital.",
                    "Para obtener la mejor respuesta, te sugiero que consultes con tu instructor o revises el material del curso."
                )
                responses.random()
            }
        }
    }

    private fun clearChat() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                database.chatMessageDao().clearAllMessages()
            }
            Toast.makeText(context, "Chat limpiado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testOllamaConnectionOnStart() {
        lifecycleScope.launch {
            try {
                Log.d("ChatBotFragment", "🔍 Iniciando prueba de conexión con Ollama...")
                
                // Limpiar cache para forzar nuevos intentos
                aiAnalysisService.clearEndpointCache()
                
                // Obtener endpoints detectados para mostrar información
                val detectedEndpoints = aiAnalysisService.getDetectedEndpoints()
                Log.d("ChatBotFragment", "📡 Endpoints detectados: ${detectedEndpoints.size}")
                
                val connectionResult = withContext(Dispatchers.IO) {
                    aiAnalysisService.testOllamaConnection()
                }
                val (serverConnected, graniteAvailable) = connectionResult
                
                val currentEndpoint = aiAnalysisService.getCurrentEndpoint()
                
                val statusMessage = if (serverConnected && currentEndpoint != null) {
                    val modelStatus = if (graniteAvailable) {
                        "✅ Modelo Granite disponible"
                    } else {
                        "⚠️ Modelo Granite NO disponible - Ejecuta: ollama run granite-code"
                    }
                    
                    Log.d("ChatBotFragment", "✅ Conexión exitosa con Ollama en: $currentEndpoint, Granite disponible: $graniteAvailable")
                    "🤖 **Asistente de IA Activado**\n\n" +
                    "✅ Conectado a Ollama exitosamente\n" +
                    "🌐 Endpoint: `$currentEndpoint`\n" +
                    "📡 Endpoints detectados: ${detectedEndpoints.size}\n" +
                    "🧠 Modelo: `granite-code`\n" +
                    "$modelStatus\n" +
                    "💬 Listo para analizar archivos y responder preguntas\n\n" +
                    (if (!graniteAvailable) "⚠️ **IMPORTANTE:** Algunas funciones estarán limitadas hasta que instales el modelo requerido.\n\n" else "") +
                    "¿En qué puedo ayudarte hoy?"
                } else {
                    Log.w("ChatBotFragment", "⚠️ No se pudo conectar con Ollama")
                    val endpointsList = detectedEndpoints.take(3).joinToString("\n") { "• `$it`" }
                    val moreText = if (detectedEndpoints.size > 3) "\n• ... y ${detectedEndpoints.size - 3} más" else ""
                    
                    "🤖 **Asistente de IA (Modo Básico)**\n\n" +
                    "⚠️ No se pudo conectar con Ollama\n" +
                    "📡 Endpoints probados:\n$endpointsList$moreText\n\n" +
                    "📝 Funcionando con respuestas predefinidas\n" +
                    "🔧 Verifica que Ollama esté ejecutándose y que el modelo 'granite-code' esté instalado\n" +
                    "💻 Para instalar el modelo, ejecuta: ollama run granite-code\n\n" +
                    "¿En qué puedo ayudarte?"
                }
                
                // Agregar mensaje de estado del sistema
                val systemMessage = ChatMessage(
                    message = statusMessage,
                    isFromUser = false,
                    sessionId = sessionId
                )
                
                withContext(Dispatchers.IO) {
                    database.chatMessageDao().insertMessage(systemMessage)
                }
                
            } catch (e: Exception) {
                Log.e("ChatBotFragment", "❌ Error probando conexión con Ollama", e)
                
                val errorMessage = ChatMessage(
                    message = "🤖 **Error de Conexión**\n\n" +
                            "❌ Error al conectar con el servicio de IA\n" +
                            "📝 Funcionando en modo básico\n" +
                            "🔧 Revisa la configuración de red\n\n" +
                            "Error: ${e.message}",
                    isFromUser = false,
                    sessionId = sessionId
                )
                
                withContext(Dispatchers.IO) {
                    database.chatMessageDao().insertMessage(errorMessage)
                }
            }
        }
    }

    /**
     * Intenta generar una respuesta utilizando diferentes modelos de Ollama en caso de error
     */
    /**
     * Intenta generar una respuesta con múltiples modelos de IA, fallback en caso de error
     */
    private suspend fun tryMultipleModels(userMessage: String, fileContext: FileContext? = null): String {
        // Verificar primero si Granite está disponible, si no, mostrar mensaje de instalación
        val (serverConnected, graniteAvailable) = aiAnalysisService.testOllamaConnection()
        
        if (!serverConnected) {
            return "⚠️ **Error de conexión con Ollama**\n\n" +
                   "No se pudo conectar al servidor Ollama. Por favor verifica que:\n\n" +
                   "1. El servidor Ollama esté ejecutándose\n" +
                   "2. El puerto 11434 esté abierto y accesible\n" +
                   "3. La conexión de red entre la app y el servidor funcione correctamente\n\n" +
                   "Ejecuta el siguiente comando para iniciar Ollama:\n" +
                   "```\nollama serve\n```"
        }
        
        if (!graniteAvailable) {
            return "⚠️ **Modelo Granite no encontrado**\n\n" +
                   "El modelo requerido '**granite-code**' no está instalado.\n\n" +
                   "Por favor instálalo con el siguiente comando:\n" +
                   "```\nollama run granite-code\n```\n\n" +
                   "Esta aplicación está diseñada para funcionar óptimamente con el modelo Granite y no " +
                   "utilizará otros modelos como alternativa."
        }
        
        // Si Granite está disponible, intentar usarlo
        try {
            Log.d("ChatBotFragment", "Usando el modelo Granite")
            return aiAnalysisService.analyzeWithContext(
                userMessage = userMessage,
                fileContext = fileContext,
                model = "granite-code"
            )
        } catch (e: Exception) {
            Log.e("ChatBotFragment", "Error con modelo Granite: ${e.message}")
            
            // Si el error es específicamente "modelo no encontrado", mostrar mensaje de instalación
            if (e.message?.contains("not found") == true || e.message?.contains("404") == true) {
                return "⚠️ **Modelo Granite no encontrado**\n\n" +
                       "El modelo '**granite-code**' no está disponible en el servidor Ollama.\n\n" +
                       "Por favor instálalo con el siguiente comando:\n" +
                       "```\nollama run granite-code\n```\n\n" +
                       "Esta aplicación está diseñada para funcionar exclusivamente con este modelo."
            }
            
            // Para otros errores, generar respuesta de fallback
            return "Lo siento, tuve un problema al procesar tu mensaje. Error: ${e.message}\n\n" +
                   generateFallbackResponse(userMessage)
        }
    }
}
