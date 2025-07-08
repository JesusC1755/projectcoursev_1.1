package com.example.tareamov.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.tareamov.R
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.Persona
import com.example.tareamov.data.entity.Usuario
import com.example.tareamov.viewmodel.PersonaViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RegisterFragment : Fragment() {
    private lateinit var viewModel: PersonaViewModel

    // TextInputLayouts para mejor manejo de errores
    private lateinit var identificacionLayout: TextInputLayout
    private lateinit var nombresLayout: TextInputLayout
    private lateinit var apellidosLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var telefonoLayout: TextInputLayout
    private lateinit var direccionLayout: TextInputLayout
    private lateinit var fechaNacimientoLayout: TextInputLayout
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout

    // EditTexts
    private lateinit var identificacionEditText: TextInputEditText
    private lateinit var nombresEditText: TextInputEditText
    private lateinit var apellidosEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var telefonoEditText: TextInputEditText
    private lateinit var direccionEditText: TextInputEditText
    private lateinit var fechaNacimientoEditText: TextInputEditText
    private lateinit var usernameEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var confirmPasswordEditText: TextInputEditText

    // Avatar components
    private lateinit var avatarImageView: CircleImageView
    private lateinit var selectAvatarFab: FloatingActionButton

    private lateinit var registerButton: Button
    private lateinit var titleTextView: TextView
    private lateinit var subtitleTextView: TextView

    // Avatar handling
    private var selectedAvatarUri: Uri? = null
    private var currentPhotoPath: String? = null

    // Activity result launchers
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path ->
                val file = File(path)
                selectedAvatarUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                loadImageIntoAvatar(selectedAvatarUri)
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAvatarUri = uri
                loadImageIntoAvatar(uri)
            }
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openImagePicker()
        } else {
            Toast.makeText(
                requireContext(),
                "Se requiere permiso de cámara para tomar fotos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_register, container, false)

        // Inicializar TextViews
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
        usernameLayout = view.findViewById(R.id.usernameLayout)
        passwordLayout = view.findViewById(R.id.passwordLayout)
        confirmPasswordLayout = view.findViewById(R.id.confirmPasswordLayout)

        // Inicializar EditTexts
        identificacionEditText = view.findViewById(R.id.identificacionEditText)
        nombresEditText = view.findViewById(R.id.nombresEditText)
        apellidosEditText = view.findViewById(R.id.apellidosEditText)
        emailEditText = view.findViewById(R.id.emailEditText)
        telefonoEditText = view.findViewById(R.id.telefonoEditText)
        direccionEditText = view.findViewById(R.id.direccionEditText)
        fechaNacimientoEditText = view.findViewById(R.id.fechaNacimientoEditText)
        usernameEditText = view.findViewById(R.id.usernameEditText)
        passwordEditText = view.findViewById(R.id.passwordEditText)
        confirmPasswordEditText = view.findViewById(R.id.confirmPasswordEditText)

        // Inicializar componentes de avatar
        avatarImageView = view.findViewById(R.id.avatarImageView)
        selectAvatarFab = view.findViewById(R.id.selectAvatarFab)

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

        // Configurar selector de avatar
        setupAvatarSelection()

        // Set up register button click listener
        registerButton.setOnClickListener {
            registerUser()
        }
    }

    private fun setupAvatarSelection() {
        selectAvatarFab.setOnClickListener {
            checkCameraPermission()
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openImagePicker()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(
                    requireContext(),
                    "Se requiere permiso de cámara para tomar fotos",
                    Toast.LENGTH_SHORT
                ).show()
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openImagePicker() {
        val options = arrayOf("Tomar foto", "Elegir de la galería", "Cancelar")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Seleccionar avatar")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> takePictureFromCamera()
                    1 -> pickImageFromGallery()
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun takePictureFromCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
            intent.resolveActivity(requireActivity().packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Toast.makeText(
                        requireContext(),
                        "Error al crear el archivo de imagen",
                        Toast.LENGTH_SHORT
                    ).show()
                    null
                }

                photoFile?.also {
                    val photoURI = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        it
                    )
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    takePictureLauncher.launch(intent)
                }
            }
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun loadImageIntoAvatar(uri: Uri?) {
        uri?.let {
            Glide.with(this)
                .load(it)
                .centerCrop()
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(avatarImageView)
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
        usernameLayout.error = null
        passwordLayout.error = null
        confirmPasswordLayout.error = null
    }

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
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val avatarUri = selectedAvatarUri?.toString()

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

        if (username.isEmpty()) {
            usernameLayout.error = "Campo requerido"
            hasError = true
        } else if (username.length < 4) {
            usernameLayout.error = "Mínimo 4 caracteres"
            hasError = true
        }

        if (password.isEmpty()) {
            passwordLayout.error = "Campo requerido"
            hasError = true
        } else if (password.length < 6) {
            passwordLayout.error = "Mínimo 6 caracteres"
            hasError = true
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordLayout.error = "Campo requerido"
            hasError = true
        } else if (password != confirmPassword) {
            confirmPasswordLayout.error = "Las contraseñas no coinciden"
            hasError = true
        }

        if (hasError) {
            return
        }

        // Proceder con el registro
        lifecycleScope.launch {
            try {
                // Verificar si el nombre de usuario ya existe
                val usernameExists = viewModel.checkUsernameExists(username)
                if (usernameExists) {
                    withContext(Dispatchers.Main) {
                        usernameLayout.error = "El nombre de usuario ya existe"
                    }
                    return@launch
                }

                // Crear entidad Persona con el avatar
                val persona = Persona(
                    identificacion = identificacion,
                    nombres = nombres,
                    apellidos = apellidos,
                    email = email,
                    telefono = telefono,
                    direccion = direccion,
                    fechaNacimiento = fechaNacimiento,
                    avatar = avatarUri,
                    esUsuario = true  // This person is a user
                )

                // Insert the persona first
                val personaId = withContext(Dispatchers.IO) {
                    viewModel.insertAndGetId(persona)
                }

                // Then create and insert the usuario separately
                val hashedPassword = com.example.tareamov.util.BcryptUtils.hash(password)
                val usuario = Usuario(
                    usuario = username,
                    contrasena = hashedPassword,
                    persona_id = personaId
                )

                withContext(Dispatchers.IO) {
                    viewModel.insertUsuario(usuario)
                }

                // Navegar a la pantalla de inicio de sesión
                withContext(Dispatchers.Main) {
                    // Show success message
                    Toast.makeText(requireContext(), "Usuario registrado correctamente", Toast.LENGTH_SHORT).show()

                    // Always navigate to login screen after registration
                    findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
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