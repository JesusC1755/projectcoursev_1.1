package com.example.tareamov.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tareamov.R
import com.example.tareamov.adapter.CreatedCourseAdapter
import com.example.tareamov.adapter.YouTubeStyleVideoAdapter
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.dao.PersonaDao
import com.example.tareamov.data.dao.SubscriptionDao
import com.example.tareamov.data.entity.ContentType
import com.example.tareamov.data.entity.Persona
import com.example.tareamov.data.entity.Subscription
import com.example.tareamov.data.entity.UserContent
import com.example.tareamov.data.entity.VideoData
import com.example.tareamov.util.VideoManager
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class UserProfileViewFragment : Fragment() {

    private lateinit var userAvatarImageView: CircleImageView
    private lateinit var usernameTextView: TextView
    private lateinit var coursesCountTextView: TextView
    private lateinit var videosCountTextView: TextView
    private lateinit var subscribersCountTextView: TextView
    private lateinit var coursesFilterButton: TextView
    private lateinit var videosFilterButton: TextView
    private lateinit var contentRecyclerView: RecyclerView
    private lateinit var emptyStateTextView: TextView
    private lateinit var contentAdapter: CreatedCourseAdapter
    private lateinit var videoAdapter: YouTubeStyleVideoAdapter
    private lateinit var videoManager: VideoManager

    private var allContent = mutableListOf<VideoData>()
    private var allCourses = mutableListOf<VideoData>()
    private var allVideos = mutableListOf<VideoData>()
    private var allUserVideos = mutableListOf<VideoData>() // All videos from the user for search purposes
    private var currentFilter = ContentType.COURSE
    private var isSearchMode = false
    
    // Variable para el usuario cuyo perfil se está viendo
    private var username: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_profile_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            // Inicializar VideoManager
            videoManager = VideoManager(requireContext())
            
            // Obtener el nombre de usuario pasado como argumento (perfil que se está viendo)
            username = arguments?.getString("username")
            
            // Obtener el nombre de usuario actual (usuario que está usando la app)
            val currentUserUsername = getCurrentUsername()
            
            // Inicializar vistas
            initializeViews(view)

            // Configurar RecyclerView
            setupRecyclerView()

            // Configurar botones de filtro
            setupFilterButtons()

            // Configurar el botón de volver
            view.findViewById<ImageView>(R.id.backButton)?.setOnClickListener {
                findNavController().navigateUp()
            }

            // Cargar datos del usuario
            username?.let {
                loadUserData(it)
            }

            // Setup bottom navigation
            setupBottomNavigation(view)
        } catch (e: Exception) {
            Log.e("UserProfileViewFragment", "Error in onViewCreated: ${e.message}")
            Toast.makeText(context, "Error al cargar el perfil de usuario", Toast.LENGTH_SHORT).show()
            // Navigate back if there's a critical error
            findNavController().navigateUp()
        }
    }

    private fun setupBottomNavigation(view: View) {
        // Home Button
        val homeNavLayout = view.findViewById<LinearLayout>(R.id.homeNavLayout)
        homeNavLayout?.setOnClickListener {
            findNavController().navigate(R.id.action_userProfileViewFragment_to_videoHomeFragment)
        }

        // Explore Button
        val exploreButton = view.findViewById<LinearLayout>(R.id.exploreButton)
        exploreButton?.setOnClickListener {
            findNavController().navigate(R.id.action_userProfileViewFragment_to_exploreFragment)
        }

        // Add/Upload Button
        val goToHomeButton = view.findViewById<ImageButton>(R.id.goToHomeButton)
        goToHomeButton?.setOnClickListener {
            findNavController().navigate(R.id.action_userProfileViewFragment_to_contentUploadFragment)
        }

        // Activity Button
        val activityButton = view.findViewById<LinearLayout>(R.id.activityButton)
        activityButton?.setOnClickListener {
            findNavController().navigate(R.id.action_userProfileViewFragment_to_notificacionesFragment)
        }

        // Profile Button
        val profileNavButton = view.findViewById<LinearLayout>(R.id.profileNavButton)
        profileNavButton?.setOnClickListener {
            findNavController().navigate(R.id.action_userProfileViewFragment_to_profileFragment)
        }

        // Admin Button - Check if this should navigate to homeFragment
        val goToAdminButton = view.findViewById<LinearLayout>(R.id.goToAdminButton)
        goToAdminButton?.setOnClickListener {
            findNavController().navigate(R.id.action_userProfileViewFragment_to_homeFragment)
        }
    }

    private fun initializeViews(view: View) {
        try {
            // Use safe property assignment with null checks
            userAvatarImageView = view.findViewById(R.id.userAvatarImageView) ?: throw NullPointerException("userAvatarImageView not found")
            usernameTextView = view.findViewById(R.id.usernameTextView) ?: throw NullPointerException("usernameTextView not found")
            coursesCountTextView = view.findViewById(R.id.coursesCountTextView) ?: throw NullPointerException("coursesCountTextView not found")
            videosCountTextView = view.findViewById(R.id.videosCountTextView) ?: throw NullPointerException("videosCountTextView not found")
            subscribersCountTextView = view.findViewById(R.id.subscribersCountTextView) ?: throw NullPointerException("subscribersCountTextView not found")
            coursesFilterButton = view.findViewById(R.id.coursesFilterButton) ?: throw NullPointerException("coursesFilterButton not found")
            videosFilterButton = view.findViewById(R.id.videosFilterButton) ?: throw NullPointerException("videosFilterButton not found")
            contentRecyclerView = view.findViewById(R.id.contentRecyclerView) ?: throw NullPointerException("contentRecyclerView not found")
            emptyStateTextView = view.findViewById(R.id.emptyStateTextView) ?: throw NullPointerException("emptyStateTextView not found")

        } catch (e: Exception) {
            // Log the error and handle it gracefully
            Log.e("UserProfileViewFragment", "Error initializing views: ${e.message}")
            Toast.makeText(context, "Error al cargar la interfaz del perfil", Toast.LENGTH_SHORT).show()
            // If in a critical error state, navigate back
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        try {
            // Configurar adaptador para cursos
            contentAdapter = CreatedCourseAdapter(
                requireContext(),
                allContent,
                onCourseClickListener = { course ->
                    handleContentClick(course)
                }
            )

            // Configurar adaptador para videos con estilo YouTube
            videoAdapter = YouTubeStyleVideoAdapter(
                requireContext(),
                mutableListOf(),
                onVideoClickListener = { video ->
                    handleVideoClick(video)
                }
            )

            contentRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = contentAdapter // Iniciar con adaptador de cursos

                // Agregar ScrollListener para optimizar la reproducción (similar a ExploreFragment)
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            // Cuando el scroll se detiene, asegurar que las miniaturas estén cargadas
                            Log.d("UserProfileView", "Scroll stopped, refreshing thumbnails")
                            ensureThumbnailsLoaded()
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("UserProfileViewFragment", "Error setting up RecyclerView: ${e.message}")
            // Handle the error gracefully, possibly showing a message
            try {
                emptyStateTextView.text = "Error al cargar contenido"
                emptyStateTextView.visibility = View.VISIBLE
                contentRecyclerView.visibility = View.GONE
            } catch (e2: Exception) {
                Log.e("UserProfileViewFragment", "Could not show error state: ${e2.message}")
            }
        }
    }

    private fun setupFilterButtons() {
        coursesFilterButton.setOnClickListener {
            setFilter(ContentType.COURSE)
        }

        videosFilterButton.setOnClickListener {
            setFilter(ContentType.VIDEO)
        }
    }    private fun setFilter(filterType: ContentType) {
        // Exit search mode if active
        if (isSearchMode) {
            isSearchMode = false
        }

        currentFilter = filterType
        Log.d("UserProfileView", "Filter changed to: $filterType")
        updateFilterButtonsUI()
        filterContent()
    }private fun updateFilterButtonsUI() {
        when (currentFilter) {
            ContentType.COURSE -> {
                coursesFilterButton.setBackgroundResource(R.drawable.filter_button_selected)
                coursesFilterButton.setTextColor(requireContext().getColor(android.R.color.black))
                videosFilterButton.setBackgroundResource(R.drawable.filter_button_unselected)
                videosFilterButton.setTextColor(requireContext().getColor(android.R.color.white))
            }
            ContentType.VIDEO -> {
                videosFilterButton.setBackgroundResource(R.drawable.filter_button_selected)
                videosFilterButton.setTextColor(requireContext().getColor(android.R.color.black))
                coursesFilterButton.setBackgroundResource(R.drawable.filter_button_unselected)
                coursesFilterButton.setTextColor(requireContext().getColor(android.R.color.white))
            }
        }
    }    private fun filterContent() {
        // If in search mode, don't change the search results view
        if (isSearchMode) {
            return
        }

        val filteredContent = when (currentFilter) {
            ContentType.COURSE -> allCourses
            ContentType.VIDEO -> allVideos
        }

        Log.d("UserProfileView", "Filtering content - Type: $currentFilter, Count: ${filteredContent.size}")

        // Cambiar adaptador según el tipo de contenido
        when (currentFilter) {
            ContentType.COURSE -> {
                allContent.clear()
                allContent.addAll(filteredContent)
                contentRecyclerView.adapter = contentAdapter
                contentAdapter.updateCourses(allContent)

                // Asegurar que las miniaturas se carguen correctamente
                contentAdapter.notifyDataSetChanged()

                Log.d("UserProfileView", "Updated course adapter with ${allContent.size} courses")
            }
            ContentType.VIDEO -> {
                contentRecyclerView.adapter = videoAdapter
                videoAdapter.updateVideos(filteredContent)

                // Asegurar que las miniaturas se carguen correctamente
                videoAdapter.notifyDataSetChanged()

                Log.d("UserProfileView", "Updated video adapter with ${filteredContent.size} videos")
            }
        }

        // Mostrar mensaje de estado vacío si no hay contenido
        if (filteredContent.isEmpty()) {
            emptyStateTextView.visibility = View.VISIBLE
            emptyStateTextView.text = when (currentFilter) {
                ContentType.COURSE -> "Este usuario no tiene cursos disponibles"
                ContentType.VIDEO -> "Este usuario no tiene videos disponibles"
            }
            contentRecyclerView.visibility = View.GONE
        } else {
            emptyStateTextView.visibility = View.GONE
            contentRecyclerView.visibility = View.VISIBLE
        }
    }private fun loadUserData(username: String) {
        lifecycleScope.launch {
            try {
                val database = AppDatabase.getDatabase(requireContext())

                // Cargar información del usuario y persona usando los métodos correctos
                val user = withContext(Dispatchers.IO) {
                    database.usuarioDao().getUsuarioByUsername(username)
                }

                val persona = user?.let {
                    withContext(Dispatchers.IO) {
                        database.personaDao().getPersonaById(it.personaId)
                    }
                }

                // Actualizar UI con información del usuario
                withContext(Dispatchers.Main) {
                    // Mostrar el nombre completo de la persona si existe, sino el username
                    if (persona != null && persona.nombres.isNotEmpty()) {
                        usernameTextView.text = "${persona.nombres} ${persona.apellidos}".trim()
                    } else {
                        usernameTextView.text = username
                    }

                    // Cargar avatar del usuario
                    loadUserAvatar(persona)
                }

                // NUEVO: Cargar cantidad de subscriptores
                loadSubscribersCount(username)

                // Cargar contenido del usuario
                loadUserContent(username)
                
            } catch (e: Exception) {
                e.printStackTrace()
                // Mostrar error o datos por defecto
                withContext(Dispatchers.Main) {
                    usernameTextView.text = username
                    userAvatarImageView.setImageResource(R.drawable.ic_profile)
                    subscribersCountTextView.text = "0"
                    showEmptyState()
                }
            }
        }
    }

    private suspend fun loadSubscribersCount(username: String) {
        try {
            val database = AppDatabase.getDatabase(requireContext())
            val count = withContext(Dispatchers.IO) {
                database.subscriptionDao().getSubscriptionCountForCreator(username)
            }
            withContext(Dispatchers.Main) {
                subscribersCountTextView.text = count.toString()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                subscribersCountTextView.text = "0"
            }
        }
    }

    private fun loadUserAvatar(persona: Persona?) {
        try {
            if (persona?.avatar != null && persona.avatar.isNotEmpty()) {
                val avatarPath = persona.avatar
                // Verificar si es una ruta de archivo válida
                if (avatarPath.startsWith("/") || avatarPath.startsWith("file://")) {
                    val file: File = if (avatarPath.startsWith("file://")) {
                        File(avatarPath.removePrefix("file://"))
                    } else {
                        File(avatarPath)
                    }
                    
                    if (file.exists() && file.canRead()) {
                        Glide.with(this@UserProfileViewFragment)
                            .load(file)
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile)
                            .circleCrop()
                            .into(userAvatarImageView)
                    } else {
                        // Si el archivo no existe, usar imagen por defecto
                        userAvatarImageView.setImageResource(R.drawable.ic_profile)
                    }
                } else {
                    // Si es una URI o URL, cargar directamente
                    Glide.with(this@UserProfileViewFragment)
                        .load(avatarPath)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .circleCrop()
                        .into(userAvatarImageView)
                }
            } else {
                // Si no hay avatar, usar imagen por defecto
                userAvatarImageView.setImageResource(R.drawable.ic_profile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // En caso de error, usar imagen por defecto
            userAvatarImageView.setImageResource(R.drawable.ic_profile)
        }
    }    private suspend fun loadUserContent(username: String) {
        try {
            // Obtener todos los datos usando EXACTAMENTE la misma lógica que ExploreFragment
            val allVideosData = getAllContentLikeExploreFragment()

            // Filtrar el contenido específico del usuario usando la lógica consistente
            val (userCourses, userVideos) = filterContentLikeExploreFragment(allVideosData, username)

            withContext(Dispatchers.Main) {
                // Actualizar las listas con los datos filtrados del usuario
                allCourses.clear()
                allCourses.addAll(userCourses)
                allVideos.clear() 
                allVideos.addAll(userVideos)
                
                // Actualizar contadores en la UI
                coursesCountTextView.text = userCourses.size.toString()
                videosCountTextView.text = userVideos.size.toString()
                
                // Aplicar el filtro actual para mostrar el contenido correcto
                filterContent()
                
                // Asegurar que las miniaturas se carguen correctamente
                ensureThumbnailsLoaded()
                
                Log.d("UserProfileView", "Loaded content for user: $username - Courses: ${userCourses.size}, Videos: ${userVideos.size}")
            }
        } catch (e: Exception) {
            Log.e("UserProfileView", "Error loading user content for: $username", e)
            withContext(Dispatchers.Main) {
                showEmptyState()
            }
        }
    }    private fun showEmptyState() {
        allContent.clear()
        allCourses.clear()
        allVideos.clear()
        coursesCountTextView.text = "0"
        videosCountTextView.text = "0"
        filterContent()
    }    private fun handleContentClick(content: VideoData) {
        // Verificar si el click es en un curso o video basándose en el filtro actual
        when (currentFilter) {
            ContentType.COURSE -> {
                // Navegar al detalle del curso
                val bundle = Bundle().apply {
                    putLong("courseId", content.id)
                    putString("courseName", content.title)
                }
                findNavController().navigate(R.id.action_userProfileViewFragment_to_courseDetailFragment, bundle)
            }
            ContentType.VIDEO -> {
                // Navegar al VideoHomeFragment con el video específico
                val bundle = Bundle().apply {
                    putLong("videoId", content.id)
                    putString("videoTitle", content.title)
                    putString("videoUsername", content.username)
                }
                findNavController().navigate(R.id.action_userProfileViewFragment_to_videoHomeFragment, bundle)
            }
        }
    }

    private fun handleVideoClick(video: VideoData) {
        // Navegar al VideoHomeFragment con el video específico
        val bundle = Bundle().apply {
            putLong("videoId", video.id)
            putString("videoTitle", video.title)
            putString("videoUsername", video.username)
        }
        findNavController().navigate(R.id.action_userProfileViewFragment_to_videoHomeFragment, bundle)
    }

    override fun onResume() {
        super.onResume()
        // Cargar datos del usuario
        username?.let { loadUserData(it) }
    }    override fun onPause() {
        super.onPause()
        // Stop any video playback when fragment is paused
        if (::contentAdapter.isInitialized) {
            contentAdapter.stopAllVideos()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up any resources
        if (::contentAdapter.isInitialized) {
            contentAdapter.stopAllVideos()
        }
    }
    
    // Método auxiliar que replica la lógica de ExploreFragment para obtener contenido
    private suspend fun getAllContentLikeExploreFragment(): List<VideoData> {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(requireContext())
            // Usar exactamente el mismo método que ExploreFragment
            database.videoDao().getAllVideos()
        }
    }
    
    // Método auxiliar para filtrar contenido como lo hace ExploreFragment
    private fun filterContentLikeExploreFragment(
        allContent: List<VideoData>, 
        targetUsername: String
    ): Pair<List<VideoData>, List<VideoData>> {
        // Filtrar por el usuario específico
        val userContent = allContent.filter { video ->
            video.username.equals(targetUsername, ignoreCase = true)
        }
        
        Log.d("UserProfileView", "User content filtered for $targetUsername: ${userContent.size} items")
        
        // Log de miniaturas disponibles para debugging
        userContent.forEach { video ->
            val hasThumbnail = !video.thumbnailUri.isNullOrEmpty()
            val hasLocalFile = !video.localFilePath.isNullOrEmpty()
            val hasVideoUri = !video.videoUriString.isNullOrEmpty()
            
            Log.d("UserProfileView", "Video ${video.id} - '${video.title}': " +
                    "Has Thumbnail: $hasThumbnail, " +
                    "Has LocalFile: $hasLocalFile, " +
                    "Has VideoUri: $hasVideoUri")
        }
        
        // Separar cursos y videos usando lógica similar a ExploreFragment
        // En ExploreFragment, todos los elementos se consideran cursos para el RecyclerView principal
        val courses = userContent
        
        // Los videos pueden ser un subconjunto de los cursos o tener lógica diferente
        val videos = userContent.filter { video ->
            // Criterio para determinar qué es un video vs curso
            // Ajusta esta lógica según las necesidades de tu aplicación
            video.localFilePath?.isNotEmpty() == true &&
            !video.description.contains("curso", ignoreCase = true)
        }
        
        return Pair(courses, videos)
    }

    // Método público para recargar el contenido del usuario (puede ser llamado externamente)
    fun refreshUserContent() {
        username?.let { loadUserData(it) }
    }
    
    // Método para asegurar que se carguen las miniaturas correctamente en ambos adaptadores
    private fun ensureThumbnailsLoaded() {
        Log.d("UserProfileView", "Ensuring thumbnails are loaded correctly")
        
        // Actualizar el adapter si está inicializado y hay contenido disponible
        if (::contentAdapter.isInitialized && allContent.isNotEmpty()) {
            contentAdapter.notifyDataSetChanged()
        }
        
        if (::videoAdapter.isInitialized && allVideos.isNotEmpty()) {
            videoAdapter.notifyDataSetChanged()
        }
    }
    
    // Método auxiliar para verificar la validez de las URIs (como en CreatedCourseAdapter)
    private fun isValidUri(uriString: String?): Boolean {
        if (uriString.isNullOrEmpty()) return false
        
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme?.lowercase()) {
                "file" -> {
                    // Check if file exists and is readable
                    val file = File(uri.path ?: "")
                    file.exists() && file.canRead()
                }
                "content" -> {
                    // Only allow specific content providers, avoid Google Drive URIs
                    val authority = uri.authority
                    authority != null && 
                    !authority.contains("com.google.android.apps.docs") &&
                    !authority.contains("com.google.android.apps.drive")
                }
                "android.resource" -> true
                "http", "https" -> true
                else -> false
            }
        } catch (e: Exception) {
            Log.e("UserProfileView", "Invalid URI: $uriString", e)
            false
        }
    }
    
    // Obtener nombre de usuario actual desde SharedPreferences
    private fun getCurrentUsername(): String? {
        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        return sharedPreferences.getString("current_username", null)
    }
}