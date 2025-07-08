package com.example.tareamov.ui

import android.content.Context
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.tareamov.MainActivity
import com.example.tareamov.R
import com.example.tareamov.data.AppDatabase
// Add this import for Usuario
import com.example.tareamov.data.entity.Usuario
import com.example.tareamov.util.SessionManager
import com.example.tareamov.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginFragment : Fragment() {
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var goToRegisterPersonaTextView: TextView
    private lateinit var authViewModel: AuthViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        usernameEditText = view.findViewById(R.id.usernameEditText)
        passwordEditText = view.findViewById(R.id.passwordEditText)
        loginButton = view.findViewById(R.id.loginButton)
        registerButton = view.findViewById(R.id.registerButton)
        goToRegisterPersonaTextView = view.findViewById(R.id.goToRegisterPersonaTextView)

        // Initialize SessionManager
        sessionManager = SessionManager.getInstance(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    
        // Initialize ViewModel
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
    
        // Set up click listener for the register button
        registerButton.setOnClickListener {
            findNavController().navigate(R.id.registerFragment)
        }

        // Set up click listener for the "Crear perfil" TextView
        goToRegisterPersonaTextView.setOnClickListener {
            findNavController().navigate(R.id.registerPersonaFragment)
        }

        // Observe login result
        authViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            if (result.success) {
                // The session is now correctly created by AuthViewModel, including the avatar.
                // Remove the redundant and incorrect session creation logic from here.

                // Store userId in SharedPreferences for ProfileFragment (if still needed,
                // SessionManager.getUserId() could also be used in ProfileFragment)
                val userId = result.userId ?: -1L
                if (userId != -1L) {
                    val sharedPrefs = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putLong("current_user_id", userId).apply()
                }

                findNavController().navigate(R.id.videoHomeFragment)
            } else {
                Toast.makeText(requireContext(), "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up login button click listener
        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Por favor ingrese usuario y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Attempt login
            authViewModel.login(username, password)
        }

        // Añade este código en el método onViewCreated:
        val togglePasswordVisibility = view.findViewById<ImageView>(R.id.togglePasswordVisibility)
        togglePasswordVisibility.setOnClickListener {
            if (passwordEditText.transformationMethod is PasswordTransformationMethod) {
                // Mostrar contraseña
                passwordEditText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off)
            } else {
                // Ocultar contraseña
                passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
                togglePasswordVisibility.setImageResource(R.drawable.ic_visibility)
            }
            // Mover el cursor al final del texto
            passwordEditText.setSelection(passwordEditText.text.length)
        }
    }

    private fun checkExistingUser() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val userCount = withContext(Dispatchers.IO) {
                db.usuarioDao().getUserCount()
            }

            // If at least one user exists, navigate directly to home screen
            // This navigation logic might need review based on app flow,
            // for now, focusing on the password hashing.
            if (userCount > 0) {
                // Consider if this navigation is always desired or if it should
                // depend on whether a user is already logged in via SessionManager.
                // findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
            }
        }
    }
}