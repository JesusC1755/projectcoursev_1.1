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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.tareamov.MainActivity
import com.example.tareamov.R
import com.example.tareamov.data.entity.Persona
import com.example.tareamov.data.entity.Usuario
import com.example.tareamov.viewmodel.PersonaViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditPersonaFragment : Fragment() {
    private lateinit var viewModel: PersonaViewModel
    private lateinit var identificacionEditText: EditText
    private lateinit var nombresEditText: EditText
    private lateinit var apellidosEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var telefonoEditText: EditText
    private lateinit var direccionEditText: EditText
    private lateinit var fechaNacimientoEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    // Add role dropdown
    private lateinit var rolDropdown: AutoCompleteTextView

    // Avatar components
    private lateinit var avatarImageView: CircleImageView
    private lateinit var selectAvatarFab: FloatingActionButton
    private lateinit var avatarProgressBar: ProgressBar

    private var personaId: Long = 0
    private var currentPersona: Persona? = null
    private var currentUsuario: Usuario? = null
    // Change from ROL_USUARIO to ROL_ESTUDIANTE
    private var selectedRole: String = Usuario.ROL_ESTUDIANTE

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
        val view = inflater.inflate(R.layout.fragment_edit_persona, container, false)

        identificacionEditText = view.findViewById(R.id.identificacionEditText)
        nombresEditText = view.findViewById(R.id.nombresEditText)
        apellidosEditText = view.findViewById(R.id.apellidosEditText)
        emailEditText = view.findViewById(R.id.emailEditText)
        telefonoEditText = view.findViewById(R.id.telefonoEditText)
        direccionEditText = view.findViewById(R.id.direccionEditText)
        fechaNacimientoEditText = view.findViewById(R.id.fechaNacimientoEditText)
        saveButton = view.findViewById(R.id.saveButton)
        cancelButton = view.findViewById(R.id.cancelButton)

        // Initialize role dropdown
        rolDropdown = view.findViewById(R.id.rolDropdown)

        // Initialize avatar components
        avatarImageView = view.findViewById(R.id.avatarImageView)
        selectAvatarFab = view.findViewById(R.id.selectAvatarFab)
        avatarProgressBar = view.findViewById(R.id.avatarProgressBar)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = (requireActivity() as MainActivity).personaViewModel

        // Get persona ID from arguments
        arguments?.let {
            personaId = it.getLong("personaId", 0)
        }

        // Setup role dropdown
        setupRoleDropdown()

        if (personaId > 0) {
            // Load persona data
            loadPersonaData()
        } else {
            // Handle error
            Toast.makeText(requireContext(), "Error: No se pudo cargar la persona", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }

        // Set up avatar selection
        setupAvatarSelection()

        saveButton.setOnClickListener {
            savePersona()
        }

        cancelButton.setOnClickListener {
            findNavController().navigateUp()
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
            avatarProgressBar.visibility = View.VISIBLE
            Glide.with(this)
                .load(it)
                .centerCrop()
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(avatarImageView)

            // Hide progress bar after image is loaded
            avatarProgressBar.visibility = View.GONE
        }
    }

    private fun setupRoleDropdown() {
        val roles = viewModel.getAllRoles() // Fetch roles from ViewModel

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, roles)
        rolDropdown.setAdapter(adapter)

        rolDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedRole = roles[position]
        }
    }

    private fun loadPersonaData() {
        lifecycleScope.launch {
            try {
                // Load persona data
                currentPersona = viewModel.getPersonaByIdSync(personaId)

                // Load associated usuario if exists
                currentUsuario = viewModel.getUsuarioByPersonaId(personaId)

                withContext(Dispatchers.Main) {
                    currentPersona?.let { persona ->
                        identificacionEditText.setText(persona.identificacion)
                        nombresEditText.setText(persona.nombres)
                        apellidosEditText.setText(persona.apellidos)
                        emailEditText.setText(persona.email)
                        telefonoEditText.setText(persona.telefono)
                        direccionEditText.setText(persona.direccion)
                        fechaNacimientoEditText.setText(persona.fechaNacimiento)

                        // Load avatar if available
                        persona.avatar?.let { avatarPath ->
                            if (avatarPath.isNotEmpty()) {
                                loadImageIntoAvatar(Uri.parse(avatarPath))
                            }
                        }

                        // Set role in dropdown if usuario exists
                        currentUsuario?.let { usuario ->
                            selectedRole = usuario.rol
                            rolDropdown.setText(selectedRole, false)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Error al cargar datos: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun savePersona() {
        val identificacion = identificacionEditText.text.toString().trim()
        val nombres = nombresEditText.text.toString().trim()
        val apellidos = apellidosEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val telefono = telefonoEditText.text.toString().trim()
        val direccion = direccionEditText.text.toString().trim()
        val fechaNacimiento = fechaNacimientoEditText.text.toString().trim()

        // Validate required fields
        if (nombres.isEmpty() || apellidos.isEmpty()) {
            Toast.makeText(requireContext(), "Nombres y apellidos son obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Update persona
                currentPersona?.let { persona ->
                    val updatedPersona = persona.copy(
                        identificacion = identificacion,
                        nombres = nombres,
                        apellidos = apellidos,
                        email = email,
                        telefono = telefono,
                        direccion = direccion,
                        fechaNacimiento = fechaNacimiento,
                        avatar = selectedAvatarUri?.toString() ?: persona.avatar
                    )
                    viewModel.update(updatedPersona)

                    // Update usuario role if exists
                    currentUsuario?.let { usuario ->
                        if (usuario.rol != selectedRole) {
                            val updatedUsuario = usuario.copy(rol = selectedRole)
                            viewModel.updateUsuario(updatedUsuario)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Persona actualizada correctamente", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Error al guardar: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun validateInputs(
        identificacion: String, nombres: String, apellidos: String, email: String,
        telefono: String, direccion: String, fechaNacimiento: String
    ): Boolean {
        if (identificacion.isEmpty() || nombres.isEmpty() || apellidos.isEmpty() || email.isEmpty() ||
            telefono.isEmpty() || direccion.isEmpty() || fechaNacimiento.isEmpty()
        ) {
            Toast.makeText(requireContext(), "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validate email format
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(requireContext(), "Por favor ingrese un email válido", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
}