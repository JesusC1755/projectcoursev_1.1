package com.example.tareamov.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tareamov.R
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.TaskSubmission
import com.example.tareamov.data.entity.FileContext
import com.example.tareamov.service.FileAnalysisService
import com.example.tareamov.service.MCPService
import com.example.tareamov.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class TaskSubmissionsFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SubmissionsAdapter
    private lateinit var sessionManager: SessionManager
    private lateinit var fileAnalysisService: FileAnalysisService
    private lateinit var mcpService: MCPService
    private var taskId: Long = -1
    private var taskName: String = ""
    private var courseCreatorUsername: String? = null
    private var isCourseCreator: Boolean = false
    private var selectedFileUri: Uri? = null
    private var hasUserSubmitted = false
    private var userSubmission: TaskSubmission? = null

    // Progress UI elements
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTextView: TextView
    private lateinit var progressSection: LinearLayout

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Verificar si el URI es de Google Drive
            val isGoogleDriveUri = uri.authority?.contains("google") == true || 
                                   uri.authority?.contains("docs") == true ||
                                   uri.toString().contains("google") ||
                                   uri.toString().contains("docs.google.com")
            
            if (isGoogleDriveUri) {
                // Mostrar mensaje de error si es un archivo de Google Drive
                Toast.makeText(
                    context, 
                    "⚠️ Los archivos de Google Drive no son compatibles. Por favor, descarga el archivo a tu almacenamiento local primero.", 
                    Toast.LENGTH_LONG
                ).show()
                
                // No asignar el URI de Google Drive
                selectedFileUri = null
                view?.findViewById<TextView>(R.id.selectedFileNameTextView)?.text = "Ningún archivo seleccionado"
            } else {
                // Es un archivo local, procesarlo normalmente
                selectedFileUri = uri
                view?.findViewById<TextView>(R.id.selectedFileNameTextView)?.text = uri.lastPathSegment ?: "Archivo seleccionado"

                // Take persistable URI permission
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            taskId = it.getLong("taskId", -1)
            taskName = it.getString("taskName", "")
            courseCreatorUsername = it.getString("courseCreatorUsername")
        }
        sessionManager = SessionManager.getInstance(requireContext())
        fileAnalysisService = FileAnalysisService(requireContext())
        mcpService = MCPService(requireContext())
        val currentUsername = sessionManager.getUsername()
        isCourseCreator = (courseCreatorUsername != null && courseCreatorUsername == currentUsername)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_task_submissions, container, false)

        val titleTextView = view.findViewById<TextView>(R.id.taskTitleTextView)
        titleTextView.text = taskName

        // Initialize progress UI elements
        progressSection = view.findViewById(R.id.progressSection)
        progressBar = view.findViewById(R.id.taskProgressBar)
        progressTextView = view.findViewById(R.id.progressTextView)

        recyclerView = view.findViewById(R.id.submissionsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = SubmissionsAdapter(emptyList()) { submission, grade, feedback ->
            updateSubmissionGrade(submission, grade, feedback)
        }
        recyclerView.adapter = adapter

        val backButton = view.findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Setup file upload section
        val uploadSection = view.findViewById<LinearLayout>(R.id.uploadSection)
        val selectFileButton = view.findViewById<Button>(R.id.selectFileButton)
        val submitFileButton = view.findViewById<Button>(R.id.submitFileButton)
        val selectedFileNameTextView = view.findViewById<TextView>(R.id.selectedFileNameTextView)
        val mySubmissionStatusTextView = view.findViewById<TextView>(R.id.mySubmissionStatusTextView)
        val emptyStateTextView = view.findViewById<TextView>(R.id.emptyStateTextView)

        // Configure visibility based on user role
        if (isCourseCreator) {
            // Course creator sees progress of all students
            progressSection.visibility = View.VISIBLE
            uploadSection.visibility = View.GONE
            emptyStateTextView.text = "No hay entregas para esta tarea"
            loadTaskProgress()
        } else {
            // Regular student sees their own progress
            progressSection.visibility = View.VISIBLE
            uploadSection.visibility = View.VISIBLE
            emptyStateTextView.text = "No has entregado esta tarea aún"
            selectFileButton.setOnClickListener { openFilePicker() }
            submitFileButton.setOnClickListener { submitTaskFile() }

            // Check if user has already submitted this task
            checkUserSubmission(mySubmissionStatusTextView)
        }

        loadSubmissions()
        return view
    }

    private fun loadTaskProgress() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val db = AppDatabase.getDatabase(requireContext())

                // Get total number of students in the course
                val courseId = withContext(Dispatchers.IO) {
                    // First get the task to find its topicId
                    val task = db.taskDao().getTaskById(taskId)
                    if (task != null) {
                        // Then get the topic to find its courseId
                        val topic = db.topicDao().getTopicById(task.topicId)
                        topic?.courseId
                    } else {
                        null
                    }
                }

                if (courseId == null) {
                    Log.e("TaskSubmissionsFragment", "Could not determine course ID")
                    return@launch
                }

                // Get all students subscribed to this course
                val students = withContext(Dispatchers.IO) {
                    // Use the courseCreatorUsername that was passed to the fragment
                    if (courseCreatorUsername != null) {
                        db.subscriptionDao().getSubscribersByCreator(courseCreatorUsername!!)
                    } else {
                        // If courseCreatorUsername wasn't passed, try to get it from the course
                        val course = db.videoDao().getVideoById(courseId)
                        if (course?.username != null) {
                            db.subscriptionDao().getSubscribersByCreator(course.username)
                        } else {
                            emptyList()
                        }
                    }
                }

                // Get all submissions for this task
                val submissions = withContext(Dispatchers.IO) {
                    db.taskSubmissionDao().getSubmissionsByTask(taskId)
                }

                // Calculate progress
                val totalStudents = students.size
                val submittedCount = submissions.size
                val gradedCount = submissions.count { it.grade != null }

                // Update UI
                if (totalStudents > 0) {
                    val submissionPercentage = (submittedCount * 100) / totalStudents
                    progressBar.max = 100
                    progressBar.progress = submissionPercentage

                    progressTextView.text = "$submittedCount de $totalStudents estudiantes han entregado " +
                            "($gradedCount calificados)"
                } else {
                    progressBar.progress = 0
                    progressTextView.text = "No hay estudiantes inscritos en este curso"
                }

            } catch (e: Exception) {
                Log.e("TaskSubmissionsFragment", "Error loading task progress", e)
            }
        }
    }

    private fun checkUserSubmission(statusTextView: TextView) {
        val username = sessionManager.getUsername() ?: return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                val submission = withContext(Dispatchers.IO) {
                    db.taskSubmissionDao().getUserSubmissionForTask(taskId, username)
                }

                if (submission != null) {
                    // User has already submitted
                    hasUserSubmitted = true
                    userSubmission = submission

                    // Update UI to show submission status
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val dateString = dateFormat.format(submission.submissionDate)

                    val gradeText = if (submission.grade != null) {
                        "Calificación: ${submission.grade}/10"
                    } else {
                        "Pendiente de calificación"
                    }

                    statusTextView.text = "Enviado el $dateString\n$gradeText"
                    statusTextView.setTextColor(resources.getColor(android.R.color.holo_green_light, null))

                    // Update progress for student
                    progressBar.max = 100
                    progressBar.progress = if (submission.grade != null) 100 else 50
                    progressTextView.text = if (submission.grade != null)
                        "Tarea completada y calificada"
                    else
                        "Tarea entregada, pendiente de calificación"

                    // Disable submit button
                    view?.findViewById<Button>(R.id.submitFileButton)?.isEnabled = false
                    view?.findViewById<Button>(R.id.submitFileButton)?.text = "Ya enviado"
                } else {
                    // User hasn't submitted yet
                    hasUserSubmitted = false
                    statusTextView.text = "No has enviado ninguna tarea aún"
                    statusTextView.setTextColor(resources.getColor(android.R.color.darker_gray, null))

                    // Update progress for student
                    progressBar.max = 100
                    progressBar.progress = 0
                    progressTextView.text = "Tarea pendiente de entrega"
                }
            } catch (e: Exception) {
                Log.e("TaskSubmissionsFragment", "Error checking user submission", e)
            }
        }
    }

    private fun loadSubmissions() {
        if (taskId == -1L) {
            Toast.makeText(context, "Error: ID de tarea inválido", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                val submissions = withContext(Dispatchers.IO) {
                    // If course creator, show all submissions
                    // If student, only show their own submission
                    if (isCourseCreator) {
                        db.taskSubmissionDao().getSubmissionsByTask(taskId)
                    } else {
                        val username = sessionManager.getUsername()
                        if (username != null) {
                            db.taskSubmissionDao().getUserSubmissionForTask(taskId, username)?.let {
                                listOf(it)
                            } ?: emptyList()
                        } else {
                            emptyList()
                        }
                    }
                }

                if (submissions.isEmpty()) {
                    // Show empty state
                    view?.findViewById<TextView>(R.id.emptyStateTextView)?.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    // Show submissions
                    view?.findViewById<TextView>(R.id.emptyStateTextView)?.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.updateSubmissions(submissions)
                }

            } catch (e: Exception) {
                Log.e("TaskSubmissionsFragment", "Error loading submissions", e)
                Toast.makeText(context, "Error al cargar entregas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSubmissionGrade(submission: TaskSubmission, grade: Float, feedback: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                val updatedSubmission = submission.copy(grade = grade, feedback = feedback)

                withContext(Dispatchers.IO) {
                    db.taskSubmissionDao().updateSubmission(updatedSubmission)
                }

                Toast.makeText(context, "Calificación guardada", Toast.LENGTH_SHORT).show()
                loadSubmissions()

                // Refresh progress after grading
                if (isCourseCreator) {
                    loadTaskProgress()
                }
            } catch (e: Exception) {
                Log.e("TaskSubmissionsFragment", "Error updating grade", e)
                Toast.makeText(context, "Error al guardar calificación: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitTaskFile() {
        val uri = selectedFileUri
        if (uri == null) {
            Toast.makeText(context, "Selecciona un archivo primero", Toast.LENGTH_SHORT).show()
            return
        }

        val username = sessionManager.getUsername()
        if (username == null) {
            Toast.makeText(context, "Debes iniciar sesión para enviar tareas", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = uri.lastPathSegment ?: "archivo_tarea"
        val submission = TaskSubmission(
            taskId = taskId,
            studentUsername = username,
            fileUri = uri.toString(),
            fileName = fileName,
            submissionDate = System.currentTimeMillis(),
            grade = null,
            feedback = null
        )

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                withContext(Dispatchers.IO) {
                    db.taskSubmissionDao().insertSubmission(submission)
                }

                Toast.makeText(context, "Tarea enviada correctamente", Toast.LENGTH_SHORT).show()
                selectedFileUri = null
                view?.findViewById<TextView>(R.id.selectedFileNameTextView)?.text = "Ningún archivo seleccionado"

                // Update submission status
                val statusTextView = view?.findViewById<TextView>(R.id.mySubmissionStatusTextView)
                if (statusTextView != null) {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val dateString = dateFormat.format(System.currentTimeMillis())
                    statusTextView.text = "Enviado el $dateString\nPendiente de calificación"
                    statusTextView.setTextColor(resources.getColor(android.R.color.holo_green_light, null))
                }

                // Update progress after submission
                progressBar.max = 100
                progressBar.progress = 50
                progressTextView.text = "Tarea entregada, pendiente de calificación"

                // Disable submit button
                view?.findViewById<Button>(R.id.submitFileButton)?.isEnabled = false
                view?.findViewById<Button>(R.id.submitFileButton)?.text = "Ya enviado"

                // Reload submissions to show the new one
                loadSubmissions()
            } catch (e: Exception) {
                Log.e("TaskSubmissionsFragment", "Error submitting task", e)
                Toast.makeText(context, "Error al enviar tarea: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFilePicker() {
        // Mostrar mensaje para aclarar que deben seleccionar archivos locales
        Toast.makeText(
            context, 
            "Selecciona un archivo de tu almacenamiento local. Los archivos de Google Drive no son compatibles.", 
            Toast.LENGTH_LONG
        ).show()
        
        // Lanzar selector de archivos
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    private fun createFileContextAndNavigateToChat(submission: TaskSubmission) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d("TaskSubmissionsFragment", "🔄 Iniciando análisis de archivo: ${submission.fileName}")
                
                // Mostrar indicador de progreso
                progressSection.visibility = View.VISIBLE
                progressBar.isIndeterminate = true
                progressTextView.text = "Procesando archivo ${submission.fileName}..."
                
                val uri = Uri.parse(submission.fileUri)
                Log.d("TaskSubmissionsFragment", "📂 URI del archivo: $uri")
                
                // Convertir el archivo a formato JSON estructurado usando el servicio MCP
                val fileContext = mcpService.convertFileToJson(uri, submission.fileName)
                
                progressTextView.text = "Archivo procesado. Preparando interfaz de chat..."
                progressBar.isIndeterminate = false
                progressBar.progress = 90
                
                // Navegar al chat con el contexto del archivo
                navigateToChatWithFileContext(fileContext)
                
            } catch (e: Exception) {
                Log.e("TaskSubmissionsFragment", "❌ Error procesando archivo: ${e.message}", e)
                
                // Mostrar error al usuario
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        context,
                        "Error procesando archivo: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Crear contexto de error específico para Google Drive
                    val errorFileContext = FileContext(
                        submissionId = submission.id,
                        fileName = submission.fileName,
                        fileType = "google_drive_error",
                        fileContent = "Este archivo está en Google Drive y no puede ser procesado directamente.",
                        extractedText = "ERROR: Los archivos de Google Drive deben descargarse localmente antes de subirlos.",
                        metadata = "Error: Archivo de Google Drive detectado - Por favor descarga el archivo localmente"
                    )
                    
                    // Navegar al chat con mensaje de error
                    navigateToChatWithFileContext(errorFileContext, true)
                }
                
                // PRIMERA ESTRATEGIA: Intentar con el MCP Service para convertir a JSON
                // Esta es la estrategia preferida para cualquier tipo de archivo
                try {
                    Log.d("TaskSubmissionsFragment", "🌐 Intentando procesar con MCP Service")
                    
                    // Verificar si el servidor MCP está disponible
                    val isMcpAvailable = mcpService.testMCPServerConnection()
                    
                    if (isMcpAvailable) {
                        // El servidor MCP está disponible, usar para convertir el archivo a JSON
                        Log.d("TaskSubmissionsFragment", "✅ Servidor MCP disponible, convirtiendo archivo a JSON")
                        Toast.makeText(context, "Convirtiendo archivo con MCP...", Toast.LENGTH_SHORT).show()
                        
                        val fileContext = withContext(Dispatchers.IO) {
                            mcpService.convertFileToJson(Uri.parse(submission.fileUri), submission.fileName)
                        }
                        
                        // Actualizar el ID de la entrega
                        val updatedFileContext = fileContext.copy(submissionId = submission.id)
                        
                        // Guardar el contexto en la base de datos y navegar al chat
                        navigateToChatWithFileContext(
                            updatedFileContext,
                            fileContext.fileType == "google_drive_error" // Es error si es de tipo google_drive_error
                        )
                        return@launch
                    } else {
                        Log.d("TaskSubmissionsFragment", "⚠️ Servidor MCP no disponible, intentando con FileAnalysisService")
                        Toast.makeText(context, "MCP no disponible, usando análisis alternativo...", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("TaskSubmissionsFragment", "❌ Error usando MCP Service: ${e.message}", e)
                    // Continuamos con FileAnalysisService como fallback
                }
                
                // SEGUNDA ESTRATEGIA (Fallback): Usar FileAnalysisService 
                // Solo si MCP no está disponible o falló
                Log.d("TaskSubmissionsFragment", "🔄 Intentando con FileAnalysisService como fallback")
                
                val analysisResult = withContext(Dispatchers.IO) {
                    fileAnalysisService.extractFileContent(Uri.parse(submission.fileUri), submission.fileName)
                }
                
                Log.d("TaskSubmissionsFragment", "📊 Resultado del análisis - Éxito: ${analysisResult.success}")
                
                if (!analysisResult.success) {
                    Log.e("TaskSubmissionsFragment", "❌ Error en análisis: ${analysisResult.error}")
                    
                    // Crear un FileContext con contenido de error para que el usuario pueda ver el error en el chat
                    val errorMsg = if (submission.fileUri.contains("google") || submission.fileUri.contains("docs")) {
                        "Este archivo está en Google Drive y no se puede acceder directamente. " +
                        "Por favor, descárgalo primero a tu dispositivo."
                    } else {
                        "No se pudo acceder al contenido del archivo. ${analysisResult.error ?: ""}"
                    }
                    
                    val errorFileContext = FileContext(
                        submissionId = submission.id,
                        fileName = submission.fileName,
                        fileType = analysisResult.fileType.name,
                        fileContent = errorMsg,
                        extractedText = "Error de acceso: ${analysisResult.error}",
                        metadata = if (submission.fileUri.contains("google") || submission.fileUri.contains("docs")) 
                                     "Error: Archivo de Google Drive inaccesible - URI: ${submission.fileUri}" 
                                   else 
                                     "Error: Archivo inaccesible - URI: ${submission.fileUri}"
                    )
                    
                    // Navegar al chat con el contexto de error
                    navigateToChatWithFileContext(errorFileContext, true)
                    return@launch
                }
                
                val fileType = fileAnalysisService.getFileType(submission.fileName)
                Log.d("TaskSubmissionsFragment", "📄 Tipo de archivo: $fileType")
                Log.d("TaskSubmissionsFragment", "📝 Contenido extraído (${analysisResult.content.length} caracteres): ${analysisResult.content.take(100)}...")
                
                val fileContext = FileContext(
                    submissionId = submission.id,
                    fileName = submission.fileName,
                    fileType = fileType.name, // Convert enum to string
                    fileContent = analysisResult.content, // Get content from result
                    extractedText = analysisResult.content, // Use the same content
                    metadata = analysisResult.metadata // Include metadata from analysis
                )
                
                // Guardar el contexto en la base de datos y navegar al chat
                navigateToChatWithFileContext(fileContext)
                
            } catch (e: Exception) {
                Log.e("TaskSubmissionsFragment", "❌ Error general procesando archivo: ${e.message}", e)
                
                val errorMessage = when {
                    e.message?.contains("StorageFileLoadException") == true ||
                    e.message?.contains("connection_failure") == true -> {
                        "El archivo no está disponible. Si es de Google Drive, descárgalo localmente primero e inténtalo de nuevo."
                    }
                    e.message?.contains("FileNotFoundException") == true -> {
                        "Archivo no encontrado. Verifica que el archivo sigue estando disponible."
                    }
                    e.message?.contains("failed to connect") == true -> {
                        "Error de conexión con el servicio. Verifica que MCP o Ollama estén ejecutándose."
                    }
                    else -> {
                        "Error procesando el archivo: ${e.message}"
                    }
                }
                
                Toast.makeText(
                    context, 
                    "$errorMessage\n\nAbriendo chat en modo básico.", 
                    Toast.LENGTH_LONG
                ).show()
                
                // Navegar al chat sin contexto de archivo, pero con información del error
                val bundle = Bundle().apply {
                    putString("errorMessage", errorMessage)
                    putString("fileName", submission.fileName)
                }
                findNavController().navigate(
                    R.id.action_taskSubmissionFragment_to_chatBotFragment,
                    bundle
                )
            }
        }
    }

    /**
     * Navega al chat con un contexto de archivo, opcionalmente marcando si hay error
     * Esta función es suspendida porque necesita guardar el contexto del archivo en la base de datos,
     * lo cual es una operación de suspensión.
     */
    private suspend fun navigateToChatWithFileContext(fileContext: FileContext, isError: Boolean = false) {
        try {
            // Guardar el contexto del archivo en la base de datos
            val database = AppDatabase.getDatabase(requireContext())
            val savedId = database.fileContextDao().insertFileContext(fileContext)
            Log.d("TaskSubmissionsFragment", "📝 FileContext guardado con ID: $savedId")
            
            // El resto del código debe ejecutarse en el hilo principal para UI
            withContext(Dispatchers.Main) {
                // Ocultar progreso
                progressSection.visibility = View.GONE
                
                // Determinar el mensaje según el tipo de archivo
                val message = when {
                    // Error de Google Drive específico
                    fileContext.fileType == "google_drive_error" -> {
                        "📱 Archivo de Google Drive detectado. Se proporcionarán instrucciones en el chat."
                    }
                    // Otros errores genéricos
                    isError -> {
                        "⚠️ No se pudo acceder completamente al archivo. Se iniciará el chat con información limitada."
                    }
                    // Éxito con diferentes tipos de archivo
                    fileContext.fileType.equals("pdf", ignoreCase = true) -> {
                        "📄 Documento PDF procesado. Puedes hacer preguntas sobre su contenido."
                    }
                    fileContext.fileType.equals("docx", ignoreCase = true) || 
                    fileContext.fileType.equals("doc", ignoreCase = true) -> {
                        "📝 Documento Word procesado. Puedes hacer preguntas sobre su contenido."
                    }
                    fileContext.fileType.equals("pptx", ignoreCase = true) || 
                    fileContext.fileType.equals("ppt", ignoreCase = true) -> {
                        "🎮 Presentación PowerPoint procesada. Puedes hacer preguntas sobre su contenido."
                    }
                    fileContext.fileType.equals("xlsx", ignoreCase = true) || 
                    fileContext.fileType.equals("xls", ignoreCase = true) -> {
                        "📊 Hoja de cálculo Excel procesada. Puedes hacer preguntas sobre su contenido."
                    }
                    fileContext.fileType.equals("txt", ignoreCase = true) -> {
                        "📃 Archivo de texto procesado. Puedes hacer preguntas sobre su contenido."
                    }
                    fileContext.fileType.equals("json", ignoreCase = true) -> {
                        "📋 Archivo JSON procesado. Puedes hacer preguntas sobre su estructura y contenido."
                    }
                    else -> {
                        "✅ Archivo analizado correctamente. Iniciando chat con contexto."
                    }
                }
                
                // Mostrar mensaje apropiado
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                
                // Crear argumentos para el fragmento de chat
                val args = Bundle().apply {
                    putLong("submissionId", fileContext.submissionId)
                    if (isError) {
                        // Si es un error de Google Drive, enviamos mensaje especial
                        if (fileContext.fileType == "google_drive_error") {
                            putString("errorMessage", "Este archivo está en Google Drive y requiere ser descargado localmente primero.")
                        } else {
                            putString("errorMessage", "No se pudo acceder al archivo. Por favor descárgalo primero.")
                        }
                    }
                    putString("fileName", fileContext.fileName)
                }
                
                // Navegar al chat
                findNavController().navigate(R.id.action_taskSubmissionFragment_to_chatBotFragment, args)
            }
            
        } catch (e: Exception) {
            Log.e("TaskSubmissionsFragment", "❌ Error navegando al chat: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error iniciando chat: ${e.message}", Toast.LENGTH_SHORT).show()
                
                // Si falla guardar el contexto, al menos intentamos navegar al chat en modo básico
                val args = Bundle().apply {
                    putString("errorMessage", "Error preparando el contexto del archivo: ${e.message}")
                    putString("fileName", fileContext.fileName)
                }
                findNavController().navigate(R.id.action_taskSubmissionFragment_to_chatBotFragment, args)
            }
        }
    }

    inner class SubmissionsAdapter(
        private var submissions: List<TaskSubmission>,
        private val onGradeSubmitted: (TaskSubmission, Float, String) -> Unit
    ) : RecyclerView.Adapter<SubmissionsAdapter.ViewHolder>() {

        fun updateSubmissions(newSubmissions: List<TaskSubmission>) {
            submissions = newSubmissions
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_submission, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(submissions[position])
        }

        override fun getItemCount() = submissions.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val avatarImageView: CircleImageView = itemView.findViewById(R.id.avatarImageView)
            private val studentNameTextView: TextView = itemView.findViewById(R.id.studentNameTextView)
            private val submissionDateTextView: TextView = itemView.findViewById(R.id.submissionDateTextView)
            private val fileNameTextView: TextView = itemView.findViewById(R.id.fileNameTextView)
            private val viewFileButton: Button = itemView.findViewById(R.id.viewFileButton)
            private val chatBotButton: Button = itemView.findViewById(R.id.chatBotButton)
            private val gradeEditText: EditText = itemView.findViewById(R.id.gradeEditText)
            private val feedbackEditText: EditText = itemView.findViewById(R.id.feedbackEditText)
            private val submitGradeButton: Button = itemView.findViewById(R.id.submitGradeButton)
            private val gradeSection: View = itemView.findViewById(R.id.gradeSection)
            private val gradeDisplayTextView: TextView = itemView.findViewById(R.id.gradeDisplayTextView)
            private val feedbackDisplayTextView: TextView = itemView.findViewById(R.id.feedbackDisplayTextView)

            fun bind(submission: TaskSubmission) {
                studentNameTextView.text = submission.studentUsername

                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                submissionDateTextView.text = "Entregado: ${dateFormat.format(submission.submissionDate)}"

                fileNameTextView.text = submission.fileName

                // Load user avatar
                loadUserAvatar(submission.studentUsername)

                viewFileButton.setOnClickListener {
                    openSubmissionFile(submission.fileUri)
                }

                chatBotButton.setOnClickListener {
                    // Create file context and navigate to ChatBot fragment
                    createFileContextAndNavigateToChat(submission)
                }

                // Only show grading controls to course creator
                if (isCourseCreator) {
                    gradeSection.visibility = View.VISIBLE
                    gradeDisplayTextView.visibility = View.GONE
                    feedbackDisplayTextView.visibility = View.GONE

                    // Pre-fill existing grade and feedback if available
                    gradeEditText.setText(submission.grade?.toString() ?: "")
                    feedbackEditText.setText(submission.feedback ?: "")

                    submitGradeButton.setOnClickListener {
                        val gradeText = gradeEditText.text.toString()
                        if (gradeText.isBlank()) {
                            Toast.makeText(context, "Ingresa una calificación", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        try {
                            val grade = gradeText.toFloat()
                            if (grade < 0 || grade > 10) {
                                Toast.makeText(context, "La calificación debe estar entre 0 y 10", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }

                            val feedback = feedbackEditText.text.toString()
                            onGradeSubmitted(submission, grade, feedback)
                        } catch (e: NumberFormatException) {
                            Toast.makeText(context, "Formato de calificación inválido", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    gradeSection.visibility = View.GONE

                    // For students, show their grade if available
                    if (submission.grade != null) {
                        gradeDisplayTextView.visibility = View.VISIBLE
                        gradeDisplayTextView.text = "Calificación: ${submission.grade}/10"

                        // Show feedback if available
                        if (!submission.feedback.isNullOrBlank()) {
                            feedbackDisplayTextView.visibility = View.VISIBLE
                            feedbackDisplayTextView.text = "Comentarios: ${submission.feedback}"
                        } else {
                            feedbackDisplayTextView.visibility = View.GONE
                        }
                    } else {
                        gradeDisplayTextView.visibility = View.GONE
                        feedbackDisplayTextView.visibility = View.GONE
                    }
                }
            }

            private fun loadUserAvatar(username: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val db = AppDatabase.getDatabase(requireContext())
                        val persona = withContext(Dispatchers.IO) {
                            db.personaDao().getPersonaByUsername(username)
                        }

                        if (persona != null && !persona.avatar.isNullOrEmpty()) {
                            try {
                                Glide.with(requireContext())
                                    .load(Uri.parse(persona.avatar))
                                    .placeholder(R.drawable.default_avatar)
                                    .error(R.drawable.default_avatar)
                                    .into(avatarImageView)
                            } catch (e: Exception) {
                                Log.e("TaskSubmissionsFragment", "Error loading avatar image", e)
                                avatarImageView.setImageResource(R.drawable.default_avatar)
                            }
                        } else {
                            avatarImageView.setImageResource(R.drawable.default_avatar)
                        }
                    } catch (e: Exception) {
                        Log.e("TaskSubmissionsFragment", "Error loading avatar", e)
                        avatarImageView.setImageResource(R.drawable.default_avatar)
                    }
                }
            }

            private fun openSubmissionFile(uriString: String) {
                try {
                    val uri = Uri.parse(uriString)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("TaskSubmissionsFragment", "Error opening file", e)
                    Toast.makeText(context, "Error al abrir el archivo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}