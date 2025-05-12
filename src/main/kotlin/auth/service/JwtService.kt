// src/main/kotlin/org/anaphygon/auth/service/JwtService.kt
package org.anaphygon.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import org.anaphygon.auth.model.User
import java.util.*

class JwtService(
    private val secret: String = System.getenv("JWT_SECRET") ?: "default-jwt-secret-change-me-in-production",
    private val issuer: String = "anaphygon-cdn",
    private val audience: String = "anaphygon-cdn-users",
    private val validityInMs: Long = 36_000_00 * 24 // 24 hours
) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateToken(user: User): String {
        val now = Date()
        val validity = Date(now.time + validityInMs)

        return JWT.create()
            .withSubject("Authentication")
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("id", user.id)
            .withClaim("username", user.username)
            .withArrayClaim("roles", user.roles.toTypedArray())
            .withIssuedAt(now)
            .withExpiresAt(validity)
            .sign(algorithm)
    }
}
