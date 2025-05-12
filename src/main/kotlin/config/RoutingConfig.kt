// src/main/kotlin/org/anaphygon/config/RoutingConfig.kt
package org.anaphygon.config

import org.anaphygon.auth.routes.authRoutes
import org.anaphygon.auth.service.JwtService
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.module.file.fileRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun Application.configureRouting(userRoleService: UserRoleService, jwtService: JwtService) {
    routing {
        authRoutes(userRoleService, jwtService)
        fileRoutes()
    }
}