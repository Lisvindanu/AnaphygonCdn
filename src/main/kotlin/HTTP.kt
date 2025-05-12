//// src/main/kotlin/org/anaphygon/HTTP.kt

// src/main/kotlin/org/anaphygon/HTTP.kt
package org.anaphygon

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
    }

    install(CORS) {
        // For development, allow specific origins instead of any host
        // anyHost()
        allowHost("localhost:8080")
        allowHost("127.0.0.1:8080")
        allowHost("localhost:3000")  // If you're using a separate frontend dev server

        // For production, uncomment this and use SecureConfig
        // allowOrigins { origin -> origin in SecureConfig.allowedOrigins }

        // Allow these HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Head)

        // Allow specific headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader("X-XSRF-TOKEN")
        allowHeader("X-Requested-With")

        // Expose headers that clients are allowed to read
        exposeHeader(HttpHeaders.Authorization)
        exposeHeader("X-XSRF-TOKEN")
        exposeHeader(HttpHeaders.ContentType)

        // Allow credentials (cookies)
        allowCredentials = true

        // Allow all headers
        allowHeaders { true }

        // Set max age for preflight requests cache (1 hour)
        maxAgeInSeconds = 3600
    }
}

//package org.anaphygon
//
//import io.ktor.http.*
//import io.ktor.server.application.*
//import io.ktor.server.plugins.cors.routing.*
//import io.ktor.server.plugins.defaultheaders.*
//import org.anaphygon.config.SecureConfig
//
//fun Application.configureHTTP() {
//    install(DefaultHeaders) {
//        header("X-Engine", "Ktor")
//        header("X-Content-Type-Options", "nosniff")
//        header("X-Frame-Options", "DENY")
//        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
//    }
//
//    install(CORS) {
//        // Only allow specific origins in production
//        allowOrigins { origin -> origin in SecureConfig.allowedOrigins }
//
//        // Allow these HTTP methods
//        allowMethod(HttpMethod.Get)
//        allowMethod(HttpMethod.Post)
//        allowMethod(HttpMethod.Put)
//        allowMethod(HttpMethod.Delete)
//        allowMethod(HttpMethod.Options)
//        allowMethod(HttpMethod.Patch)
//        allowMethod(HttpMethod.Head)
//
//        // Allow specific headers
//        allowHeader(HttpHeaders.Authorization)
//        allowHeader(HttpHeaders.ContentType)
//        allowHeader(HttpHeaders.Accept)
//        allowHeader("X-XSRF-TOKEN")
//        allowHeader("X-Requested-With")
//
//        // Expose headers that clients are allowed to read
//        exposeHeader(HttpHeaders.Authorization)
//        exposeHeader("X-XSRF-TOKEN")
//        exposeHeader(HttpHeaders.ContentType)
//
//        // Allow credentials (cookies)
//        allowCredentials = true
//
//        // Set max age for preflight requests cache (1 hour)
//        maxAgeInSeconds = 3600
//    }
//}