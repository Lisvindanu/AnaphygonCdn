// src/main/kotlin/org/anaphygon/jobs/CleanupJob.kt
package org.anaphygon.jobs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.anaphygon.util.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean

class CleanupJob(
    private val uploadsDir: String,
    private val dbUrl: String,
    private val dbUser: String,
    private val dbPassword: String,
    private val maxAgeInDays: Int = 30,
    private val orphanedFileMaxAgeInDays: Int = 1
) {
    private val logger = Logger("CleanupJob")
    private val isRunning = AtomicBoolean(false)

    // Start scheduled job
    fun start() {
        val timer = Timer(true)
        val periodMs = 24 * 60 * 60 * 1000L // Run once a day

        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (!isRunning.getAndSet(true)) {
                    try {
                        CoroutineScope(Dispatchers.IO).launch {
                            runCleanup()
                        }
                    } finally {
                        isRunning.set(false)
                    }
                }
            }
        }, 0, periodMs)

        logger.info("Cleanup job scheduled to run every 24 hours")
    }

    // Manually trigger cleanup
    suspend fun runCleanup() {
        if (!isRunning.getAndSet(true)) {
            try {
                logger.info("Starting cleanup job")

                val cleanupResults = cleanupOldFiles() + cleanupOrphanedFiles()

                logger.info("Cleanup completed: ${cleanupResults.cleanedFiles} files cleaned up, ${cleanupResults.freedSpace} bytes freed")
            } catch (e: Exception) {
                logger.error("Error in cleanup job: ${e.message}", e)
            } finally {
                isRunning.set(false)
            }
        } else {
            logger.info("Cleanup job already running")
        }
    }

    private fun cleanupOldFiles(): CleanupResult {
        var cleanedFiles = 0
        var freedSpace = 0L

        try {
            // Calculate cutoff date
            val cutoffTime = Instant.now().minus(maxAgeInDays.toLong(), ChronoUnit.DAYS).toEpochMilli()

            // Get old files from database
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                val sql = "SELECT id, stored_file_name FROM file_meta WHERE upload_date < ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, cutoffTime)

                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val id = rs.getString("id")
                            val storedFileName = rs.getString("stored_file_name")

                            // Delete the file
                            val path = Paths.get(uploadsDir, storedFileName)
                            if (Files.exists(path)) {
                                val fileSize = Files.size(path)
                                Files.delete(path)
                                freedSpace += fileSize
                                cleanedFiles++
                                logger.info("Deleted old file: $storedFileName (ID: $id)")

                                // Delete metadata from database
                                deleteFileFromDB(conn, id)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error cleaning up old files: ${e.message}", e)
        }

        return CleanupResult(cleanedFiles, freedSpace)
    }

    private fun cleanupOrphanedFiles(): CleanupResult {
        var cleanedFiles = 0
        var freedSpace = 0L

        try {
            // Get list of all files in storage
            val directory = File(uploadsDir)
            if (!directory.exists()) {
                return CleanupResult(0, 0)
            }

            // Get list of all files in database
            val dbFiles = mutableSetOf<String>()
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                val sql = "SELECT stored_file_name FROM file_meta"
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        while (rs.next()) {
                            dbFiles.add(rs.getString("stored_file_name"))
                        }
                    }
                }
            }

            // Check each file in storage
            directory.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val fileName = file.name

                    // If file not in database and older than orphanedFileMaxAgeInDays
                    if (fileName !in dbFiles) {
                        val path = file.toPath()
                        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
                        val creationTime = attributes.creationTime().toInstant()
                        val cutoffTime = Instant.now().minus(orphanedFileMaxAgeInDays.toLong(), ChronoUnit.DAYS)

                        if (creationTime.isBefore(cutoffTime)) {
                            // Delete orphaned file
                            val fileSize = file.length()
                            if (file.delete()) {
                                freedSpace += fileSize
                                cleanedFiles++
                                logger.info("Deleted orphaned file: $fileName")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error cleaning up orphaned files: ${e.message}", e)
        }

        return CleanupResult(cleanedFiles, freedSpace)
    }

    private fun deleteFileFromDB(conn: Connection, fileId: String): Boolean {
        return try {
            val sql = "DELETE FROM file_meta WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, fileId)
                val rows = stmt.executeUpdate()
                rows > 0
            }
        } catch (e: Exception) {
            logger.error("Error deleting file metadata from database: ${e.message}", e)
            false
        }
    }

    data class CleanupResult(
        val cleanedFiles: Int,
        val freedSpace: Long
    ) {
        operator fun plus(other: CleanupResult): CleanupResult {
            return CleanupResult(
                cleanedFiles + other.cleanedFiles,
                freedSpace + other.freedSpace
            )
        }
    }
}