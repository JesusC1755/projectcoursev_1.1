package com.example.tareamov.service

import com.example.tareamov.data.model.OllamaRequest
import com.example.tareamov.data.model.OllamaResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApiService {
    
    @POST("api/generate")
    suspend fun generateResponse(@Body request: OllamaRequest): Response<OllamaResponse>
    
    @POST("api/chat")
    suspend fun generateChat(@Body request: OllamaRequest): Response<OllamaResponse>
    
    companion object {
        const val BASE_URL = "http://localhost:11434/"
    }
}
