// src/main/kotlin/org/anaphygon/auth/JwtConfig.kt
package org.anaphygon.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import java.util.*

object JwtConfig {
    private const val JWT_SECRET = "your-secret-key" // In production, use environment variables
    private const val ISSUER = "anaphygon-cdn"
    private const val VALIDITY_IN_MS = 36_000_00 * 24 // 24 hours

    private val algorithm = Algorithm.HMAC256(JWT_SECRET)

    // Add this method to return the verifier
    fun getVerifier(): JWTVerifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .build()

    fun createToken(userId: String, role: String): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(ISSUER)
        .withClaim("id", userId)
        .withClaim("role", role)
        .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_IN_MS))
        .sign(algorithm)

    fun validateToken(token: String): DecodedJWT = getVerifier().verify(token)
}