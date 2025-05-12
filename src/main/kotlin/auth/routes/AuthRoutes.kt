// src/main/kotlin/org/anaphygon/auth/AuthRoutes.kt
package org.anaphygon.auth.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import org.anaphygon.auth.controller.AuthController
import org.anaphygon.auth.controller.LoginRequest
import org.anaphygon.auth.controller.RegisterRequest
import org.anaphygon.auth.service.JwtService
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.util.ResponseWrapper

fun Route.authRoutes(userRoleService: UserRoleService, jwtService: JwtService) {
    val authController = AuthController(userRoleService, jwtService)

    route("/api/auth") {
        // Add CSRF token endpoint
        get("/csrf") {
            authController.getCsrfToken(call)
        }

        // Enable CORS for OPTIONS requests
        options("/register") {
            call.respond(HttpStatusCode.OK)
        }

        options("/login") {
            call.respond(HttpStatusCode.OK)
        }

        post("/register") {
            authController.register(call)
        }

        post("/login") {
            authController.login(call)
        }
        
        // Admin routes
        authenticate("auth-jwt") {
            get("/users") {
                authController.getAllUsers(call)
            }
            
            options("/users") {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}