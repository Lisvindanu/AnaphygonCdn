// src/main/kotlin/org/anaphygon/module/file/FileValidator.kt
package org.anaphygon.module.file

import org.anaphygon.config.SecureConfig
import org.anaphygon.util.Logger
import org.anaphygon.util.ValidationUtils
import io.ktor.http.content.*
import java.nio.file.Path
import java.nio.file.Paths

class FileValidator {
    private val logger = Logger("FileValidator")
    private val maxFileSize = SecureConfig.maxFileSize

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

        // Validate filename format and security
        val filenameValidation = ValidationUtils.validateFileName(fileName)
        if (!filenameValidation.isValid) {
            return ValidationResult(false, filenameValidation.message)
        }

        // Check for path traversal attacks
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            logger.warn("Path traversal attempt detected with filename: $fileName")
            return ValidationResult(false, "Invalid file name")
        }

        // Check file extension
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in allowedExtensions) {
            return ValidationResult(false, "File type not allowed")
        }

        // Check file size
        try {
            val fileBytes = part.streamProvider().readBytes()
            if (fileBytes.size > maxFileSize) {
                return ValidationResult(false, "File size exceeds maximum allowed size (${maxFileSize / 1024 / 1024}MB)")
            }
            return ValidationResult(true, "Valid file")
        } catch (e: Exception) {
            logger.error("Error validating file: ${e.message}", e)
            return ValidationResult(false, "Error validating file")
        }
    }

    fun validateFilePath(baseDir: String, fileName: String): ValidationResult {
        // Normalize paths to prevent path traversal
        val basePath = Paths.get(baseDir).normalize().toAbsolutePath()
        val requestedPath = Paths.get(baseDir, fileName).normalize().toAbsolutePath()

        // Check if the requested path is still within the base directory
        if (!requestedPath.startsWith(basePath)) {
            logger.warn("Path traversal attempt detected: $fileName attempts to access outside of $baseDir")
            return ValidationResult(false, "Invalid file path")
        }

        return ValidationResult(true, "Valid file path")
    }
}

data class ValidationResult(val isValid: Boolean, val message: String)