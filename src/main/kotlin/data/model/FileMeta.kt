package org.anaphygon.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FileMeta(
    val id: String,
    val fileName: String,
    val storedFileName: String,
    val contentType: String,
    val size: Long,
    val uploadDate: Long,
    val userId: String? = null
)