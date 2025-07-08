package com.example.tareamov.repository

import android.content.Context
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.VideoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for handling course-related data operations
 */
class CourseRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val videoDao = database.videoDao()

    /**
     * Get all courses (represented as VideoData in the current architecture)
     */
    suspend fun getAllCourses(): List<VideoData> = withContext(Dispatchers.IO) {
        return@withContext videoDao.getAllVideos()
    }

    /**
     * Get a course by ID
     */
    suspend fun getCourseById(courseId: Long): VideoData? = withContext(Dispatchers.IO) {
        return@withContext videoDao.getVideoById(courseId)
    }

    /**
     * Get courses by creator username
     */
    suspend fun getCoursesByCreator(username: String): List<VideoData> = withContext(Dispatchers.IO) {
        return@withContext videoDao.getVideosByUsername(username)
    }

    /**
     * Save a new course
     */
    suspend fun saveCourse(course: VideoData): VideoData = withContext(Dispatchers.IO) {
        val id = videoDao.insertVideo(course)
        return@withContext course.copy(id = id)
    }

    /**
     * Update an existing course
     */
    suspend fun updateCourse(course: VideoData) = withContext(Dispatchers.IO) {
        videoDao.updateVideo(course)
    }

    /**
     * Delete a course
     */
    suspend fun deleteCourse(courseId: Long) = withContext(Dispatchers.IO) {
        videoDao.deleteVideo(courseId)
    }
}