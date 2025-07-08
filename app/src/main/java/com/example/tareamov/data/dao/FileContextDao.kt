package com.example.tareamov.data.dao

import androidx.room.*
import com.example.tareamov.data.entity.FileContext
import kotlinx.coroutines.flow.Flow

@Dao
interface FileContextDao {
    
    @Query("SELECT * FROM file_contexts WHERE submissionId = :submissionId")
    suspend fun getFileContextBySubmission(submissionId: Long): FileContext?
    
    @Query("SELECT * FROM file_contexts ORDER BY timestamp DESC")
    fun getAllFileContexts(): Flow<List<FileContext>>
    
    @Insert
    suspend fun insertFileContext(fileContext: FileContext): Long
    
    @Update
    suspend fun updateFileContext(fileContext: FileContext)
    
    @Query("DELETE FROM file_contexts WHERE submissionId = :submissionId")
    suspend fun deleteFileContextBySubmission(submissionId: Long)
    
    @Query("DELETE FROM file_contexts")
    suspend fun clearAllFileContexts()
}
