package com.example.tareamov.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.example.tareamov.R
import com.example.tareamov.util.UriPermissionManager

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var uriPermissionManager: UriPermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        videoView = findViewById(R.id.videoView)
        uriPermissionManager = UriPermissionManager(this)

        // Get video URI from intent
        val videoUriString = intent.getStringExtra("videoUri")
        val videoTitle = intent.getStringExtra("videoTitle") ?: "Video"

        // Set title
        title = videoTitle

        if (videoUriString != null) {
            try {
                val uri = Uri.parse(videoUriString)

                // Ensure we have permission for this URI
                if (uri.scheme == "content" && !uriPermissionManager.hasPermissionForUri(uri)) {
                    uriPermissionManager.takePersistablePermission(uri)
                }

                // Set up media controller
                val mediaController = MediaController(this)
                mediaController.setAnchorView(videoView)
                videoView.setMediaController(mediaController)

                // Set video URI
                videoView.setVideoURI(uri)

                // Start playing
                videoView.setOnPreparedListener { mp ->
                    mp.start()
                }

                videoView.setOnErrorListener { _, what, extra ->
                    Log.e("VideoPlayerActivity", "Error playing video: what=$what, extra=$extra")
                    Toast.makeText(this, "Error al reproducir el video", Toast.LENGTH_SHORT).show()
                    finish()
                    true
                }

            } catch (e: Exception) {
                Log.e("VideoPlayerActivity", "Error setting up video player", e)
                Toast.makeText(this, "Error al reproducir el video: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, "URI de video no válida", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // Save current position if needed
        if (videoView.isPlaying) {
            videoView.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView.stopPlayback()
    }
}