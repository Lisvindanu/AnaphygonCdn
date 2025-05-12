package org.anaphygon.module.file

import org.anaphygon.config.ApplicationConfig
import org.anaphygon.util.Logger
import io.ktor.http.content.*

class FileValidator {
    private val logger = Logger("FileValidator")
    private val maxFileSize = ApplicationConfig.maxFileSize

    // Allowed file extensions
    private val allowedExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp",
        "pdf", "doc", "docx", "xls", "xlsx",
        "zip", "rar", "tar", "gz",
        "mp3", "mp4", "avi", "mov"
        // Add more as needed
    )

    fun validateFile(part: PartData.FileItem): ValidationResult {
        val fileName = part.originalFileName ?: return ValidationResult(false, "Invalid file name")

        // Check file extension
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in allowedExtensions) {
            return ValidationResult(false, "File type not allowed")
        }

        // Check file size
        val fileBytes = part.streamProvider().readBytes()
        if (fileBytes.size > maxFileSize) {
            return ValidationResult(false, "File size exceeds maximum allowed size (${maxFileSize / 1024 / 1024}MB)")
        }

        return ValidationResult(true, "Valid file")
    }
}

data class ValidationResult(val isValid: Boolean, val message: String)