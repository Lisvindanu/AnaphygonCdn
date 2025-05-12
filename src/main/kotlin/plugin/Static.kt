// src/main/kotlin/org/anaphygon/plugin/Static.kt
package org.anaphygon.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureStatic() {
    // Ensure uploads directory exists
    val uploadsDir = "uploads"
    File(uploadsDir).mkdirs()

    // Create static directory if it doesn't exist
    val staticDir = "static"
    File(staticDir).mkdirs()

    // Add interceptor for adding cache headers
    intercept(ApplicationCallPipeline.Plugins) {
        call.response.pipeline.intercept(io.ktor.server.response.ApplicationSendPipeline.Before) {
            val contentType = call.response.headers[HttpHeaders.ContentType]

            if (contentType != null) {
                // For images, cache for 7 days
                if (contentType.startsWith("image/")) {
                    call.response.header(HttpHeaders.CacheControl, "max-age=${60 * 60 * 24 * 7}, public")
                }
                // For documents (PDF, etc.), cache for 1 day
                else if (contentType.startsWith("application/")) {
                    call.response.header(HttpHeaders.CacheControl, "max-age=${60 * 60 * 24}, public")
                }
                // For other static content, cache for 1 hour
                else {
                    call.response.header(HttpHeaders.CacheControl, "max-age=${60 * 60}, public")
                }
            }
        }
    }

    routing {
        // Serve index.html at the root
        get("/") {
            call.respondFile(File("index.html"))
        }

        // Static resources
        static("/static") {
            staticBasePackage = "static"
            resources(".")
        }

        // Serve files directly from the uploads directory
        static("/cdn") {
            files(uploadsDir)
        }
    }
}