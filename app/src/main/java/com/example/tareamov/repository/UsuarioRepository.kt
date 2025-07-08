package com.example.tareamov.repository

import com.example.tareamov.data.dao.UsuarioDao
import com.example.tareamov.data.entity.Usuario
import com.example.tareamov.util.CryptoUtils

class UsuarioRepository(private val usuarioDao: UsuarioDao) {
    suspend fun login(username: String, password: String): Usuario? {
        // Instead of using the DAO's login method which checks both username and password,
        // we'll just get the user by username and check the password in the ViewModel
        return usuarioDao.getUsuarioByUsername(username)
    }

    suspend fun insert(usuario: Usuario): Long {
        // Hash the password before saving
        val hashedUsuario = usuario.copy(contrasena = CryptoUtils.sha256(usuario.contrasena))
        return usuarioDao.insertUsuario(hashedUsuario)
    }

    suspend fun update(usuario: Usuario) {
        // Hash the password before updating
        val hashedUsuario = usuario.copy(contrasena = CryptoUtils.sha256(usuario.contrasena))
        usuarioDao.updateUsuario(hashedUsuario)
    }

    suspend fun delete(usuario: Usuario) {
        usuarioDao.deleteUsuario(usuario)
    }

    suspend fun getUsuarioByPersonaId(personaId: Long): Usuario? {
        return usuarioDao.getUsuarioByPersonaId(personaId)
    }

    suspend fun getAllUserPersonaIds(): List<Long> {
        return usuarioDao.getAllUserPersonaIds()
    }

    suspend fun getUsuarioById(id: Long): Usuario? {
        return usuarioDao.getUsuarioById(id)
    }

    suspend fun getUsuarioByUsername(username: String): Usuario? {
        return usuarioDao.getUsuarioByUsername(username)
    }

    // Add the missing findByUsername method
    suspend fun findByUsername(username: String): Usuario? {
        return usuarioDao.getUsuarioByUsername(username)
    }

    suspend fun getAllUsuarios(): List<Usuario> {
        return usuarioDao.getAllUsuarios()
    }

    suspend fun getUsuariosByRole(role: String): List<Usuario> {
        return usuarioDao.getUsuariosByRole(role)
    }

    suspend fun getUserCountByRole(role: String): Int {
        return usuarioDao.getUserCountByRole(role)
    }

    suspend fun updateUserRole(userId: Long, role: String): Boolean {
        val usuario = getUsuarioById(userId)
        return if (usuario != null) {
            usuarioDao.updateUserRole(userId, role)
            true
        } else {
            false
        }
    }

    // Update method to update user profile with more comprehensive information
    suspend fun updateUserProfile(userId: Long, newUsername: String, displayName: String? = null, bio: String? = null, role: String? = null): Boolean {
        val usuario = getUsuarioById(userId)
        return if (usuario != null) {
            val updatedUsuario = usuario.copy(
                usuario = newUsername,
                rol = role ?: usuario.rol
            )
            update(updatedUsuario)
            true
        } else {
            false
        }
    }

    // Helper methods to check user roles
    suspend fun isTeacher(userId: Long): Boolean {
        val usuario = getUsuarioById(userId)
        return usuario?.rol == Usuario.ROL_MAESTRO
    }

    suspend fun isStudent(userId: Long): Boolean {
        val usuario = getUsuarioById(userId)
        return usuario?.rol == Usuario.ROL_ESTUDIANTE
    }

    suspend fun isAdmin(userId: Long): Boolean {
        val usuario = getUsuarioById(userId)
        return usuario?.rol == Usuario.ROL_ADMIN
    }

    // Add a new method to migrate any existing "usuario" roles to "estudiante"
    suspend fun migrateUserRoles() {
        val allUsers = getAllUsuarios()
        for (user in allUsers) {
            if (user.rol == "usuario") {
                updateUserRole(user.id, Usuario.ROL_ESTUDIANTE)
            }
        }
    }

    // Add a method to update just the password
    suspend fun updatePassword(userId: Long, hashedPassword: String): Boolean {
        val usuario = getUsuarioById(userId)
        return if (usuario != null) {
            val updatedUsuario = usuario.copy(contrasena = hashedPassword)
            usuarioDao.updateUsuario(updatedUsuario)
            true
        } else {
            false
        }
    }
}