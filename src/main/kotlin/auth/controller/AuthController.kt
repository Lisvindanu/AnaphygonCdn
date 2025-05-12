// src/main/kotlin/org/anaphygon/auth/controller/AuthController.kt
package org.anaphygon.auth.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import org.anaphygon.auth.model.User
import org.anaphygon.auth.service.JwtService
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.config.SecureConfig
import org.anaphygon.util.Logger
import org.anaphygon.util.ValidationUtils
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.fixedRateTimer

@Serializable
data class LoginRequest(val usernameOrEmail: String, val password: String)

@Serializable
data class RegisterRequest(val username: String, val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val userId: String, val username: String, val roles: Set<String>)

@Serializable
data class UserListItem(
    val id: String,
    val username: String,
    val email: String,
    val roles: Set<String>,
    val active: Boolean,
    val createdAt: Long,
    val lastLogin: Long?
)

class AuthController(
    private val userRoleService: UserRoleService,
    private val jwtService: JwtService
) {
    private val logger = Logger("AuthController")

    // Login attempt tracking - In production, use a distributed cache or database
    private val loginAttempts = ConcurrentHashMap<String, LoginAttemptInfo>()

    // Clean up old login attempts periodically
    init {
        val cleanupIntervalMs = 60 * 60 * 1000L // 1 hour
        fixedRateTimer("login-attempts-cleanup", daemon = true, initialDelay = cleanupIntervalMs, period = cleanupIntervalMs) {
            cleanupLoginAttempts()
        }
    }

    private fun cleanupLoginAttempts() {
        val currentTime = System.currentTimeMillis()
        val lockoutDurationMs = SecureConfig.lockoutDurationMinutes * 60 * 1000L

        // Remove entries older than lockout duration
        loginAttempts.entries.removeIf { entry ->
            currentTime - entry.value.lastAttemptTime > lockoutDurationMs
        }

        logger.info("Cleaned up expired login attempts. Remaining: ${loginAttempts.size}")
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun login(call: ApplicationCall) {
        try {
            logger.info("Login attempt received")
            val request = call.receive<LoginRequest>()

            // Pre-validate input
            if (request.usernameOrEmail.isBlank() || request.password.isBlank()) {
                sendErrorResponse(call, "Username/email and password cannot be empty", HttpStatusCode.BadRequest)
                return
            }

            // Check if account is locked
            val identifier = request.usernameOrEmail.lowercase()
            val attemptInfo = loginAttempts.getOrPut(identifier) { LoginAttemptInfo() }

            if (isAccountLocked(attemptInfo)) {
                val lockoutMinutes = SecureConfig.lockoutDurationMinutes
                val remainingLockoutMs = (attemptInfo.lastAttemptTime + (lockoutMinutes * 60 * 1000L)) - System.currentTimeMillis()
                val remainingMinutes = (remainingLockoutMs / 60000) + 1  // Round up to nearest minute

                sendErrorResponse(
                    call,
                    "Account is temporarily locked due to too many failed login attempts. Please try again in $remainingMinutes minutes.",
                    HttpStatusCode.TooManyRequests
                )
                return
            }

            logger.info("Authenticating user: ${request.usernameOrEmail}")
            val user = userRoleService.authenticateUser(request.usernameOrEmail, request.password)

            if (user != null) {
                // Authentication succeeded, reset login attempts
                loginAttempts.remove(identifier)

                logger.info("User authenticated successfully: ${user.username} with roles: ${user.roles}")
                val token = jwtService.generateToken(user)

                call.respond(HttpStatusCode.OK, buildAuthResponse(user, token))
            } else {
                // Authentication failed, increment attempt counter
                attemptInfo.attempts.incrementAndGet()
                attemptInfo.lastAttemptTime = System.currentTimeMillis()

                logger.info("Authentication failed for user: ${request.usernameOrEmail}, attempt ${attemptInfo.attempts.get()}")

                val maxAttempts = SecureConfig.maxLoginAttempts
                val remainingAttempts = maxAttempts - attemptInfo.attempts.get()

                val errorMessage = if (remainingAttempts <= 0) {
                    "Account locked due to too many failed attempts. Try again later."
                } else {
                    "Invalid username/email or password. Remaining attempts: $remainingAttempts"
                }

                sendErrorResponse(call, errorMessage, HttpStatusCode.Unauthorized)
            }
        } catch (e: Exception) {
            logger.error("Login error: ${e.message}", e)
            sendErrorResponse(call, "An error occurred: ${e.message}", HttpStatusCode.InternalServerError)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun register(call: ApplicationCall) {
        try {
            logger.info("Registration attempt received")
            val request = call.receive<RegisterRequest>()

            // Validate inputs
            val usernameValidation = ValidationUtils.validateUsername(request.username)
            if (!usernameValidation.isValid) {
                sendErrorResponse(call, usernameValidation.message, HttpStatusCode.BadRequest)
                return
            }

            val emailValidation = ValidationUtils.validateEmail(request.email)
            if (!emailValidation.isValid) {
                sendErrorResponse(call, emailValidation.message, HttpStatusCode.BadRequest)
                return
            }

            val passwordValidation = ValidationUtils.validatePassword(request.password)
            if (!passwordValidation.isValid) {
                sendErrorResponse(call, passwordValidation.message, HttpStatusCode.BadRequest)
                return
            }

            logger.info("Registering new user: ${request.username}")

            val user = userRoleService.createUser(
                username = request.username,
                email = request.email,
                password = request.password
            )

            val token = jwtService.generateToken(user)
            logger.info("User registered successfully: ${user.username}")

            call.respond(HttpStatusCode.Created, buildAuthResponse(user, token))

        } catch (e: IllegalArgumentException) {
            logger.error("Registration validation error: ${e.message}")
            sendErrorResponse(call, e.message ?: "Registration failed", HttpStatusCode.BadRequest)
        } catch (e: Exception) {
            logger.error("Registration error: ${e.message}", e)
            sendErrorResponse(call, "An error occurred: ${e.message}", HttpStatusCode.InternalServerError)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getCsrfToken(call: ApplicationCall) {
        // Log the request
        logger.info("CSRF token request received from ${call.request.origin.remoteHost}, origin: ${call.request.headers["Origin"]}")

        // Set CSRF token cookie
        val token = UUID.randomUUID().toString()
        call.response.cookies.append(
            Cookie(
                name = "XSRF-TOKEN",
                value = token,
                secure = false, // Set to true in production with HTTPS
                httpOnly = false, // Must be false for JS to read it
                path = "/"
            )
        )

        logger.info("CSRF token set in cookie: $token")

        // The CSRF token is automatically set in cookie by the CSRF protection plugin
        val jsonResponse = buildJsonObject {
            put("success", true)
            put("data", JsonPrimitive("CSRF token set"))
            put("error", null)
        }
        call.respond(HttpStatusCode.OK, jsonResponse)
    }

//    @OptIn(ExperimentalSerializationApi::class)
//    suspend fun getCsrfToken(call: ApplicationCall) {
//        // The CSRF token is automatically set in cookie by the CSRF protection plugin
//        val jsonResponse = buildJsonObject {
//            put("success", true)
//            put("data", JsonPrimitive("CSRF token set"))
//            put("error", null)
//        }
//        call.respond(HttpStatusCode.OK, jsonResponse)
//    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getAllUsers(call: ApplicationCall) {
        try {
            // Check if user is admin
            val principal = call.principal<JWTPrincipal>()
            val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()

            if (!roles.contains("ADMIN")) {
                logger.info("Unauthorized attempt to access user list")
                sendErrorResponse(call, "Admin access required", HttpStatusCode.Forbidden)
                return
            }

            logger.info("Admin requested user list")
            val users = userRoleService.getAllUsers()

            // Map to safe data transfer object
            val userList = users.map { user ->
                UserListItem(
                    id = user.id,
                    username = user.username,
                    email = user.email,
                    roles = user.roles,
                    active = user.active,
                    createdAt = user.createdAt,
                    lastLogin = user.lastLogin
                )
            }

            // Create a JSON response with user list
            val jsonResponse = buildJsonObject {
                put("success", true)
                put("data", buildJsonObject {
                    put("users", buildJsonObject {
                        userList.forEachIndexed { index, user ->
                            put(index.toString(), buildJsonObject {
                                put("id", user.id)
                                put("username", user.username)
                                put("email", user.email)
                                put("active", user.active)
                                put("createdAt", user.createdAt)
                                if (user.lastLogin != null) {
                                    put("lastLogin", user.lastLogin)
                                } else {
                                    put("lastLogin", null)
                                }
                                put("roles", buildJsonObject {
                                    user.roles.forEachIndexed { roleIndex, role ->
                                        put(roleIndex.toString(), role)
                                    }
                                })
                            })
                        }
                    })
                })
                put("error", null)
            }

            logger.info("Returning list of ${userList.size} users")
            call.respond(HttpStatusCode.OK, jsonResponse)
        } catch (e: Exception) {
            logger.error("Error retrieving user list: ${e.message}", e)
            sendErrorResponse(call, "Error retrieving user list: ${e.message}", HttpStatusCode.InternalServerError)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun buildAuthResponse(user: User, token: String) = buildJsonObject {
        put("success", true)
        put("data", buildJsonObject {
            put("token", token)
            put("userId", user.id)
            put("username", user.username)
            put("roles", buildJsonObject {
                user.roles.forEachIndexed { index, role ->
                    put(index.toString(), role)
                }
            })
        })
        put("error", null)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun sendErrorResponse(call: ApplicationCall, message: String, statusCode: HttpStatusCode) {
        val errorResponse = buildJsonObject {
            put("success", false)
            put("data", null)
            put("error", JsonPrimitive(message))
        }
        call.respond(statusCode, errorResponse)
    }

    private fun isAccountLocked(attemptInfo: LoginAttemptInfo): Boolean {
        val maxAttempts = SecureConfig.maxLoginAttempts
        val lockoutDurationMs = SecureConfig.lockoutDurationMinutes * 60 * 1000L

        if (attemptInfo.attempts.get() >= maxAttempts) {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastAttempt = currentTime - attemptInfo.lastAttemptTime

            // If within lockout period, account is locked
            if (timeSinceLastAttempt < lockoutDurationMs) {
                return true
            } else {
                // Reset attempts after lockout period
                attemptInfo.attempts.set(0)
                return false
            }
        }
        return false
    }

    private class LoginAttemptInfo {
        val attempts = AtomicInteger(0)
        var lastAttemptTime: Long = System.currentTimeMillis()
    }
}