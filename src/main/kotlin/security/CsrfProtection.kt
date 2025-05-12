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
            "/api/auth/csrf",
            "/api/files",  // Allow file operations without CSRF
            "/cdn"  // Allow CDN access without CSRF
        )
    }

    companion object Plugin : BaseApplicationPlugin<Application, Configuration, CsrfProtection> {
        override val key = AttributeKey<CsrfProtection>("CsrfProtection")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): CsrfProtection {
            val configuration = Configuration().apply(configure)
            val plugin = CsrfProtection()

            pipeline.intercept(ApplicationCallPipeline.Setup) {
                // Generate token early in the pipeline
                if (!call.response.isCommitted && 
                    !call.request.path().startsWith("/static") &&
                    !configuration.excludedPaths.any { call.request.path().startsWith(it) }) {
                    
                    val existingToken = call.request.cookies[configuration.cookieName]
                    if (existingToken == null) {
                        val token = UUID.randomUUID().toString()
                        call.response.cookies.append(
                            Cookie(
                                name = configuration.cookieName,
                                value = token,
                                secure = false, // Set to true in production
                                httpOnly = false,
                                path = "/",
                                extensions = mapOf("SameSite" to "Lax")
                            )
                        )
                    }
                }
            }

            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                // Skip CSRF check for excluded paths and OPTIONS requests
                if (call.request.httpMethod == HttpMethod.Options ||
                    configuration.excludedPaths.any { call.request.path().startsWith(it) }) {
                    return@intercept
                }

                // Skip for non-mutation methods
                if (call.request.httpMethod !in listOf(
                    HttpMethod.Post,
                    HttpMethod.Put,
                    HttpMethod.Delete,
                    HttpMethod.Patch
                )) {
                    return@intercept
                }

                val cookieToken = call.request.cookies[configuration.cookieName]
                val headerToken = call.request.header(configuration.headerName)

                if (cookieToken == null || headerToken == null || cookieToken != headerToken) {
                    call.respond(HttpStatusCode.Forbidden, mapOf(
                        "error" to "CSRF token validation failed",
                        "message" to "Invalid or missing CSRF token"
                    ))
                    finish()
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
            "/api/files",
            "/cdn"
        )
    }
}