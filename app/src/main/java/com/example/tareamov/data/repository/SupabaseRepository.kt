package com.example.tareamov.data.repository

import io.supabase.SupabaseClient
import com.example.tareamov.BuildConfig

class SupabaseRepository {
    private val client = SupabaseClient(
        BuildConfig.SUPABASE_URL,
        BuildConfig.SUPABASE_KEY
    )

    // Login usando email y contraseña (puedes adaptar para username/email más adelante)
    suspend fun loginConEmail(email: String, password: String): String? {
        val response = client.auth.signInWith(email = email, password = password)
        return response.session?.accessToken
    }

    // Aquí puedes agregar más funciones para consultar o modificar datos en Supabase
}
