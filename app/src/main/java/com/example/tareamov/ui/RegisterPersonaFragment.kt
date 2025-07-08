// Make sure there's only one RegisterPersonaFragment class in your project
package com.example.tareamov.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.tareamov.R
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.Persona
import com.example.tareamov.data.entity.Usuario
import com.example.tareamov.viewmodel.PersonaViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Changed class name from RegisterUserFragment to RegisterPersonaFragment to match navigation graph
class RegisterPersonaFragment : Fragment() {
    private lateinit var viewModel: PersonaViewModel

    // TextInputLayouts para mejor manejo de errores
    private lateinit var identificacionLayout: TextInputLayout
    private lateinit var nombresLayout: TextInputLayout
    private lateinit var apellidosLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var telefonoLayout: TextInputLayout
    private lateinit var direccionLayout: TextInputLayout
    private lateinit var fechaNacimientoLayout: TextInputLayout
    // Remove these fields as they don't exist in the layout
    // private lateinit var usernameLayout: TextInputLayout
    // private lateinit var passwordLayout: TextInputLayout
    // private lateinit var confirmPasswordLayout: TextInputLayout

    // EditTexts
    private lateinit var identificacionEditText: TextInputEditText
    private lateinit var nombresEditText: TextInputEditText
    private lateinit var apellidosEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var telefonoEditText: TextInputEditText
    private lateinit var direccionEditText: TextInputEditText
    private lateinit var fechaNacimientoEditText: TextInputEditText
    // Remove these fields as they don't exist in the layout
    // private lateinit var usernameEditText: TextInputEditText
    // private lateinit var passwordEditText: TextInputEditText
    // private lateinit var confirmPasswordEditText: TextInputEditText

