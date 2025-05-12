package org.anaphygon.data.model

import kotlinx.serialization.Serializable

// src/main/kotlin/data/model/FileMeta.kt
@Serializable
data class FileMeta(
    val id: String,
    val fileName: String,
    val storedFileName: String,
    val contentType: String,
    val size: Long,
    val uploadDate: Long,
    val userId: String? = null,
    val isPublic: Boolean = false,
    val moderationStatus: String = "PENDING"
)

// You can also add this enum if desired
@Serializable
enum class ModerationStatus {
    PENDING,
    APPROVED,
    REJECTED;

    companion object {
        fun fromString(value: String?): ModerationStatus {
            return values().find { it.name == value } ?: PENDING
        }
    }
}