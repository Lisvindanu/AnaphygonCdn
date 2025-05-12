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

    // Ensure styles and scripts directories exist
    val stylesDir = "styles"
    val scriptsDir = "scripts"
    File(stylesDir).mkdirs()
    File(scriptsDir).mkdirs()

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
        
        // Serve verify.html for email verification
        get("/verify") {
            call.respondFile(File("src/main/resources/static/verify.html"))
        }

        // Serve static resources from styles directory
        static("/styles") {
            files(stylesDir)
        }

        // Serve static resources from scripts directory
        static("/scripts") {
            files(scriptsDir)
        }

        // Serve files directly from the uploads directory
        static("/cdn") {
            files(uploadsDir)
        }
        
        // Serve other static resources from the resources directory
        static("/") {
            resources("static")
        }
    }
}