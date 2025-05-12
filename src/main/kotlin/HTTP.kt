// src/main/kotlin/org/anaphygon/HTTP.kt
package org.anaphygon

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }

    install(CORS) {
        // Allow requests from any origin during development
        // In production, replace with specific origins
        anyHost()

        // Allow common HTTP methods
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

        // Allow all request headers
        allowHeaders { true }

        // Set max age for preflight requests cache (1 hour)
        maxAgeInSeconds = 3600
    }
}