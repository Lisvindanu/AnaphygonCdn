// src/main/kotlin/org/anaphygon/security/CsrfProtection.kt
package org.anaphygon.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.util.UUID

// Define a custom event for CSRF token generation
private val CSRF_TOKEN_EVENT = AttributeKey<Unit>("CSRFTokenEvent")

class CsrfProtection {
    class Configuration {
        var cookieName: String = "XSRF-TOKEN"
        var headerName: String = "X-XSRF-TOKEN"

        // List of paths that don't require CSRF protection
        var excludedPaths: List<String> = listOf(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/csrf"
        )
    }

    companion object Plugin : BaseApplicationPlugin<Application, Configuration, CsrfProtection> {
        override val key = AttributeKey<CsrfProtection>("CsrfProtection")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): CsrfProtection {
            val configuration = Configuration().apply(configure)
            val plugin = CsrfProtection()

            // CSRF validation interceptor
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                // Skip for OPTIONS requests (CORS preflight)
                if (call.request.httpMethod == HttpMethod.Options) {
                    return@intercept
                }

                // Skip for excluded paths
                if (configuration.excludedPaths.any { call.request.path().startsWith(it) }) {
                    return@intercept
                }

                // Skip for non-mutation methods
                if (call.request.httpMethod !in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete, HttpMethod.Patch)) {
                    return@intercept
                }

                // Get token from cookie
                val cookieToken = call.request.cookies[configuration.cookieName]

                // Get token from header
                val headerToken = call.request.header(configuration.headerName)

                // Check if tokens match
                if (cookieToken == null || headerToken == null || cookieToken != headerToken) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "CSRF token validation failed"))
                    finish()
                }
            }

            // CSRF token generation interceptor
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                // Skip for static files
                if (call.request.path().startsWith("/static")) {
                    return@intercept
                }

                // Check if token already exists in cookie
                if (call.request.cookies[configuration.cookieName] == null) {
                    // Generate a new token
                    val token = UUID.randomUUID().toString()

                    // Set cookie with SameSite=None to allow cross-site requests
                    call.response.cookies.append(
                        Cookie(
                            name = configuration.cookieName,
                            value = token,
                            secure = false, // Set to false for local development
                            httpOnly = false, // Accessible by JavaScript
                            path = "/",
                            extensions = mapOf("SameSite" to "None") // Allow cross-site requests
                        )
                    )
                }
            }

            return plugin
        }
    }
}

fun Application.configureCsrfProtection() {
    install(CsrfProtection) {
        cookieName = "XSRF-TOKEN"
        headerName = "X-XSRF-TOKEN"
        excludedPaths = listOf(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/csrf",
            "/api/files" // Allow file operations without CSRF
        )
    }
}