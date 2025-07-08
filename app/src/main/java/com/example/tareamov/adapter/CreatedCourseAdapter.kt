package com.example.tareamov.adapter

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tareamov.R
import com.example.tareamov.data.entity.VideoData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Adaptador para mostrar los cursos creados en el fragmento de exploración
 */
class CreatedCourseAdapter(
    private val context: Context,
    private var courses: List<VideoData>,
    private val onCourseClickListener: (VideoData) -> Unit
) : RecyclerView.Adapter<CreatedCourseAdapter.CourseViewHolder>() {

    private var currentPlayingHolder: CourseViewHolder? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_created_course, parent, false)
        return CourseViewHolder(view)
    }    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        holder.bind(courses[position])
    }    override fun onViewAttachedToWindow(holder: CourseViewHolder) {
        super.onViewAttachedToWindow(holder)
        Log.d("CreatedCourseAdapter", "View attached to window for position: ${holder.adapterPosition}")
        // Iniciar reproducción automática con un pequeño delay
        holder.startAutoPlayWithDelay()
    }

    override fun onViewDetachedFromWindow(holder: CourseViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.stopAutoPlay()
    }

    override fun getItemCount(): Int = courses.size    /**
     * Actualiza la lista de cursos y notifica al adaptador
     */
    fun updateCourses(newCourses: List<VideoData>) {
        // Detener cualquier reproducción actual
        currentPlayingHolder?.stopAutoPlay()
        currentPlayingHolder = null
        
        courses = newCourses
        notifyDataSetChanged()
    }

    /**
     * Detiene todos los videos que se están reproduciendo
     */
    fun stopAllVideos() {
        currentPlayingHolder?.stopAutoPlay()
        currentPlayingHolder = null
    }/**
     * ViewHolder para mostrar un curso individual
     */
    inner class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.courseVideoThumbnail)
        private val videoView: VideoView = itemView.findViewById(R.id.courseVideoView)
        private val titleTextView: TextView = itemView.findViewById(R.id.courseTitleTextView)
        private val studentsTextView: TextView = itemView.findViewById(R.id.courseStudentsTextView)
        private val categoryTextView: TextView = itemView.findViewById(R.id.courseCategoryTextView)
        
        private val handler = Handler(Looper.getMainLooper())
        private var thumbnailRunnable: Runnable? = null
        private var videoRunnable: Runnable? = null
        private var stopVideoRunnable: Runnable? = null
        private var currentCourse: VideoData? = null

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    Log.d("CreatedCourseAdapter", "Course clicked: ID=${courses[position].id}, Title=${courses[position].title}")
                    onCourseClickListener(courses[position])
                }
            }
        }        fun bind(course: VideoData) {
            currentCourse = course
            titleTextView.text = course.title ?: "Curso sin título"
            titleTextView.maxLines = 2
            titleTextView.ellipsize = android.text.TextUtils.TruncateAt.END
            studentsTextView.text = "${(0..1000).random()} estudiantes"
            categoryTextView.text = "Tecnología"

            Log.d("CreatedCourseAdapter", "Binding course: ${course.title} with URI: ${course.videoUriString}")
            
            // Reset views
            videoView.visibility = View.GONE
            thumbnailImageView.visibility = View.VISIBLE
            stopAutoPlay()            // Mostrar la miniatura seleccionada si existe, si no, cargar la miniatura del video o un placeholder
            if (!course.thumbnailUri.isNullOrEmpty()) {
                try {
                    // Usar Glide para cargar el thumbnail de manera más eficiente
                    Glide.with(context)
                        .load(Uri.parse(course.thumbnailUri))
                        .placeholder(R.drawable.placeholder_image)
                        .error(R.drawable.placeholder_image)
                        .centerCrop()
                        .into(thumbnailImageView)
                    
                    Log.d("CreatedCourseAdapter", "Loaded custom thumbnail for: ${course.title}")
                } catch (e: Exception) {
                    Log.e("CreatedCourseAdapter", "Error loading custom thumbnail", e)
                    thumbnailImageView.setImageResource(R.drawable.placeholder_image)
                }
            } else {
                loadVideoThumbnail(course)
            }
        }fun startAutoPlay() {
            currentCourse?.let { course ->
                Log.d("CreatedCourseAdapter", "Attempting to start autoplay for: ${course.title}")
                Log.d("CreatedCourseAdapter", "Video URI: ${course.videoUriString}")
                
                // Solo reproducir si hay un video válido
                if (!course.videoUriString.isNullOrEmpty() && 
                    !course.videoUriString.startsWith("content://media/external/video/dummy_")) {
                    
                    Log.d("CreatedCourseAdapter", "Video URI is valid, starting playback sequence")
                    
                    // Esperar 2 segundos antes de empezar la reproducción
                    thumbnailRunnable = Runnable {
                        startVideoPlayback(course)
                    }
                    handler.postDelayed(thumbnailRunnable!!, 2000)
                } else {
                    Log.d("CreatedCourseAdapter", "Video URI is invalid or dummy: ${course.videoUriString}")
                }
            }
        }

        fun startAutoPlayWithDelay() {
            // Detener cualquier reproducción anterior antes de iniciar nueva
            currentPlayingHolder?.stopAutoPlay()
            
            // Esperar un poco antes de iniciar para asegurar que la vista esté completamente cargada
            handler.postDelayed({
                if (currentPlayingHolder == null) {
                    startAutoPlay()
                }
            }, 500)
        }

        fun stopAutoPlay() {
            // Cancelar todos los runnables
            thumbnailRunnable?.let { handler.removeCallbacks(it) }
            videoRunnable?.let { handler.removeCallbacks(it) }
            stopVideoRunnable?.let { handler.removeCallbacks(it) }
            
            // Detener video si está reproduciéndose
            if (videoView.isPlaying) {
                videoView.stopPlayback()
            }
            
            // Resetear vistas
            videoView.visibility = View.GONE
            thumbnailImageView.visibility = View.VISIBLE
            
            if (currentPlayingHolder == this) {
                currentPlayingHolder = null
            }
        }        private fun startVideoPlayback(course: VideoData) {
            try {
                Log.d("CreatedCourseAdapter", "Starting video playback for: ${course.title}")
                
                // Detener cualquier reproducción anterior
                currentPlayingHolder?.stopAutoPlay()
                currentPlayingHolder = this@CourseViewHolder

                val uri = Uri.parse(course.videoUriString)
                Log.d("CreatedCourseAdapter", "Parsed URI: $uri")
                
                // Verificar si el archivo existe para URIs de archivos
                if (course.videoUriString!!.startsWith("file://")) {
                    val path = course.videoUriString!!.replace("file://", "")
                    val file = File(path)
                    if (!file.exists()) {
                        Log.e("CreatedCourseAdapter", "Video file does not exist: $path")
                        return
                    }
                    Log.d("CreatedCourseAdapter", "File exists: $path")
                }

                // Preparar el VideoView
                videoView.setVideoURI(uri)
                
                videoView.setOnPreparedListener { mediaPlayer ->
                    Log.d("CreatedCourseAdapter", "Video prepared, starting playback")
                    
                    mediaPlayer.isLooping = false
                    mediaPlayer.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    
                    // Mostrar el video y ocultar la miniatura
                    thumbnailImageView.visibility = View.GONE
                    videoView.visibility = View.VISIBLE
                    
                    // Empezar reproducción
                    videoView.start()
                    
                    // Programar detener el video después de 10 segundos
                    stopVideoRunnable = Runnable {
                        Log.d("CreatedCourseAdapter", "Stopping video after 10 seconds")
                        stopVideoAndShowThumbnail()
                    }
                    handler.postDelayed(stopVideoRunnable!!, 10000)
                }
                
                videoView.setOnErrorListener { _, what, extra ->
                    Log.e("CreatedCourseAdapter", "Video playback error: what=$what, extra=$extra, URI=${course.videoUriString}")
                    stopVideoAndShowThumbnail()
                    true                }
                
                videoView.setOnCompletionListener {
                    Log.d("CreatedCourseAdapter", "Video playback completed")
                    stopVideoAndShowThumbnail()
                }

                // No necesitamos llamar prepareAsync() ya que setVideoURI() lo hace automáticamente

            } catch (e: Exception) {
                Log.e("CreatedCourseAdapter", "Error starting video playback for ${course.title}", e)
                stopVideoAndShowThumbnail()
            }
        }private fun stopVideoAndShowThumbnail() {
            try {
                Log.d("CreatedCourseAdapter", "Stopping video and showing thumbnail")
                
                if (videoView.isPlaying) {
                    videoView.stopPlayback()
                }
                videoView.visibility = View.GONE
                thumbnailImageView.visibility = View.VISIBLE
                
                if (currentPlayingHolder == this@CourseViewHolder) {
                    currentPlayingHolder = null
                }
                
                // Limpiar callbacks
                stopVideoRunnable?.let { handler.removeCallbacks(it) }
                  } catch (e: Exception) {
                Log.e("CreatedCourseAdapter", "Error stopping video", e)
            }
        }

        private fun loadVideoThumbnail(course: VideoData) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Intentar diferentes fuentes para el thumbnail
                    when {
                        // Si hay un thumbnail asociado con el curso (que puede provenir de un video)
                        !course.thumbnailUri.isNullOrEmpty() && isValidUri(course.thumbnailUri) -> {
                            Log.d("CreatedCourseAdapter", "Using course thumbnailUri: ${course.thumbnailUri}")
                            Glide.with(context)
                                .load(course.thumbnailUri)
                                .placeholder(R.drawable.placeholder_image)
                                .error(R.drawable.placeholder_image)
                                .centerCrop()
                                .into(thumbnailImageView)
                        }
                        // Si hay un video asociado, intentar extraer un frame
                        course.videoUriString != null && !course.videoUriString.startsWith("content://media/external/video/dummy_") && isValidUri(course.videoUriString) -> {
                            val uri = Uri.parse(course.videoUriString)
                            val thumbnail = withContext(Dispatchers.IO) {
                                extractThumbnailFromVideo(uri)
                            }

                            if (thumbnail != null) {
                                thumbnailImageView.setImageBitmap(thumbnail)
                                Log.d("CreatedCourseAdapter", "Thumbnail extracted from video: ${course.videoUriString}")
                            } else {
                                // Si no se pudo extraer un frame, usar Glide para cargar directamente el video como thumbnail
                                Glide.with(context)
                                    .load(uri)
                                    .placeholder(R.drawable.placeholder_image)
                                    .error(R.drawable.placeholder_image)
                                    .centerCrop()
                                    .into(thumbnailImageView)
                                
                                Log.d("CreatedCourseAdapter", "Using Glide to load video as thumbnail: ${course.videoUriString}")
                            }
                        }
                        // Si hay una ruta de archivo local, intentar cargar el archivo
                        !course.localFilePath.isNullOrEmpty() -> {
                            val file = File(course.localFilePath)
                            if (file.exists() && file.canRead()) {
                                Glide.with(context)
                                    .load(file)
                                    .placeholder(R.drawable.placeholder_image)
                                    .error(R.drawable.placeholder_image)
                                    .centerCrop()
                                    .into(thumbnailImageView)
                                
                                Log.d("CreatedCourseAdapter", "Using localFilePath as thumbnail: ${course.localFilePath}")
                            } else {
                                thumbnailImageView.setImageResource(R.drawable.placeholder_image)
                                Log.d("CreatedCourseAdapter", "Local file not found or not readable, using placeholder: ${course.localFilePath}")
                            }
                        }
                        // Si todo lo demás falla, usar el placeholder
                        else -> {
                            thumbnailImageView.setImageResource(R.drawable.placeholder_image)
                            Log.d("CreatedCourseAdapter", "Using placeholder for course: ${course.title}")
                        }
                    }                } catch (e: SecurityException) {
                    Log.w("CreatedCourseAdapter", "Permission denied when loading thumbnail for course ${course.title}: ${e.message}")
                    thumbnailImageView.setImageResource(R.drawable.placeholder_image)
                } catch (e: Exception) {
                    Log.e("CreatedCourseAdapter", "Error loading thumbnail for course ${course.title}: ${e.message}")
                    thumbnailImageView.setImageResource(R.drawable.placeholder_image)
                }
            }
        }

        private fun extractThumbnailFromVideo(uri: Uri): Bitmap? {
            val retriever = MediaMetadataRetriever()
            try {
                if (uri.toString().startsWith("file://")) {
                    val path = uri.toString().replace("file://", "")
                    val file = File(path)
                    if (file.exists()) {
                        retriever.setDataSource(path)
                    } else {
                        Log.e("CreatedCourseAdapter", "File does not exist: $path")
                        return null
                    }
                } else {
                    retriever.setDataSource(context, uri)
                }

                return retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                Log.e("CreatedCourseAdapter", "Error extracting thumbnail", e)
                return null            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    Log.e("CreatedCourseAdapter", "Error releasing retriever", e)
                }
            }
        }
        
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
                false
            }
        }
    }
}