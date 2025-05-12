// src/main/kotlin/org/anaphygon/auth/UserService.kt
package org.anaphygon.auth

import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val passwordHash: String,
    val role: String = "user" // Can be "admin", "user", etc.
)

class UserService {
    private val users = mutableMapOf<String, User>()

    init {
        // Add a default admin user
        val adminId = UUID.randomUUID().toString()
        val adminPasswordHash = BCrypt.hashpw("admin", BCrypt.gensalt())
        users[adminId] = User(adminId, "admin", adminPasswordHash, "admin")
    }

    fun registerUser(username: String, password: String, role: String = "user"): User {
        val existingUser = users.values.find { it.username == username }
        if (existingUser != null) {
            throw IllegalArgumentException("Username already exists")
        }

        val id = UUID.randomUUID().toString()
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = User(id, username, passwordHash, role)
        users[id] = user
        return user
    }

    fun validateUser(username: String, password: String): User? {
        val user = users.values.find { it.username == username }
        return if (user != null && BCrypt.checkpw(password, user.passwordHash)) {
            user
        } else {
            null
        }
    }

    fun getUserById(id: String): User? = users[id]
}