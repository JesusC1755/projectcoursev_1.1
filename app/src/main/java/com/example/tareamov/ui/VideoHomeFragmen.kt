package com.example.tareamov.ui

import android.content.Context
import android.content.Intent // Add this import for Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView  // Add this import for ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tareamov.R
import com.example.tareamov.adapter.VideoAdapter
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.Persona
import com.example.tareamov.data.entity.VideoData
import com.example.tareamov.util.VideoManager
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import java.io.File
// Add this import if it's missing, for Usuario.ROL_ADMIN
import com.example.tareamov.data.entity.Usuario
// Import SessionManager
import com.example.tareamov.util.SessionManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.MediaPlayer // Required for MediaPlayer interactions if direct

class VideoHomeFragment : Fragment() {
    private lateinit var profileAvatars: CircleImageView
    private lateinit var profileButton: CircleImageView
    private lateinit var usernameText: TextView
    private lateinit var videoManager: VideoManager
    private lateinit var sessionManager: SessionManager // Add SessionManager instance

    private lateinit var homeIconImageView: ImageView
    private lateinit var exploreIconImageView: ImageView
    private lateinit var activityIconImageView: ImageView
    private lateinit var profileIconImageView: ImageView

    private var isLiked = false
    private var isMuted = false


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video_home, container, false)
    }

    // List to store video data (in a real app, this would come from a database or API)
    private val videoList = mutableListOf<VideoData>()
    private var currentVideoIndex = 0
    private lateinit var videoAdapter: VideoAdapter

    // In the onViewCreated method, update the goToHomeButton click listener
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize VideoManager
        videoManager = VideoManager(requireContext())
        sessionManager = SessionManager.getInstance(requireContext()) // Initialize SessionManager

        // Obtener parámetros de navegación para video específico
        val videoId = arguments?.getLong("videoId", -1L) ?: -1L
        val videoTitle = arguments?.getString("videoTitle")
        val videoUsername = arguments?.getString("videoUsername")

        // Initialize views
        profileAvatars = view.findViewById(R.id.profileAvatars)
        profileButton = view.findViewById(R.id.profileButton)
        usernameText = view.findViewById(R.id.usernameText)

        // Initialize bottom navigation icons
        homeIconImageView = view.findViewById(R.id.homeIconImageView)
        exploreIconImageView = view.findViewById(R.id.exploreIconImageView)
        activityIconImageView = view.findViewById(R.id.activityIconImageView)
        profileIconImageView = view.findViewById(R.id.profileIconImageView)

        // Setup initial colors for bottom navigation icons
        setupBottomNavigationIconColors()

        // Initialize like and sound buttons
        val likeButton = view.findViewById<ImageView>(R.id.likeButton)
        val soundButton = view.findViewById<ImageView>(R.id.soundButton)
        val shareButton = view.findViewById<ImageView>(R.id.shareButton) // Initialize shareButton

        // Set initial tints
        likeButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        soundButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        shareButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) // Set initial tint for shareButton


        likeButton.setOnClickListener {
            isLiked = !isLiked
            if (isLiked) {
                likeButton.setColorFilter(Color.parseColor("#9C27B0"), PorterDuff.Mode.SRC_IN) // Purple
            } else {
                likeButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) // White
            }
        }

        soundButton.setOnClickListener {
            isMuted = !isMuted
            val viewPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.videoViewPager)
            val recyclerView = viewPager.getChildAt(0) as? RecyclerView
            val currentViewHolder = recyclerView?.findViewHolderForAdapterPosition(currentVideoIndex) as? VideoAdapter.VideoViewHolder
            currentViewHolder?.setMuteState(isMuted)

            if (isMuted) {
                soundButton.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN) // Gray when muted
            } else {
                soundButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) // White when not muted
            }
        }

        shareButton.setOnClickListener {
            val currentVideo = videoList.getOrNull(currentVideoIndex)
            if (currentVideo?.localFilePath != null) {
                val videoFile = File(currentVideo.localFilePath)
                if (videoFile.exists()) {
                    val videoUri: Uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.provider", // Make sure this matches your FileProvider authority
                        videoFile
                    )

                    val shareText = "Mira este video: ${currentVideo.title}\\n${currentVideo.description}"

                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, videoUri)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        putExtra(Intent.EXTRA_SUBJECT, currentVideo.title) // Optional: for email apps
                        type = requireContext().contentResolver.getType(videoUri) ?: "video/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val shareIntent = Intent.createChooser(sendIntent, "Compartir video vía")
                    startActivity(shareIntent)
                } else {
                    Toast.makeText(requireContext(), "Archivo de video no encontrado.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "No hay video para compartir o ruta no válida.", Toast.LENGTH_SHORT).show()
            }
        }


        // Change this block to navigate to LoginFragment using Navigation Component
        view.findViewById<ImageView>(R.id.coursesButton)?.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }

        // Add this block to navigate to CourseDetailFragment when username is clicked
        usernameText.setOnClickListener {
            // Get the current video (or course) data
            val currentVideo = videoList.getOrNull(currentVideoIndex)
            if (currentVideo != null) {
                // Pass the courseId (or another identifier) as argument
                val bundle = Bundle().apply {
                    putLong("courseId", currentVideo.id) // Adjust if your VideoData has a courseId field
                }
                findNavController().navigate(R.id.action_videoHomeFragment_to_courseDetailFragment, bundle)
            } else {
                Toast.makeText(requireContext(), "No course information available", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up database orbit button click to navigate to DatabaseQueryFragment
        view.findViewById<ImageView>(R.id.databaseOrbitButton)?.setOnClickListener {
            findNavController().navigate(R.id.action_videoHomeFragment_to_databaseQueryFragment)
        }

        // Start the animated vector drawable for the orbit icon
        val databaseOrbitButton = view.findViewById<ImageView>(R.id.databaseOrbitButton)
        val drawable = databaseOrbitButton.drawable
        if (drawable is android.graphics.drawable.AnimatedVectorDrawable) {
            drawable.start()
        }

        // Set up profile button click to navigate to the viewed user's profile
        profileButton.setOnClickListener {
            val currentVideo = videoList.getOrNull(currentVideoIndex)
            val username = currentVideo?.username
            if (!username.isNullOrEmpty()) {
                val bundle = Bundle().apply {
                    putString("username", username)
                }
                findNavController().navigate(R.id.userProfileViewFragment, bundle)
            } else {
                Toast.makeText(requireContext(), "No se pudo obtener el usuario del video", Toast.LENGTH_SHORT).show()
            }
        }

        // Also set up the profile avatars in the top bar to navigate to profile
        profileAvatars.setOnClickListener {
            navigateToProfileSafely()
        }

        // Add this code to handle the bottom navigation profile button click
        val profileNavButton = view.findViewById<LinearLayout>(R.id.profileNavButton)
        profileNavButton?.setOnClickListener {
            navigateToProfileSafely()
        }

        // Set up button to navigate to the content upload screen
        view.findViewById<ImageButton>(R.id.goToHomeButton)?.setOnClickListener {
            // Navigate to ContentUploadFragment first to select a video
            findNavController().navigate(R.id.action_videoHomeFragment_to_contentUploadFragment)
        }

        // Set up Explorar button to navigate to ExploreFragment
        val exploreButton = view.findViewById<LinearLayout>(R.id.exploreButton)
        exploreButton?.setOnClickListener {
            findNavController().navigate(R.id.action_videoHomeFragment_to_exploreFragment)
        }

        // Set up Activity button to navigate to NotificacionesFragment
        val activityButton = view.findViewById<LinearLayout>(R.id.activityButton)
        activityButton?.setOnClickListener {
            findNavController().navigate(R.id.action_videoHomeFragment_to_notificacionesFragment)
        }        // Mostrar el botón de admin solo si el usuario es admin
        val goToAdminButton = view.findViewById<LinearLayout>(R.id.goToAdminButton)
        
        // Check if the current user is admin
        checkAdminStatus { isAdmin ->
            if (isAdmin) {
                goToAdminButton?.visibility = View.VISIBLE
                goToAdminButton?.setOnClickListener {
                    Log.d("VideoHomeFragment", "Admin button clicked, navigating to HomeFragment")
                    findNavController().navigate(R.id.action_videoHomeFragment_to_homeFragment)
                }
            } else {
                goToAdminButton?.visibility = View.GONE
            }
        }        // Load the current user's avatar
        loadCurrentUserAvatar()

        // Load sample videos or recently uploaded videos
        loadVideos(videoId, videoTitle, videoUsername)

        // Inicializar el adaptador de videos y configurar el ViewPager2
        setupVideoViewPager(view)
    }

    // REMOVE the checkCurrentUserAdminStatus() function as it's no longer needed
    // private suspend fun checkCurrentUserAdminStatus(): Boolean { ... }

    private fun setupBottomNavigationIconColors() {
        // Active color (Purple)
        val activeColor = Color.parseColor("#9C27B0")
        // Inactive color (White)
        val inactiveColor = Color.parseColor("#FFFFFF")

        // Set "Inicio" to active (purple), others to inactive (white)
        homeIconImageView.setColorFilter(activeColor, PorterDuff.Mode.SRC_IN)
        exploreIconImageView.setColorFilter(inactiveColor, PorterDuff.Mode.SRC_IN)
        activityIconImageView.setColorFilter(inactiveColor, PorterDuff.Mode.SRC_IN)
        profileIconImageView.setColorFilter(inactiveColor, PorterDuff.Mode.SRC_IN)
    }

    private fun setupVideoViewPager(view: View) {
        // Inicializar el adaptador con la lista de videos
        videoAdapter = VideoAdapter(videoList)

        // Configurar el ViewPager2
        val viewPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.videoViewPager)
        viewPager.adapter = videoAdapter

        // Configurar orientación vertical para deslizar como TikTok
        viewPager.orientation = androidx.viewpager2.widget.ViewPager2.ORIENTATION_VERTICAL

        // Desactivar el overscroll effect (el efecto de rebote al final de la lista)
        viewPager.getChildAt(0).overScrollMode = View.OVER_SCROLL_NEVER        // Listener para cambios de página
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentVideoIndex = position

                // Pausar todos los videos y reproducir solo el actual
                val viewHolder = (viewPager.getChildAt(0) as RecyclerView)
                    .findViewHolderForAdapterPosition(position) as? VideoAdapter.VideoViewHolder
                viewHolder?.playVideo()
                viewHolder?.setMuteState(isMuted) // Apply current mute state

                // Actualizar la información en pantalla
                displayVideo(videoList[position])
            }
        })
    }

    // Update these methods to work with ViewPager2
    private fun showNextVideo() {
        if (currentVideoIndex < videoList.size - 1) {
            currentVideoIndex++
            view?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.videoViewPager)?.currentItem = currentVideoIndex
        }
    }

    private fun showPreviousVideo() {
        if (currentVideoIndex > 0) {
            currentVideoIndex--
            view?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.videoViewPager)?.currentItem = currentVideoIndex
        }
    }

    private fun displayVideo(videoData: VideoData) {
        usernameText.text = videoData.username
        view?.findViewById<TextView>(R.id.videoDescription)?.text = videoData.description

        // Always use the local file path if available
        val videoPath = videoData.localFilePath
        if (videoPath != null && File(videoPath).exists()) {
            // Use this path for playback (e.g., setVideoPath or ExoPlayer)
            Log.d("VideoHomeFragment", "Playing video from local file: $videoPath")
        } else {
            Log.w("VideoHomeFragment", "No local file for video, cannot play after restart: ${videoData.videoUriString}")
        }

        // --- NUEVO BLOQUE: Cargar avatar de la persona asociada al usuario del video ---
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                val persona = withContext(Dispatchers.IO) {
                    db.personaDao().getPersonaByUsername(videoData.username)
                }
                if (persona != null && !persona.avatar.isNullOrEmpty()) {
                    val avatarUri = Uri.parse(persona.avatar)
                    Glide.with(requireContext())
                        .load(avatarUri)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .into(profileButton)
                } else {
                    profileButton.setImageResource(R.drawable.ic_profile)
                }
            } catch (e: Exception) {
                Log.e("VideoHomeFragment", "Error loading video uploader avatar", e)
                profileButton.setImageResource(R.drawable.ic_profile)
            }
        }
        // --- FIN DEL BLOQUE NUEVO ---
    }    private fun loadVideos(targetVideoId: Long = -1L, targetVideoTitle: String? = null, targetVideoUsername: String? = null) {
        // Limpiar la lista actual
        videoList.clear()

        // Cargar videos desde la base de datos usando VideoManager
        lifecycleScope.launch {
            try {
                Log.d("VideoHomeFragment", "Starting to load videos from database")
                
                // Obtener todos los videos de la base de datos
                val savedVideos = videoManager.getAllVideos()
                Log.d("VideoHomeFragment", "Retrieved ${savedVideos.size} videos from database")

                // Log each video for debugging
                savedVideos.forEachIndexed { index, video ->
                    Log.d("VideoHomeFragment", "Video $index: ID=${video.id}, title='${video.title}', username='${video.username}', localPath='${video.localFilePath}', uriString='${video.videoUriString}'")
                }

                // Filtrar videos válidos y que NO sean por defecto
                val playableVideos = savedVideos.filter { video ->
                    val hasLocalFile = video.localFilePath != null && File(video.localFilePath).exists()
                    val isNotDefaultTitle = !video.title.equals("mi video", ignoreCase = true) && 
                                          !video.title.equals("movideo", ignoreCase = true)
                    
                    Log.d("VideoHomeFragment", "Video '${video.title}': hasLocalFile=$hasLocalFile, isNotDefaultTitle=$isNotDefaultTitle")
                    
                    // For debugging, let's be less restrictive initially
                    hasLocalFile || !video.videoUriString.isNullOrEmpty()
                }
                
                videoList.addAll(playableVideos)
                Log.d("VideoHomeFragment", "Loaded ${playableVideos.size} playable videos from database")

                // Notificar al adaptador que los datos han cambiado
                withContext(Dispatchers.Main) {
                    if (::videoAdapter.isInitialized) {
                        videoAdapter.updateVideos(videoList)
                        Log.d("VideoHomeFragment", "Updated video adapter with ${videoList.size} videos")
                    }

                    // Si hay un video específico solicitado, intentar navegar a él
                    if (targetVideoId != -1L && videoList.isNotEmpty()) {
                        val targetIndex = videoList.indexOfFirst { it.id == targetVideoId }
                        if (targetIndex != -1) {
                            currentVideoIndex = targetIndex
                            navigateToVideoIndex(targetIndex)
                            Log.d("VideoHomeFragment", "Navigated to target video at index $targetIndex")
                        } else {
                            // Si no se encuentra por ID, intentar por título y usuario
                            val fallbackIndex = videoList.indexOfFirst { 
                                it.title == targetVideoTitle && it.username == targetVideoUsername 
                            }
                            if (fallbackIndex != -1) {
                                currentVideoIndex = fallbackIndex
                                navigateToVideoIndex(fallbackIndex)
                                Log.d("VideoHomeFragment", "Navigated to fallback video at index $fallbackIndex")
                            } else {
                                // Si no se encuentra el video específico, mostrar el primero
                                displayVideo(videoList[0])
                                Log.d("VideoHomeFragment", "Target video not found, displaying first video")
                            }
                        }
                    } else if (videoList.isNotEmpty()) {
                        // Display the first video if available and no specific video requested
                        displayVideo(videoList[0])
                        Log.d("VideoHomeFragment", "Displaying first video: ${videoList[0].title}")
                    } else {
                        Log.w("VideoHomeFragment", "No videos available to display")
                    }
                }

            } catch (e: Exception) {
                Log.e("VideoHomeFragment", "Error loading videos", e)
                e.printStackTrace()
            }
        }
    }    override fun onResume() {
        super.onResume()
        loadVideos() // Recargar videos al volver al fragmento
    }

    private fun getCurrentUsername(): String? {
        val sharedPreferences = requireActivity().getSharedPreferences(
            "auth_prefs", Context.MODE_PRIVATE
        )
        return sharedPreferences.getString("current_username", null)
    }

    // Add this missing method if it doesn't exist
    // This method loads the avatar of the session user into profileAvatars
    private fun loadCurrentUserAvatar() {
        // Ensure fragment is added and context is available before proceeding
        if (!isAdded || context == null) {
            Log.w("VideoHomeFragment", "loadCurrentUserAvatar: Fragment not added or context is null.")
            if (::profileAvatars.isInitialized) {
                profileAvatars.setImageResource(R.drawable.ic_profile_avatars)
            }
            return
        }

        try {
            if (!::sessionManager.isInitialized) {
                Log.e("VideoHomeFragment", "SessionManager not initialized in loadCurrentUserAvatar")
                if (::profileAvatars.isInitialized) {
                     profileAvatars.setImageResource(R.drawable.ic_profile_avatars)
                }
                return
            }

            val avatarUriString = sessionManager.getUserAvatar()
            if (!avatarUriString.isNullOrEmpty()) {
                val avatarUri = Uri.parse(avatarUriString)
                if (::profileAvatars.isInitialized) {
                    Glide.with(requireContext())
                        .load(avatarUri)
                        .placeholder(R.drawable.ic_profile_avatars)
                        .error(R.drawable.ic_profile_avatars)
                        .into(profileAvatars)
                    Log.d("VideoHomeFragment", "Current user avatar loaded from session: $avatarUriString")
                } else {
                     Log.e("VideoHomeFragment", "profileAvatars not initialized in loadCurrentUserAvatar")
                }
            } else {
                if (::profileAvatars.isInitialized) {
                    profileAvatars.setImageResource(R.drawable.ic_profile_avatars)
                }
                Log.d("VideoHomeFragment", "Current user avatar not found in session or URI is empty, using default.")
            }
        } catch (e: IllegalArgumentException) {
            Log.e("VideoHomeFragment", "Error parsing avatar URI in loadCurrentUserAvatar: ${e.message}", e)
            if (::profileAvatars.isInitialized) {
                profileAvatars.setImageResource(R.drawable.ic_profile_avatars)
            }
        } catch (e: Exception) {
            Log.e("VideoHomeFragment", "Error in loadCurrentUserAvatar: ${e.message}", e)
            if (::profileAvatars.isInitialized) {
                profileAvatars.setImageResource(R.drawable.ic_profile_avatars)
            }
        }
    }

    private fun loadAvatarIntoViews(persona: Persona) {
        if (!persona.avatar.isNullOrEmpty()) {
            try {
                val avatarUri = Uri.parse(persona.avatar)
                Log.d("VideoHomeFragment", "Loading avatar from URI for profileButton: $avatarUri")

                // Check if fragment is still attached to context
                if (!isAdded || context == null) {
                    Log.e("VideoHomeFragment", "Fragment not attached or context is null in loadAvatarIntoViews")
                    return
                }

                // This method should only update profileButton (uploader's avatar)
                // The profileAvatars (logged-in user) is handled by loadCurrentUserAvatar()
                try {
                    // Load avatar into profileButton
                    Glide.with(requireContext())
                        .load(avatarUri)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .into(profileButton)
                    Log.d("VideoHomeFragment", "Avatar loaded successfully into profileButton")
                } catch (e: Exception) {
                    Log.e("VideoHomeFragment", "Error loading avatar into profileButton", e)
                    if (::profileButton.isInitialized) {
                        profileButton.setImageResource(R.drawable.ic_profile)
                    }
                }

            } catch (e: IllegalArgumentException) {
                Log.e("VideoHomeFragment", "Error parsing avatar URI in loadAvatarIntoViews", e)
                if (::profileButton.isInitialized) {
                    profileButton.setImageResource(R.drawable.ic_profile)
                }
            } catch (e: Exception) {
                Log.e("VideoHomeFragment", "Error loading avatar image in loadAvatarIntoViews", e)
                if (::profileButton.isInitialized) {
                    profileButton.setImageResource(R.drawable.ic_profile) // Set default for profileButton on error
                }
            }
        } else {
            Log.d("VideoHomeFragment", "No avatar found for persona, using default for profileButton")
            if (::profileButton.isInitialized) {
                profileButton.setImageResource(R.drawable.ic_profile) // Set default for profileButton if no avatar
            }
        }
    }

    // Add this method to handle content URIs
    private fun getFilePathFromUri(uri: Uri): String? {
        try {
            if (uri.scheme == "content") {
                val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndexOrThrow("_data")
                        if (columnIndex >= 0) {
                            return it.getString(columnIndex)
                        }
                    }
                }

                // If we couldn't get the path from the cursor, try to copy the file to app's cache
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val fileName = "video_${System.currentTimeMillis()}.mp4"
                    val cacheFile = File(requireContext().cacheDir, fileName)

                    inputStream.use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    Log.d("VideoHomeFragment", "Copied content URI to cache: ${cacheFile.absolutePath}")
                    return cacheFile.absolutePath
                }
            } else if (uri.scheme == "file") {
                return uri.path
            }
        } catch (e: Exception) {
            Log.e("VideoHomeFragment", "Error getting file path from URI", e)
        }
        return null
    }

    // Add this method to modify the VideoData to use file paths instead of URIs when possible
    // Update the prepareVideoForPlayback method to better handle file paths
    private fun prepareVideoForPlayback(videoData: VideoData): VideoData {
        if (videoData.videoUriString != null) {
            try {
                val uri = Uri.parse(videoData.videoUriString)

                // If it's already a file URI and the file exists, use it as is
                if (uri.scheme == "file") {
                    val path = uri.path
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) {
                            Log.d("VideoHomeFragment", "Using existing file path: $path")
                            return videoData
                        } else {
                            Log.e("VideoHomeFragment", "File does not exist: $path")
                        }
                    }
                }

                // For content URIs, try to get a persistent file path
                val filePath = getFilePathFromUri(uri)

                if (filePath != null) {
                    val file = File(filePath)
                    if (file.exists()) {
                        Log.d("VideoHomeFragment", "Using file path from URI: $filePath")
                        // Create a new VideoData with the file path
                        return VideoData(
                            id = videoData.id,
                            username = videoData.username,
                            description = videoData.description,
                            title = videoData.title,
                            videoUriString = "file://$filePath",
                            timestamp = videoData.timestamp
                        )
                    } else {
                        Log.e("VideoHomeFragment", "File does not exist after conversion: $filePath")
                    }
                } else {
                    Log.e("VideoHomeFragment", "Could not get file path from URI: ${videoData.videoUriString}")
                }
            } catch (e: Exception) {
                Log.e("VideoHomeFragment", "Error preparing video for playback", e)
            }
        }
        return videoData
    }

    // Add this method to check if current user is admin and invoke callback with result
    private fun checkAdminStatus(callback: (Boolean) -> Unit) {
        val username = sessionManager.getUsername()
        if (username == null) {
            callback(false)
            return
        }

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                val usuario = withContext(Dispatchers.IO) {
                    db.usuarioDao().getUsuarioByUsername(username)
                }
                
                val isAdmin = usuario?.rol == Usuario.ROL_ADMIN
                Log.d("VideoHomeFragment", "User $username is admin: $isAdmin")
                callback(isAdmin)
            } catch (e: Exception) {
                Log.e("VideoHomeFragment", "Error checking admin status", e)
                callback(false)
            }
        }
    }

    // Add this method to safely navigate to the profile fragment
    private fun navigateToProfileSafely() {
        try {
            // Try to navigate directly to the destination ID
            findNavController().navigate(R.id.profileFragment)
            Log.d("VideoHomeFragment", "Navigated to profile fragment successfully")
        } catch (e: Exception) {
            Log.e("VideoHomeFragment", "Error navigating to profile fragment: ${e.message}")
            // Show a toast to inform the user
            Toast.makeText(context, "No se pudo navegar al perfil", Toast.LENGTH_SHORT).show()
        }    }

    private fun navigateToVideoIndex(index: Int) {
        try {
            val viewPager = view?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.videoViewPager)
            viewPager?.setCurrentItem(index, false) // false para navegación inmediata sin animación
            displayVideo(videoList[index])
        } catch (e: Exception) {
            Log.e("VideoHomeFragment", "Error navigating to video index $index", e)
        }
    }
}