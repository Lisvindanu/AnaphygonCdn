package org.anaphygon.config

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*

object ApplicationConfig {
    private val dotenv = dotenv {
        ignoreIfMissing = true
    }

    val port: Int = dotenv["PORT"]?.toIntOrNull() ?: 8080
    val uploadsDir: String = dotenv["UPLOADS_DIR"] ?: "uploads"
    val maxFileSize: Long = dotenv["MAX_FILE_SIZE"]?.toLongOrNull() ?: 10_485_760 // 10MB default

    fun init(environment: ApplicationEnvironment) {
        // Can be used to load configuration from application.yaml if needed
    }
}