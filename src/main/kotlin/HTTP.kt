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
        // Allow requests from any origin
        anyHost()

        // Allow common HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Head)

        // Allow common headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader("X-XSRF-TOKEN")
        allowHeader("X-Requested-With")

        // Allow credentials (cookies)
        allowCredentials = true

        // Allow all request headers
        allowHeaders { true }

        // Set max age for preflight requests cache
        maxAgeInSeconds = 3600
    }
}