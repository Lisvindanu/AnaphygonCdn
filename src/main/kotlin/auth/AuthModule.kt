// src/main/kotlin/org/anaphygon/auth/AuthModule.kt
package org.anaphygon.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.anaphygon.auth.service.JwtService

fun Application.configureAuth(jwtService: JwtService) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "CDN Server"
            verifier(jwtService.verifier)
            validate { credential ->
                try {
                    val payload = credential.payload
                    val userId = payload.getClaim("id").asString()

                    // Check for roles as an array (new format)
                    val rolesArray = payload.getClaim("roles").asList(String::class.java)
                    if (!rolesArray.isNullOrEmpty()) {
                        JWTPrincipal(payload)
                    }
                    // Backward compatibility for the old format with 'role' claim
                    else {
                        val role = payload.getClaim("role").asString()
                        if (userId != null && role != null) {
                            JWTPrincipal(payload)
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }
}