// src/main/kotlin/org/anaphygon/auth/service/JwtService.kt
package org.anaphygon.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import org.anaphygon.auth.model.User
import org.anaphygon.config.SecureConfig
import org.anaphygon.util.Logger
import java.util.*

class JwtService {
    private val algorithm = Algorithm.HMAC256(SecureConfig.jwtSecret)
    private val logger = Logger("JwtService")

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(SecureConfig.jwtIssuer)
        .withAudience(SecureConfig.jwtAudience)
        .build()

    fun generateToken(user: User): String {
        val now = Date()
        val validity = Date(now.time + SecureConfig.jwtExpirationMs)

        logger.info("Generating token for user ${user.username} with roles: ${user.roles}")

        return JWT.create()
            .withSubject("Authentication")
            .withIssuer(SecureConfig.jwtIssuer)
            .withAudience(SecureConfig.jwtAudience)
            .withClaim("id", user.id)
            .withClaim("username", user.username)
            .withArrayClaim("roles", user.roles.toTypedArray())
            .withIssuedAt(now)
            .withExpiresAt(validity)
            .sign(algorithm)
    }
}