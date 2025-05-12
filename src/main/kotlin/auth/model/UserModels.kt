package org.anaphygon.auth.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class User(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val email: String,
    val passwordHash: String,
    val roles: Set<String> = setOf("USER"),
    val active: Boolean = true,
    val verified: Boolean = false,  // Added verified field
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long? = null
)

@Serializable
data class Role(
    val name: String,
    val description: String,
    val permissions: Set<Permission>
)

@Serializable
enum class Permission {
    // File permissions
    VIEW_FILES,
    UPLOAD_FILES,
    DELETE_FILES,
    SHARE_FILES,
    MODIFY_FILES,

    // User management permissions
    VIEW_USERS,
    CREATE_USERS,
    DELETE_USERS,
    MODIFY_USERS,

    // Role management permissions
    VIEW_ROLES,
    CREATE_ROLES,
    DELETE_ROLES,
    MODIFY_ROLES,

    // System permissions
    VIEW_METRICS,
    MODIFY_SETTINGS,

    // Versioning permissions
    VIEW_FILE_HISTORY,
    RESTORE_FILE_VERSIONS
}