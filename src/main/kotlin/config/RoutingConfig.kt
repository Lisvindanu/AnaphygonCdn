package org.anaphygon.config

import org.anaphygon.auth.routes.authRoutes
import org.anaphygon.auth.service.JwtService
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.email.EmailService
import org.anaphygon.module.file.fileRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun Application.configureRouting(
    userRoleService: UserRoleService,
    jwtService: JwtService,
    emailService: EmailService
) {
    routing {
        authRoutes(userRoleService, jwtService, emailService)
        fileRoutes()
    }
}