// src/main/kotlin/org/anaphygon/monitoring/FileMetrics.kt
package org.anaphygon.monitoring

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.DiskSpaceMetrics
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.anaphygon.util.Logger

class FileMetrics(private val registry: MeterRegistry) {
    private val logger = Logger("FileMetrics")

    // Upload metrics
    private val uploadCounter = Counter
        .builder("cdn.file.uploads")
        .description("Number of files uploaded")
        .register(registry)

    // Use DistributionSummary.builder instead of registry.summary
    private val uploadSizeDistribution = DistributionSummary
        .builder("cdn.file.upload.size")
        .description("Distribution of uploaded file sizes in bytes")
        .register(registry)

    private val uploadTimer = Timer
        .builder("cdn.file.upload.time")
        .description("Time taken to upload files")
        .register(registry)

    // Download metrics
    private val downloadCounter = Counter
        .builder("cdn.file.downloads")
        .description("Number of files downloaded")
        .register(registry)

    private val downloadTimers = ConcurrentHashMap<String, Timer>()

    // File type metrics
    private val fileTypeCounters = ConcurrentHashMap<String, Counter>()

    // Gauge metrics using AtomicLong/AtomicInteger
    private val sizeGauge = AtomicLong(0)
    private val countGauge = AtomicInteger(0)

    // Initialize disk space metrics
    init {
        try {
            // Add disk space metrics for the uploads directory
            val uploadsDir = File("uploads")
            if (!uploadsDir.exists()) {
                uploadsDir.mkdirs()
            }

            // Register gauges properly - using lambda to convert to Double
            registry.gauge("cdn.storage.size", emptyList(), sizeGauge) { it.get().toDouble() }
            registry.gauge("cdn.storage.count", emptyList(), countGauge) { it.get().toDouble() }

            // DiskSpaceMetrics is deprecated but still usable
            try {
                DiskSpaceMetrics(uploadsDir).bindTo(registry)
            } catch (e: Exception) {
                logger.warn("Could not bind DiskSpaceMetrics: ${e.message}")
            }

            logger.info("Metrics initialized for uploads directory")
        } catch (e: Exception) {
            logger.error("Error initializing metrics: ${e.message}", e)
        }
    }

    fun recordUpload(fileSize: Long, durationMs: Long, fileType: String) {
        try {
            uploadCounter.increment()
            uploadSizeDistribution.record(fileSize.toDouble())
            uploadTimer.record(durationMs, TimeUnit.MILLISECONDS)

            // Record file type metrics
            getOrCreateFileTypeCounter(fileType).increment()

            logger.debug("Recorded upload: size=$fileSize, duration=${durationMs}ms, type=$fileType")
        } catch (e: Exception) {
            logger.error("Error recording upload metrics: ${e.message}", e)
        }
    }

    fun recordDownload(fileId: String, fileType: String, durationMs: Long) {
        try {
            downloadCounter.increment()

            // Record download time by file type
            getOrCreateDownloadTimer(fileType).record(durationMs, TimeUnit.MILLISECONDS)

            logger.debug("Recorded download: fileId=$fileId, type=$fileType, duration=${durationMs}ms")
        } catch (e: Exception) {
            logger.error("Error recording download metrics: ${e.message}", e)
        }
    }

    fun updateStorageMetrics(totalSize: Long, fileCount: Int) {
        try {
            // Update gauge values
            sizeGauge.set(totalSize)
            countGauge.set(fileCount)
            logger.debug("Updated storage metrics: totalSize=$totalSize, fileCount=$fileCount")
        } catch (e: Exception) {
            logger.error("Error updating storage metrics: ${e.message}", e)
        }
    }

    private fun getOrCreateFileTypeCounter(fileType: String): Counter {
        return fileTypeCounters.computeIfAbsent(fileType) {
            Counter.builder("cdn.file.type")
                .tag("type", fileType)
                .description("Number of files by type")
                .register(registry)
        }
    }

    private fun getOrCreateDownloadTimer(fileType: String): Timer {
        return downloadTimers.computeIfAbsent(fileType) {
            Timer.builder("cdn.file.download.time")
                .tag("type", fileType)
                .description("Time taken to download files by type")
                .register(registry)
        }
    }
}