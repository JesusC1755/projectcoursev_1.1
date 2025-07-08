package com.example.tareamov.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.tareamov.data.entity.VideoData
import com.example.tareamov.repository.CourseRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for handling course-related data and business logic
 */
class CursoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    // LiveData for courses
    private val _cursoData = MutableLiveData<List<VideoData>>()
    val cursoData: LiveData<List<VideoData>> = _cursoData

    // LiveData for a single course
    private val _selectedCurso = MutableLiveData<VideoData?>()
    val selectedCurso: LiveData<VideoData?> = _selectedCurso

    // LiveData for loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData for error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /**
     * Load all courses
     */
    fun loadCursoData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                val courses = repository.getAllCourses()
                _cursoData.value = courses
            } catch (e: Exception) {
                _errorMessage.value = "Error loading courses: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load a specific course by ID
     */
    fun loadCursoById(courseId: Long) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                val course = repository.getCourseById(courseId)
                _selectedCurso.value = course
            } catch (e: Exception) {
                _errorMessage.value = "Error loading course: ${e.message}"
                _selectedCurso.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Save a new course
     */
    fun saveCurso(course: VideoData, onSuccess: (VideoData) -> Unit) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                val savedCourse = repository.saveCourse(course)
                onSuccess(savedCourse)
            } catch (e: Exception) {
                _errorMessage.value = "Error saving course: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update an existing course
     */
    fun updateCurso(course: VideoData, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                repository.updateCourse(course)
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = "Error updating course: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Delete a course
     */
    fun deleteCurso(courseId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                repository.deleteCourse(courseId)
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = "Error deleting course: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}