// src/main/kotlin/org/anaphygon/Monitoring.kt
package org.anaphygon

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.anaphygon.monitoring.FileMetrics
import org.anaphygon.monitoring.StorageMetricsService
import org.slf4j.event.Level
import kotlinx.coroutines.runBlocking
import java.util.Timer
import java.util.TimerTask

// Define keys with public visibility
val FILE_METRICS_KEY = AttributeKey<FileMetrics>("fileMetrics")
val STORAGE_METRICS_KEY = AttributeKey<StorageMetricsService>("storageMetricsService")

fun Application.configureMonitoring() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    // Create file metrics
    val fileMetrics = FileMetrics(appMicrometerRegistry)

    // Create storage metrics service
    val storageMetricsService = StorageMetricsService("uploads", fileMetrics)

    // Make metrics available to other modules using built-in AttributeKey
    attributes.put(FILE_METRICS_KEY, fileMetrics)
    attributes.put(STORAGE_METRICS_KEY, storageMetricsService)

    // Schedule regular updates of storage metrics
    val updateIntervalMs = 60000L // 1 minute

    Timer().schedule(object : TimerTask() {
        override fun run() {
            runBlocking {
                storageMetricsService.updateStorageMetrics()
            }
        }
    }, 0, updateIntervalMs)

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry

        // Configure metric tags and filters
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ClassLoaderMetrics(),
            ProcessorMetrics()
        )

        // Use a simpler timer configuration
        timers { call, _ ->
            // Get the route path
            val routePath = call.request.path()
            // Create timer
            io.micrometer.core.instrument.Timer.builder("http.server.requests")
                .tag("uri", routePath)
                .tag("method", call.request.httpMethod.value)
                .tag("status", call.response.status()?.value?.toString() ?: "unknown")
                .register(registry)
        }
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    routing {
        get("/metrics-micrometer") {
            call.respond(appMicrometerRegistry.scrape())
        }

        // Endpoint to get storage metrics
        get("/api/metrics/storage") {
            val usage = storageMetricsService.getStorageUsage()
            call.respond(usage)
        }
    }
}