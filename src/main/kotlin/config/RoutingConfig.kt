// src/main/kotlin/org/anaphygon/config/RoutingConfig.kt
package org.anaphygon.config

import org.anaphygon.auth.authRoutes
import org.anaphygon.module.file.fileRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        authRoutes()
        fileRoutes()
    }
}