// src/main/kotlin/org/anaphygon/util/ValidationUtils.kt
package org.anaphygon.util

import org.anaphygon.config.SecureConfig
import java.nio.file.Paths
import java.util.regex.Pattern

object ValidationUtils {
    // Email validation pattern
    private val EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    )

    // Username validation pattern (alphanumeric and underscore)
    private val USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,50}$")

    // Password validation
    private val SPECIAL_CHARS_PATTERN = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]")
    private val NUMBERS_PATTERN = Pattern.compile("[0-9]")
    private val UPPERCASE_PATTERN = Pattern.compile("[A-Z]")

    // Sanitize file name pattern
    private val SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$")

    fun validateEmail(email: String): ValidationResult {
        if (email.isBlank()) return ValidationResult(false, "Email cannot be blank")
        if (email.length > 255) return ValidationResult(false, "Email is too long")
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ValidationResult(false, "Invalid email format")
        }
        return ValidationResult(true, "Valid email")
    }

    fun validateUsername(username: String): ValidationResult {
        if (username.isBlank()) return ValidationResult(false, "Username cannot be blank")
        if (username.length < 3) return ValidationResult(false, "Username must be at least 3 characters")
        if (username.length > 50) return ValidationResult(false, "Username cannot exceed 50 characters")
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return ValidationResult(false, "Username can only contain letters, numbers, and underscores")
        }
        return ValidationResult(true, "Valid username")
    }

    fun validatePassword(password: String): ValidationResult {
        if (password.isBlank()) return ValidationResult(false, "Password cannot be blank")

        val minLength = SecureConfig.minPasswordLength
        if (password.length < minLength) {
            return ValidationResult(false, "Password must be at least $minLength characters")
        }

        if (SecureConfig.requireSpecialChars && !SPECIAL_CHARS_PATTERN.matcher(password).find()) {
            return ValidationResult(false, "Password must contain at least one special character")
        }

        if (SecureConfig.requireNumbers && !NUMBERS_PATTERN.matcher(password).find()) {
            return ValidationResult(false, "Password must contain at least one number")
        }

        if (SecureConfig.requireUppercase && !UPPERCASE_PATTERN.matcher(password).find()) {
            return ValidationResult(false, "Password must contain at least one uppercase letter")
        }

        return ValidationResult(true, "Valid password")
    }

    fun validateFileName(fileName: String): ValidationResult {
        if (fileName.isBlank()) return ValidationResult(false, "Filename cannot be blank")
        if (fileName.length > 255) return ValidationResult(false, "Filename is too long")

        // Check for directory traversal attempts
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ValidationResult(false, "Invalid filename - contains forbidden characters")
        }

        if (!SAFE_FILENAME_PATTERN.matcher(fileName).matches()) {
            return ValidationResult(false, "Filename contains invalid characters")
        }

        return ValidationResult(true, "Valid filename")
    }

    fun validateFilePath(baseDir: String, fileName: String): ValidationResult {
        val basePath = Paths.get(baseDir).normalize().toAbsolutePath()
        val filePath = Paths.get(baseDir, fileName).normalize().toAbsolutePath()

        // Ensure the file is within the base directory
        if (!filePath.startsWith(basePath)) {
            return ValidationResult(false, "Invalid file path - path traversal detected")
        }

        return ValidationResult(true, "Valid file path")
    }

    data class ValidationResult(val isValid: Boolean, val message: String)
}