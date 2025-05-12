// src/main/kotlin/org/anaphygon/auth/AuthModule.kt
package org.anaphygon.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureAuth() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "CDN Server"
            verifier {
                try {
                    JwtConfig.getVerifier()  // Change to use a method that returns a JWTVerifier
                } catch (e: Exception) {
                    null
                }
            }
            validate { credential ->
                try {
                    val payload = credential.payload
                    val userId = payload.getClaim("id").asString()
                    val role = payload.getClaim("role").asString()

                    if (userId != null && role != null) {
                        JWTPrincipal(payload)
                    } else {
                        null
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