    private lateinit var registerButton: Button
    private lateinit var titleTextView: TextView
    private lateinit var subtitleTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_register_persona, container, false)

        // Initialize views
        titleTextView = view.findViewById(R.id.registerTitle)
        subtitleTextView = view.findViewById(R.id.registerSubtitle)

        // Inicializar TextInputLayouts
        identificacionLayout = view.findViewById(R.id.identificacionLayout)
        nombresLayout = view.findViewById(R.id.nombresLayout)
        apellidosLayout = view.findViewById(R.id.apellidosLayout)
        emailLayout = view.findViewById(R.id.emailLayout)
        telefonoLayout = view.findViewById(R.id.telefonoLayout)
        direccionLayout = view.findViewById(R.id.direccionLayout)
        fechaNacimientoLayout = view.findViewById(R.id.fechaNacimientoLayout)
        // Remove these lines
        // usernameLayout = view.findViewById(R.id.usernameLayout)
        // passwordLayout = view.findViewById(R.id.passwordLayout)
        // confirmPasswordLayout = view.findViewById(R.id.confirmPasswordLayout)

        // Inicializar EditTexts
        identificacionEditText = view.findViewById(R.id.identificacionEditText)
        nombresEditText = view.findViewById(R.id.nombresEditText)
        apellidosEditText = view.findViewById(R.id.apellidosEditText)
        emailEditText = view.findViewById(R.id.emailEditText)
        telefonoEditText = view.findViewById(R.id.telefonoEditText)
        direccionEditText = view.findViewById(R.id.direccionEditText)
        fechaNacimientoEditText = view.findViewById(R.id.fechaNacimientoEditText)
        // Remove these lines
        // usernameEditText = view.findViewById(R.id.usernameEditText)
        // passwordEditText = view.findViewById(R.id.passwordEditText)
        // confirmPasswordEditText = view.findViewById(R.id.confirmPasswordEditText)

        registerButton = view.findViewById(R.id.registerButton)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[PersonaViewModel::class.java]

        // Configurar el selector de fecha
        setupDatePicker()

        // Configurar listeners para validación en tiempo real
        setupTextChangeListeners()

        // Set up register button click listener
        registerButton.setOnClickListener {
            registerUser()
        }
    }

    private fun setupDatePicker() {
        fechaNacimientoEditText.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecciona fecha de nacimiento")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = Date(selection)
                fechaNacimientoEditText.setText(dateFormat.format(date))
            }

            datePicker.show(parentFragmentManager, "DATE_PICKER")
        }
    }

    private fun setupTextChangeListeners() {
        // Implement real-time validation if needed
        // For example:
        /*
        emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s != null && !android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()) {
                    emailLayout.error = "Email inválido"
                } else {
                    emailLayout.error = null
                }
            }
        })
        */
    }

    private fun clearErrors() {
        identificacionLayout.error = null
        nombresLayout.error = null
        apellidosLayout.error = null
        emailLayout.error = null
        telefonoLayout.error = null
        direccionLayout.error = null
        fechaNacimientoLayout.error = null
        // Remove these lines
        // usernameLayout.error = null
        // passwordLayout.error = null
        // confirmPasswordLayout.error = null
    }

    // Update the registerUser method to not use username/password fields
    private fun registerUser() {
        // Limpiar errores previos
        clearErrors()

        val identificacion = identificacionEditText.text.toString()
        val nombres = nombresEditText.text.toString()
        val apellidos = apellidosEditText.text.toString()
        val email = emailEditText.text.toString()
        val telefono = telefonoEditText.text.toString()
        val direccion = direccionEditText.text.toString()
        val fechaNacimiento = fechaNacimientoEditText.text.toString()
        // Remove these lines
        // val username = usernameEditText.text.toString()
        // val password = passwordEditText.text.toString()
        // val confirmPassword = confirmPasswordEditText.text.toString()

        // Validar campos vacíos
        var hasError = false

        if (identificacion.isEmpty()) {
            identificacionLayout.error = "Campo requerido"
            hasError = true
        }

        if (nombres.isEmpty()) {
            nombresLayout.error = "Campo requerido"
            hasError = true
        }

        if (apellidos.isEmpty()) {
            apellidosLayout.error = "Campo requerido"
            hasError = true
        }

        if (email.isEmpty()) {
            emailLayout.error = "Campo requerido"
            hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Email inválido"
            hasError = true
        }

        if (telefono.isEmpty()) {
            telefonoLayout.error = "Campo requerido"
            hasError = true
        }

        if (direccion.isEmpty()) {
            direccionLayout.error = "Campo requerido"
            hasError = true
        }

        if (fechaNacimiento.isEmpty()) {
            fechaNacimientoLayout.error = "Campo requerido"
            hasError = true
        }

        // Remove username/password validation
        // if (username.isEmpty()) {
        //     usernameLayout.error = "Campo requerido"
        //     hasError = true
        // } else if (username.length < 4) {
        //     usernameLayout.error = "Mínimo 4 caracteres"
        //     hasError = true
        // }
        //
        // if (password.isEmpty()) {
        //     passwordLayout.error = "Campo requerido"
        //     hasError = true
        // } else if (password.length < 6) {
        //     passwordLayout.error = "Mínimo 6 caracteres"
        //     hasError = true
        // }
        //
        // if (confirmPassword.isEmpty()) {
        //     confirmPasswordLayout.error = "Campo requerido"
        //     hasError = true
        // } else if (password != confirmPassword) {
        //     confirmPasswordLayout.error = "Las contraseñas no coinciden"
        //     hasError = true
        // }

        if (hasError) {
            return
        }

        // Proceder con el registro
        lifecycleScope.launch {
            try {
                // Crear entidad Persona
                val persona = Persona(
                    identificacion = identificacion,
                    nombres = nombres,
                    apellidos = apellidos,
                    email = email,
                    telefono = telefono,
                    direccion = direccion,
                    fechaNacimiento = fechaNacimiento,
                    esUsuario = false // This person is not a user
                )

                // Use insert method from the repository instead of insertPersona
                viewModel.insert(persona)

                // Navegar a la pantalla de inicio
                withContext(Dispatchers.Main) {
                    // Show success message
                    Toast.makeText(requireContext(), "Persona registrada correctamente", Toast.LENGTH_SHORT).show()

                    // Navigate back to HomeFragment after registration
                    findNavController().navigate(R.id.action_registerPersonaFragment_to_homeFragment)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error al registrar: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace() // Log the full stack trace for debugging
                }
            }
        }
    }
}