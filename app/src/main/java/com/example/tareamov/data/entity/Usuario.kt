package com.example.tareamov.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usuarios",
    foreignKeys = [
        ForeignKey(
            entity = Persona::class,
            parentColumns = ["id"],
            childColumns = ["persona_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("persona_id")]
)
data class Usuario(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val usuario: String = "",
    val contrasena: String = "",
    val persona_id: Long = 0,
    val rol: String = ROL_ESTUDIANTE // Changed default from "usuario" to "estudiante"
) {
    // Property to match the reference in VideoHomeFragment
    val personaId: Long
        get() = persona_id

    // Add nombreUsuario property that returns the usuario field
    val nombreUsuario: String
        get() = usuario

    companion object {
        // Removed ROL_USUARIO constant
        const val ROL_MAESTRO = "maestro"
        const val ROL_ESTUDIANTE = "estudiante"
        const val ROL_ADMIN = "admin"
    }
}