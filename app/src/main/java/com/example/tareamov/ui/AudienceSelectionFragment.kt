package com.example.tareamov.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.tareamov.R
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.VideoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudienceSelectionFragment : Fragment() {

    private lateinit var backButton: ImageButton
    private lateinit var uploadButton: Button
    private lateinit var audienceOptions: RadioGroup
    private lateinit var yesForKidsOption: RadioButton
    private lateinit var notForKidsOption: RadioButton
    private lateinit var whatIsContentLink: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_audience_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        backButton = view.findViewById(R.id.backButton)
        uploadButton = view.findViewById(R.id.uploadButton)
        audienceOptions = view.findViewById(R.id.audienceOptions)
        yesForKidsOption = view.findViewById(R.id.yesForKidsOption)
        notForKidsOption = view.findViewById(R.id.notForKidsOption)
        whatIsContentLink = view.findViewById(R.id.whatIsContentLink)

        // Set default selection
        notForKidsOption.isChecked = true

        // Set up back button
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Set up "What is content for kids" link
        whatIsContentLink.setOnClickListener {
            // In a real app, this would open a help dialog or webpage
            // For now, just show a message
        }

        // Set up upload button
        uploadButton.setOnClickListener {
            // Get video details from shared preferences
            val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
            val videoUriString = sharedPref.getString("latest_video_uri", null)
            val title = sharedPref.getString("video_title", "Mi video")
            val description = sharedPref.getString("video_description", "")
            val isForKids = yesForKidsOption.isChecked

            if (videoUriString != null) {
                // Save the video to the database
                saveVideoToDatabase(
                    Uri.parse(videoUriString),
                    title ?: "Mi video",
                    description ?: "",
                    isForKids
                )
            }
        }
    }

    private fun saveVideoToDatabase(videoUri: Uri, title: String, description: String, isForKids: Boolean) {
        lifecycleScope.launch {
            try {
                val username = getCurrentUsername() ?: "Usuario"

                // Create a new VideoData object
                val newVideo = VideoData(
                    username = username,
                    description = description,
                    title = title,
                    videoUri = videoUri
                )

                // Save to database
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(requireContext())
                    db.videoDao().insertVideo(newVideo)
                }

                // Navigate to VideoHomeFragment to see the uploaded video
                findNavController().navigate(
                    R.id.action_audienceSelectionFragment_to_videoHomeFragment
                )

            } catch (e: Exception) {
                e.printStackTrace()
                // In a real app, show an error message to the user
            }
        }
    }

    private fun getCurrentUsername(): String? {
        val sharedPreferences = requireActivity().getSharedPreferences(
            "auth_prefs", Context.MODE_PRIVATE
        )
        return sharedPreferences.getString("current_username", null)
    }
}