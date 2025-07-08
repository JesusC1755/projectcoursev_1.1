package com.example.tareamov.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.Usuario
import com.example.tareamov.repository.UsuarioRepository
import com.example.tareamov.repository.PersonaRepository // Import PersonaRepository
import com.example.tareamov.util.SessionManager
import com.example.tareamov.util.BcryptUtils
import com.example.tareamov.util.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val usuarioRepository: UsuarioRepository
    private val sessionManager: SessionManager
    private val personaRepository: PersonaRepository // Declare PersonaRepository

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    private val _currentUserId = MutableLiveData<Long?>()
    val currentUserId: LiveData<Long?> = _currentUserId

    init {
        val database = AppDatabase.getDatabase(application)
        usuarioRepository = UsuarioRepository(database.usuarioDao())
        personaRepository = PersonaRepository(database.personaDao(), database.usuarioDao()) // Initialize PersonaRepository
        sessionManager = SessionManager.getInstance(application.applicationContext)

        _currentUserId.value = sessionManager.getUserId()
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {            try {
                Log.d("AuthViewModel", "Attempting login for user: $username")
                val user = withContext(Dispatchers.IO) {
                    usuarioRepository.getUsuarioByUsername(username)
                }
                
                if (user != null) {
                    Log.d("AuthViewModel", "User found in database: ${user.usuario}")

                    // Check if stored password is a BCrypt hash or plain text
                    val isStoredPasswordBcryptHash = BcryptUtils.isValidHash(user.contrasena)
                    val isStoredPasswordPlainText = !isStoredPasswordBcryptHash && user.contrasena == password
                    
                    val passwordMatches = when {
                        isStoredPasswordBcryptHash -> {
                            // Normal case: verify BCrypt hash
                            BcryptUtils.verify(password, user.contrasena)
                        }
                        isStoredPasswordPlainText -> {
                            // Migration case: plain text password
                            true
                        }
                        else -> {
                            // Try legacy SHA256 comparison for migration
                            val hashedInputPassword = CryptoUtils.sha256(password)
                            user.contrasena == hashedInputPassword
                        }
                    }

                    Log.d("AuthViewModel", "Stored password type: ${when {
                        isStoredPasswordBcryptHash -> "BCrypt hash"
                        isStoredPasswordPlainText -> "Plain text"
                        else -> "Legacy hash"
                    }}")
                    Log.d("AuthViewModel", "Password match: $passwordMatches")

                    if (passwordMatches) {
                        // If password is not already BCrypt hashed, update it
                        if (!isStoredPasswordBcryptHash) {
                            Log.d("AuthViewModel", "Updating password to BCrypt hash")
                            val newBcryptHash = BcryptUtils.hash(password)
                            withContext(Dispatchers.IO) {
                                usuarioRepository.updatePassword(user.id, newBcryptHash)
                            }
                        }

                        // Fetch Persona to get avatar
                        val persona = withContext(Dispatchers.IO) {
                            personaRepository.getPersonaById(user.persona_id)
                        }
                        val avatarUri = persona?.avatar

                        Log.d("AuthViewModel", "Password match successful. Persona ID: ${user.persona_id}, Avatar URI: $avatarUri")
                        // Save session with user details, including persona_id and avatarUri
                        sessionManager.createLoginSession(user.usuario, user.id, user.persona_id, user.rol, avatarUri)

                        _loginResult.value = LoginResult(success = true, userId = user.id, userRole = user.rol)
                        _currentUserId.value = user.id
                    } else {
                        Log.d("AuthViewModel", "Password match failed")
                        _loginResult.value = LoginResult(success = false)
                        _currentUserId.value = null
                    }
                } else {
                    Log.d("AuthViewModel", "User not found in database")
                    _loginResult.value = LoginResult(success = false)
                    _currentUserId.value = null
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login error: ${e.message}", e)
                _loginResult.value = LoginResult(success = false)
                _currentUserId.value = null
            }
        }
    }

    // Method to fetch Usuario by username (if needed elsewhere)
    suspend fun getUsuarioByUsername(username: String): Usuario? {
        return withContext(Dispatchers.IO) {
            usuarioRepository.getUsuarioByUsername(username)
        }
    }    // Example: Add a logout function that updates currentUserId
    fun logout() {
        // Perform logout actions (e.g., clear session in SessionManager)
        sessionManager.logout() // Clear the session
        _currentUserId.value = null
        _loginResult.value = LoginResult(success = false) // Optionally update loginResult
    }
}

data class LoginResult(val success: Boolean, val userId: Long? = null, val userRole: String? = null)