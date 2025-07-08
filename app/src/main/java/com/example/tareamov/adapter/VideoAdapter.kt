package com.example.tareamov.adapter

import android.media.MediaPlayer // Added for MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tareamov.R
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.VideoData
import java.io.File
import kotlinx.coroutines.*

/**
 * Adaptador para mostrar videos en un ViewPager2 con estilo TikTok
 */
class VideoAdapter(private var videos: List<VideoData>) :
    RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videos[position])
    }

    override fun getItemCount(): Int = videos.size

    /**
     * Actualiza la lista de videos y notifica al adaptador
     */
    fun updateVideos(newVideos: List<VideoData>) {
        videos = newVideos
        notifyDataSetChanged()
    }

    /**
     * ViewHolder para mostrar un video individual
     */    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val videoView: VideoView = itemView.findViewById(R.id.videoView)
        private val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        private val descriptionText: TextView = itemView.findViewById(R.id.videoDescription)
        private val titleText: TextView = itemView.findViewById(R.id.gameTitle)
        private val errorPlaceholder: TextView = itemView.findViewById(R.id.errorPlaceholder)
        private val profileButton: de.hdodenhof.circleimageview.CircleImageView = itemView.findViewById(R.id.profileButton)
        private var currentJob: Job? = null
        private var mediaPlayer: MediaPlayer? = null // Store MediaPlayer instance
        private var isVideoPaused = false // Track pause state

        private fun showErrorPlaceholder() {
            videoView.visibility = View.GONE
            errorPlaceholder.visibility = View.VISIBLE
            Log.e("VideoAdapter", "Showing error placeholder")
        }        fun bind(videoData: VideoData) {
            usernameText.text = videoData.username
            descriptionText.text = videoData.description
            titleText.text = videoData.title

            // Reset views and pause state
            videoView.visibility = View.VISIBLE
            errorPlaceholder.visibility = View.GONE
            isVideoPaused = false // Reset pause state for new video

            // --- AVATAR LOADING LOGIC ---
            // Cancel any previous job to avoid race conditions
            currentJob?.cancel()
            profileButton.setImageResource(R.drawable.ic_profile) // Default while loading

            // Use coroutine to load avatar from DB
            currentJob = CoroutineScope(Dispatchers.Main).launch {
                try {
                    val context = itemView.context.applicationContext
                    val db = AppDatabase.getDatabase(context)
                    val persona = withContext(Dispatchers.IO) {
                        db.personaDao().getPersonaByUsername(videoData.username)
                    }
                    if (persona != null && !persona.avatar.isNullOrEmpty()) {
                        Glide.with(itemView)
                            .load(persona.avatar)
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile)
                            .into(profileButton)
                    } else {
                        profileButton.setImageResource(R.drawable.ic_profile)
                    }
                } catch (e: Exception) {
                    profileButton.setImageResource(R.drawable.ic_profile)
                }
            }
            // --- END AVATAR LOADING ---

            // Try to get the best URI for playback
            val bestUri = videoData.getBestVideoUri()

            if (bestUri != null) {
                try {
                    Log.d("VideoAdapter", "Setting video URI: $bestUri")
                    videoView.setVideoURI(bestUri)

                    videoView.setOnPreparedListener { mp -> // mp is the MediaPlayer instance
                        this.mediaPlayer = mp // Store the instance
                        // Ajustar el tamaño del VideoView según la relación de aspecto real
                        val videoWidth = mp.videoWidth
                        val videoHeight = mp.videoHeight
                        if (videoWidth > 0 && videoHeight > 0) {
                            val parentWidth = (videoView.parent as View).width
                            val aspectRatio = videoWidth.toFloat() / videoHeight
                            val newHeight = (parentWidth / aspectRatio).toInt()
                            val params = videoView.layoutParams
                            params.width = parentWidth
                            params.height = newHeight
                            videoView.layoutParams = params
                        }
                        mp.setVolume(1f, 1f) // Default volume
                        mp.isLooping = true
                        videoView.start()
                    }                    // Manejar errores de reproducción
                    videoView.setOnErrorListener { _, what, extra ->
                        Log.e("VideoAdapter", "Video playback error: what=$what, extra=$extra")
                        showErrorPlaceholder()
                        true // Return true to indicate we handled the error
                    }

                    // Set up touch listener for play/pause functionality
                    videoView.setOnClickListener {
                        togglePlayPause()
                    }
                } catch (e: Exception) {
                    Log.e("VideoAdapter", "Error setting video URI", e)
                    showErrorPlaceholder()
                }
            } else {
                Log.e("VideoAdapter", "No valid video URI available")
                showErrorPlaceholder()
            }
        }        /**
         * Pausa la reproducción del video
         */
        fun pauseVideo() {
            if (videoView.isPlaying) {
                videoView.pause()
                isVideoPaused = true
            }
        }/**
         * Inicia la reproducción del video
         */
        fun playVideo() {
            try {
                if (!videoView.isPlaying && !isVideoPaused) {
                    // Only auto-play if the user hasn't manually paused it
                    videoView.start()
                }
            } catch (e: Exception) {
                Log.e("VideoAdapter", "Error playing video", e)
            }
        }/**
         * Sets the mute state of the video.
         */
        fun setMuteState(mute: Boolean) {
            val volume = if (mute) 0f else 1f
            try {
                mediaPlayer?.setVolume(volume, volume)
            } catch (e: IllegalStateException) {
                Log.e("VideoAdapter", "Error setting volume, MediaPlayer might not be ready.", e)
                // Optionally, store desired mute state and apply it in onPreparedListener
            }
        }

        /**
         * Toggles between play and pause state of the video
         */
        private fun togglePlayPause() {
            try {
                if (videoView.isPlaying) {
                    videoView.pause()
                    isVideoPaused = true
                    Log.d("VideoAdapter", "Video paused by user tap")
                } else {
                    videoView.start()
                    isVideoPaused = false
                    Log.d("VideoAdapter", "Video resumed by user tap")
                }
            } catch (e: Exception) {
                Log.e("VideoAdapter", "Error toggling play/pause", e)
            }
        }
    }

    override fun onViewAttachedToWindow(holder: VideoViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.playVideo()
    }

    override fun onViewDetachedFromWindow(holder: VideoViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.pauseVideo()
    }
}