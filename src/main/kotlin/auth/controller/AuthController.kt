// src/main/kotlin/org/anaphygon/auth/controller/AuthController.kt
package org.anaphygon.auth.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.anaphygon.auth.model.User
import org.anaphygon.auth.service.JwtService
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.util.ResponseWrapper

@Serializable
data class LoginRequest(val usernameOrEmail: String, val password: String)

@Serializable
data class RegisterRequest(val username: String, val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val userId: String, val username: String, val roles: Set<String>)

class AuthController(
    private val userRoleService: UserRoleService,
    private val jwtService: JwtService
) {

    suspend fun login(call: ApplicationCall) {
        try {
            val request = call.receive<LoginRequest>()
            val user = userRoleService.authenticateUser(request.usernameOrEmail, request.password)

            if (user != null) {
                val token = jwtService.generateToken(user)

                call.respond(HttpStatusCode.OK, ResponseWrapper.success(
                    AuthResponse(
                        token = token,
                        userId = user.id,
                        username = user.username,
                        roles = user.roles
                    )
                ))
            } else {
                call.respond(HttpStatusCode.Unauthorized, ResponseWrapper.error("Invalid username/email or password"))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("An error occurred: ${e.message}"))
        }
    }

    suspend fun register(call: ApplicationCall) {
        try {
            val request = call.receive<RegisterRequest>()

            val user = userRoleService.createUser(
                username = request.username,
                email = request.email,
                password = request.password
            )

            val token = jwtService.generateToken(user)

            call.respond(HttpStatusCode.Created, ResponseWrapper.success(
                AuthResponse(
                    token = token,
                    userId = user.id,
                    username = user.username,
                    roles = user.roles
                )
            ))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error(e.message ?: "Registration failed"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("An error occurred: ${e.message}"))
        }
    }

    suspend fun getCsrfToken(call: ApplicationCall) {
        // The CSRF token is automatically set in cookie by the CSRF protection plugin
        call.respond(HttpStatusCode.OK, ResponseWrapper.success("CSRF token set"))
    }
}