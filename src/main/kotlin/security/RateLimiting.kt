// src/main/kotlin/org/anaphygon/security/RateLimiting.kt
package org.anaphygon.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class RateLimiter(
    private val requestsPerMinute: Int = 60,
    private val blockDurationMinutes: Int = 5
) {
    private val requests = ConcurrentHashMap<String, RequestCounter>()
    private val blockedIps = ConcurrentHashMap<String, Long>()

    fun checkRateLimit(call: ApplicationCall): Boolean {
        val ipAddress = call.request.origin.remoteHost

        // Check if IP is blocked
        val blockedUntil = blockedIps[ipAddress]
        if (blockedUntil != null) {
            if (System.currentTimeMillis() < blockedUntil) {
                return false
            } else {
                // Block expired, remove from blocked list
                blockedIps.remove(ipAddress)
            }
        }

        // Check rate limit
        val counter = requests.computeIfAbsent(ipAddress) { RequestCounter() }

        if (counter.incrementAndGet() > requestsPerMinute) {
            // Block the IP for the specified duration
            blockedIps[ipAddress] = System.currentTimeMillis() + (blockDurationMinutes * 60 * 1000)
            return false
        }

        return true
    }

    // Clean up old entries (run periodically)
    fun cleanUp() {
        val now = System.currentTimeMillis()

        // Remove expired IP blocks
        blockedIps.entries.removeIf { now > it.value }

        // Reset counters older than 1 minute
        requests.entries.removeIf {
            now - it.value.timestamp > 60000
        }
    }

    private class RequestCounter {
        val timestamp = System.currentTimeMillis()
        private val count = AtomicInteger(0)

        fun incrementAndGet(): Int = count.incrementAndGet()
    }
}

class RateLimitingPlugin(private val rateLimiter: RateLimiter) {
    class Configuration {
        var requestsPerMinute: Int = 60
        var blockDurationMinutes: Int = 5
    }

    companion object Plugin : BaseApplicationPlugin<Application, Configuration, RateLimitingPlugin> {
        override val key = AttributeKey<RateLimitingPlugin>("RateLimiting")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): RateLimitingPlugin {
            val configuration = Configuration().apply(configure)
            val rateLimiter = RateLimiter(
                configuration.requestsPerMinute,
                configuration.blockDurationMinutes
            )

            val plugin = RateLimitingPlugin(rateLimiter)

            // Changed from Features to Plugins
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                if (!rateLimiter.checkRateLimit(call)) {
                    call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limit exceeded"))
                    finish()
                }
            }

            // Schedule cleanup task
            pipeline.environment.monitor.subscribe(ApplicationStarted) {
                val cleanupIntervalMs = 60000L // 1 minute

                java.util.Timer().schedule(object : java.util.TimerTask() {
                    override fun run() {
                        rateLimiter.cleanUp()
                    }
                }, cleanupIntervalMs, cleanupIntervalMs)
            }

            return plugin
        }
    }
}

fun Application.configureRateLimiting() {
    install(RateLimitingPlugin) {
        requestsPerMinute = 100 // Adjust as needed
        blockDurationMinutes = 5
    }
}