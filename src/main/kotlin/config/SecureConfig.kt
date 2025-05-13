package org.anaphygon.config

import io.github.cdimascio.dotenv.dotenv
import java.io.File

object SecureConfig {
    private val dotenv = dotenv {
        ignoreIfMissing = true
        directory = findEnvDirectory()
    }

    // Database config
    val dbUrl: String = dotenv["DB_URL"]
        ?: "jdbc:h2:file:./data/cdn_db;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
    val dbUser: String = dotenv["DB_USER"] ?: "root"
    val dbPassword: String = dotenv["DB_PASSWORD"] ?: ""

    // JWT config
    val jwtSecret: String = dotenv["JWT_SECRET"]
        ?: throw IllegalStateException("JWT_SECRET environment variable is required")
    val jwtIssuer: String = dotenv["JWT_ISSUER"] ?: "anaphygon-cdn"
    val jwtAudience: String = dotenv["JWT_AUDIENCE"] ?: "anaphygon-cdn-users"
    val jwtExpirationMs: Long = dotenv["JWT_EXPIRATION_MS"]?.toLongOrNull() ?: (3600000L * 24 * 7) // 7 days

    // File storage config
    val uploadsDir: String = dotenv["UPLOADS_DIR"] ?: "uploads"
    val maxFileSize: Long = dotenv["MAX_FILE_SIZE"]?.toLongOrNull() ?: 10_485_760 // 10MB

    // CORS config
    val allowedOrigins: List<String> = dotenv["ALLOWED_ORIGINS"]
        ?.split(",")?.map { it.trim() }
        ?: listOf("http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:3000")

    // Password policy
    val minPasswordLength: Int = dotenv["MIN_PASSWORD_LENGTH"]?.toIntOrNull() ?: 8
    val requireSpecialChars: Boolean = dotenv["REQUIRE_SPECIAL_CHARS"]?.toBoolean() ?: true
    val requireNumbers: Boolean = dotenv["REQUIRE_NUMBERS"]?.toBoolean() ?: true
    val requireUppercase: Boolean = dotenv["REQUIRE_UPPERCASE"]?.toBoolean() ?: true

    // Account lockout
    val maxLoginAttempts: Int = dotenv["MAX_LOGIN_ATTEMPTS"]?.toIntOrNull() ?: 5
    val lockoutDurationMinutes: Int = dotenv["LOCKOUT_DURATION_MINUTES"]?.toIntOrNull() ?: 30

    // Email configuration - Updated for Hostinger
    val emailHost: String = dotenv["EMAIL_HOST"] ?: "smtp.hostinger.com"
    val emailPort: String = dotenv["EMAIL_PORT"] ?: "465" // Updated to use SSL port
    val emailUsername: String = dotenv["EMAIL_USERNAME"] ?: "anaphygon@vinmedia.my.id"
    val emailPassword: String = dotenv["EMAIL_PASSWORD"] ?: ""
    val emailSender: String = dotenv["EMAIL_SENDER"] ?: "anaphygon@vinmedia.my.id"
    val appBaseUrl: String = dotenv["APP_BASE_URL"] ?: "http://localhost:8080"

    private fun findEnvDirectory(): String {
        // Try to find .env file in various locations
        val locations = listOf(".", "..", "../..", "../../..")
        for (loc in locations) {
            if (File("$loc/.env").exists()) {
                return loc
            }
        }
        return "."
    }
}