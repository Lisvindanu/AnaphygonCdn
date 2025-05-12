package org.anaphygon.auth.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.anaphygon.auth.controller.AuthController
import org.anaphygon.auth.controller.LoginRequest
import org.anaphygon.auth.controller.RegisterRequest
import org.anaphygon.auth.service.JwtService
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.email.EmailService
import org.anaphygon.util.ResponseWrapper
import org.anaphygon.util.ValidationUtils

fun Route.authRoutes(
    userRoleService: UserRoleService,
    jwtService: JwtService,
    emailService: EmailService
) {
    val authController = AuthController(userRoleService, jwtService, emailService)

    route("/api/auth") {
        // Public routes that don't require authentication

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

        // Email verification endpoint - GET for direct browser access
        get("/verify") {
            val token = call.parameters["token"]
            if (token == null) {
                call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("Token is required"))
                return@get
            }

            val success = userRoleService.verifyEmail(token)
            if (success) {
                call.respond(HttpStatusCode.OK, ResponseWrapper.success("Email verified successfully"))
            } else {
                call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("Invalid or expired token"))
            }
        }
        
        // Email verification endpoint - POST for API access
        post("/verify") {
            try {
                val request = call.receive<Map<String, String>>()
                val token = request["token"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ResponseWrapper.error("Token is required")
                )
                
                val success = userRoleService.verifyEmail(token)
                if (success) {
                    call.respond(HttpStatusCode.OK, ResponseWrapper.success("Email verified successfully"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("Invalid or expired token"))
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ResponseWrapper.error("Error processing verification: ${e.message}")
                )
            }
        }

        // Request password reset
        post("/forgot-password") {
            try {
                val request = call.receive<Map<String, String>>()
                val email = request["email"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ResponseWrapper.error("Email is required")
                )

                // Validate email format
                val emailValidation = ValidationUtils.validateEmail(email)
                if (!emailValidation.isValid) {
                    call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error(emailValidation.message))
                    return@post
                }

                val user = userRoleService.getUserByEmail(email)

                // Always return success even if user doesn't exist (for security)
                if (user != null) {
                    val token = userRoleService.createPasswordResetToken(user.id)
                    emailService.sendPasswordResetEmail(user.email, user.username, token)
                }

                call.respond(HttpStatusCode.OK, ResponseWrapper.success(
                    "If an account exists with that email, a password reset link has been sent"
                ))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ResponseWrapper.error("Error processing request: ${e.message}")
                )
            }
        }
        
        // Routes that require authentication
        authenticate("auth-jwt") {
            // Resend verification email
            post("/resend-verification") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ResponseWrapper.error("Authentication required"))

                val user = userRoleService.getUserById(userId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("User not found"))

                if (userRoleService.isUserVerified(userId)) {
                    call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("Email already verified"))
                    return@post
                }

                val token = userRoleService.createVerificationToken(userId)
                emailService.sendVerificationEmail(user.email, user.username, token)

                call.respond(HttpStatusCode.OK, ResponseWrapper.success("Verification email sent"))
            }

            // Admin routes
            get("/users") {
                authController.getAllUsers(call)
            }

            options("/users") {
                call.respond(HttpStatusCode.OK)
            }

            // Check verification status
            get("/verification-status") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ResponseWrapper.error("Authentication required"))

                val isVerified = userRoleService.isUserVerified(userId)
                call.respond(HttpStatusCode.OK, ResponseWrapper.success(mapOf("verified" to isVerified)))
            }
        }
    }
}