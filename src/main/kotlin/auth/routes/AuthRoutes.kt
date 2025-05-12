// src/main/kotlin/org/anaphygon/auth/AuthRoutes.kt
package org.anaphygon.auth.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.anaphygon.auth.JwtConfig
import org.anaphygon.auth.UserService
import org.anaphygon.util.ResponseWrapper

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val userId: String, val username: String, val role: String)

fun Route.authRoutes() {
    val userService = UserService()

    route("/api/auth") {
        // Add CSRF token endpoint
        get("/csrf") {
            // The token is automatically set by the CsrfProtection plugin
            // Just respond with success
            call.respond(HttpStatusCode.OK, ResponseWrapper.success("CSRF token set in cookie"))
        }

        // Enable CORS for OPTIONS requests
        options("/register") {
            call.respond(HttpStatusCode.OK)
        }

        options("/login") {
            call.respond(HttpStatusCode.OK)
        }

        post("/register") {
            try {
                val request = call.receive<RegisterRequest>()
                val user = userService.registerUser(request.username, request.password)
                val token = JwtConfig.createToken(user.id, user.role)

                call.respond(HttpStatusCode.Created, ResponseWrapper.success(
                    AuthResponse(token, user.id, user.username, user.role)
                ))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error(e.message ?: "Registration failed"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Internal server error"))
            }
        }

        post("/login") {
            try {
                val request = call.receive<LoginRequest>()
                val user = userService.validateUser(request.username, request.password)

                if (user != null) {
                    val token = JwtConfig.createToken(user.id, user.role)
                    call.respond(HttpStatusCode.OK, ResponseWrapper.success(
                        AuthResponse(token, user.id, user.username, user.role)
                    ))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, ResponseWrapper.error("Invalid credentials"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Internal server error"))
            }
        }
    }
}