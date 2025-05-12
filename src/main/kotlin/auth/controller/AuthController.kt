// src/main/kotlin/org/anaphygon/auth/controller/AuthController.kt
package org.anaphygon.auth.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import org.anaphygon.auth.model.User
import org.anaphygon.auth.service.JwtService
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.util.Logger
import org.anaphygon.util.ResponseWrapper
import org.anaphygon.util.JsonResponseWrapper

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

    suspend fun login(call: ApplicationCall) {
        try {
            logger.info("Login attempt received")
            val request = call.receive<LoginRequest>()
            logger.info("Authenticating user: ${request.usernameOrEmail}")
            
            val user = userRoleService.authenticateUser(request.usernameOrEmail, request.password)

            if (user != null) {
                logger.info("User authenticated successfully: ${user.username} with roles: ${user.roles}")
                val token = jwtService.generateToken(user)

                val response = AuthResponse(
                    token = token,
                    userId = user.id,
                    username = user.username,
                    roles = user.roles
                )
                
                logger.info("Sending response with roles: ${response.roles}")

                // Create a JSON response using buildJsonObject
                val jsonResponse = buildJsonObject {
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

                call.respond(HttpStatusCode.OK, jsonResponse)
            } else {
                logger.info("Authentication failed for user: ${request.usernameOrEmail}")
                val errorResponse = buildJsonObject {
                    put("success", false)
                    put("data", null)
                    put("error", JsonPrimitive("Invalid username/email or password"))
                }
                call.respond(HttpStatusCode.Unauthorized, errorResponse)
            }
        } catch (e: Exception) {
            logger.error("Login error: ${e.message}", e)
            val errorResponse = buildJsonObject {
                put("success", false)
                put("data", null)
                put("error", JsonPrimitive("An error occurred: ${e.message}"))
            }
            call.respond(HttpStatusCode.InternalServerError, errorResponse)
        }
    }

    suspend fun register(call: ApplicationCall) {
        try {
            logger.info("Registration attempt received")
            val request = call.receive<RegisterRequest>()
            logger.info("Registering new user: ${request.username}")

            val user = userRoleService.createUser(
                username = request.username,
                email = request.email,
                password = request.password
            )

            val token = jwtService.generateToken(user)
            logger.info("User registered successfully: ${user.username}")

            // Create a JSON response using buildJsonObject
            val jsonResponse = buildJsonObject {
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

            call.respond(HttpStatusCode.Created, jsonResponse)
        } catch (e: IllegalArgumentException) {
            logger.error("Registration validation error: ${e.message}")
            val errorResponse = buildJsonObject {
                put("success", false)
                put("data", null)
                put("error", JsonPrimitive(e.message ?: "Registration failed"))
            }
            call.respond(HttpStatusCode.BadRequest, errorResponse)
        } catch (e: Exception) {
            logger.error("Registration error: ${e.message}", e)
            val errorResponse = buildJsonObject {
                put("success", false)
                put("data", null)
                put("error", JsonPrimitive("An error occurred: ${e.message}"))
            }
            call.respond(HttpStatusCode.InternalServerError, errorResponse)
        }
    }

    suspend fun getCsrfToken(call: ApplicationCall) {
        // The CSRF token is automatically set in cookie by the CSRF protection plugin
        val jsonResponse = buildJsonObject {
            put("success", true)
            put("data", JsonPrimitive("CSRF token set"))
            put("error", null)
        }
        call.respond(HttpStatusCode.OK, jsonResponse)
    }
    
    suspend fun getAllUsers(call: ApplicationCall) {
        try {
            // Check if user is admin
            val principal = call.principal<JWTPrincipal>()
            val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()
            
            if (!roles.contains("ADMIN")) {
                logger.info("Unauthorized attempt to access user list")
                val errorResponse = buildJsonObject {
                    put("success", false)
                    put("data", null)
                    put("error", JsonPrimitive("Admin access required"))
                }
                call.respond(HttpStatusCode.Forbidden, errorResponse)
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
                                    put("lastLogin", JsonPrimitive(null as String?))
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
            val errorResponse = buildJsonObject {
                put("success", false)
                put("data", null)
                put("error", JsonPrimitive("Error retrieving user list: ${e.message}"))
            }
            call.respond(HttpStatusCode.InternalServerError, errorResponse)
        }
    }
}