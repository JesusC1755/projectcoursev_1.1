package com.example.tareamov.data.dao

import androidx.room.*
import com.example.tareamov.data.entity.Usuario

@Dao
interface UsuarioDao {
    // Cambia este método para recibir la contraseña ya encriptada
    @Query("SELECT * FROM usuarios WHERE usuario = :username AND contrasena = :passwordHash")
    suspend fun login(username: String, passwordHash: String): Usuario?

    @Query("SELECT * FROM usuarios")
    suspend fun getAllUsuarios(): List<Usuario>

    @Query("SELECT * FROM usuarios WHERE rol = :role")
    suspend fun getUsuariosByRole(role: String): List<Usuario>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsuario(usuario: Usuario): Long

    @Update
    suspend fun updateUsuario(usuario: Usuario)

    @Delete
    suspend fun deleteUsuario(usuario: Usuario)

    @Query("SELECT * FROM usuarios WHERE persona_id = :personaId")
    suspend fun getUsuarioByPersonaId(personaId: Long): Usuario?

    @Query("SELECT COUNT(*) FROM usuarios")
    suspend fun getUserCount(): Int

    @Query("SELECT COUNT(*) FROM usuarios WHERE rol = :role")
    suspend fun getUserCountByRole(role: String): Int

    @Query("SELECT * FROM usuarios WHERE usuario = :username LIMIT 1")
    suspend fun getUsuarioByUsername(username: String): Usuario?

    @Query("SELECT persona_id FROM usuarios")
    suspend fun getAllUserPersonaIds(): List<Long>

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    suspend fun getUsuarioById(id: Long): Usuario?

    @Query("UPDATE usuarios SET rol = :role WHERE id = :userId")
    suspend fun updateUserRole(userId: Long, role: String)
}