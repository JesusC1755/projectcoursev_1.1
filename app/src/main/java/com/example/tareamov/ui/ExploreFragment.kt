package com.example.tareamov.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tareamov.R
import com.example.tareamov.adapter.CreatedCourseAdapter
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.VideoData
import com.example.tareamov.util.VideoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class ExploreFragment : Fragment() {
    private lateinit var videoManager: VideoManager
    private lateinit var coursesAdapter: CreatedCourseAdapter
    private val coursesList = mutableListOf<VideoData>()
    private var currentUsername: String? = null
    private lateinit var searchEditText: EditText
    private var allCoursesList = mutableListOf<VideoData>() // Store all courses for filtering

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_explore, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar VideoManager
        videoManager = VideoManager(requireContext())

        // Set up navigation back to VideoHomeFragment
        view.findViewById<ImageButton>(R.id.backButton)?.setOnClickListener {
            findNavController().navigate(R.id.action_exploreFragment_to_videoHomeFragment)
        }

        // Get current username from shared preferences
        currentUsername = getCurrentUsername()

        // Configurar RecyclerViews para cursos
        setupRecyclerViews(view)

        // Initialize searchEditText
        searchEditText = view.findViewById(R.id.searchEditText)

        // Add TextWatcher to search bar
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCourses(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Cargar los cursos
        loadCourses()

        // Panel de navegación inferior
        view.findViewById<View>(R.id.exploreButton)?.setOnClickListener {
            // Ya estás en Explorar, puedes dejarlo vacío o recargar
        }
        view.findViewById<View>(R.id.activityButton)?.setOnClickListener {
            findNavController().navigate(R.id.action_exploreFragment_to_notificacionesFragment)
        }
        view.findViewById<View>(R.id.profileNavButton)?.setOnClickListener {
            findNavController().navigate(R.id.action_exploreFragment_to_profileFragment)
        }
        // Botón de añadir contenido (signo +)
        view.findViewById<View>(R.id.goToHomeButton)?.setOnClickListener {
            findNavController().navigate(R.id.action_exploreFragment_to_contentUploadFragment)
        }
        // Botón de inicio (home) para ir a VideoHomeFragment
        view.findViewById<View>(R.id.homeButton)?.setOnClickListener {
            findNavController().navigate(R.id.action_exploreFragment_to_videoHomeFragment)
        }
    }

    private fun getCurrentUsername(): String? {
        val sharedPref = requireContext().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        return sharedPref.getString("current_username", null)
    }    private fun setupRecyclerViews(view: View) {
        // Setup for "My Courses" RecyclerView
        val coursesRecyclerView = view.findViewById<RecyclerView>(R.id.coursesRecyclerView)
        coursesRecyclerView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        coursesAdapter = CreatedCourseAdapter(
            requireContext(),
            coursesList,
            onCourseClickListener = { course ->
                navigateToCourseDetail(course)
            }
        )
        coursesRecyclerView.adapter = coursesAdapter
        
        // Agregar ScrollListener para optimizar la reproducción
        coursesRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Cuando el scroll se detiene, permitir que el video visible se reproduzca
                    Log.d("ExploreFragment", "Scroll stopped, allowing video playback")
                }
            }
        })
    }

    private fun navigateToCourseDetail(course: VideoData) {
        val bundle = Bundle().apply {
            putLong("courseId", course.id)
            putString("courseName", course.title)
        }
        findNavController().navigate(R.id.action_exploreFragment_to_courseDetailFragment, bundle)
    }    private fun loadCourses() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val savedVideos = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(requireContext()).videoDao().getAllVideos()
                }
                allCoursesList.clear()
                allCoursesList.addAll(savedVideos)
                filterCourses("") // Load all courses initially
            } catch (e: Exception) {
                Log.e("ExploreFragment", "Error loading courses", e)
            }
        }
    }

    // Filter courses by name or category
    private fun filterCourses(query: String) {
        val filtered = if (query.isBlank()) {
            allCoursesList
        } else {
            allCoursesList.filter { course ->
                course.title.contains(query, ignoreCase = true) ||
                course.description.contains(query, ignoreCase = true)
            }
        }
        coursesList.clear()
        coursesList.addAll(filtered)
        coursesAdapter.updateCourses(coursesList)
    }    override fun onResume() {
        super.onResume()
        // Reload courses when returning to this fragment
        loadCourses()
    }

    override fun onPause() {
        super.onPause()
        // Stop any video playback when fragment is paused
        coursesAdapter.stopAllVideos()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up any resources
        coursesAdapter.stopAllVideos()
    }
}