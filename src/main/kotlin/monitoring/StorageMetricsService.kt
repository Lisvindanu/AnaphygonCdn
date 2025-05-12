// src/main/kotlin/org/anaphygon/monitoring/StorageMetricsService.kt
package org.anaphygon.monitoring

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.anaphygon.util.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.Serializable

class StorageMetricsService(
    private val uploadsDir: String,
    private val fileMetrics: FileMetrics
) {
    private val logger = Logger("StorageMetricsService")

    suspend fun updateStorageMetrics() {
        withContext(Dispatchers.IO) {
            try {
                val directory = File(uploadsDir)
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                var totalSize = 0L
                var fileCount = 0

                directory.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        totalSize += file.length()
                        fileCount++
                    }
                }

                fileMetrics.updateStorageMetrics(totalSize, fileCount)
                logger.info("Updated storage metrics: size=$totalSize bytes, count=$fileCount files")
            } catch (e: Exception) {
                logger.error("Error updating storage metrics: ${e.message}", e)
            }
        }
    }

    suspend fun getStorageUsage(): StorageUsage {
        return withContext(Dispatchers.IO) {
            try {
                val directory = File(uploadsDir)
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                var totalSize = 0L
                var fileCount = 0
                val fileTypes = mutableMapOf<String, Int>()
                val fileTypeSizes = mutableMapOf<String, Long>()

                directory.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val size = file.length()
                        totalSize += size
                        fileCount++

                        // Count by file type
                        val extension = file.extension.lowercase()
                        fileTypes[extension] = (fileTypes[extension] ?: 0) + 1
                        fileTypeSizes[extension] = (fileTypeSizes[extension] ?: 0) + size
                    }
                }

                // Get disk space metrics
                val path = Paths.get(uploadsDir)
                val store = Files.getFileStore(path)
                val totalSpace = store.totalSpace
                val usableSpace = store.usableSpace
                val usedSpace = totalSpace - usableSpace

                StorageUsage(
                    totalFiles = fileCount,
                    totalSize = totalSize,
                    filesByType = fileTypes,
                    sizeByType = fileTypeSizes,
                    diskTotalSpace = totalSpace,
                    diskUsableSpace = usableSpace,
                    diskUsedSpace = usedSpace
                )
            } catch (e: Exception) {
                logger.error("Error getting storage usage: ${e.message}", e)
                StorageUsage(0, 0, emptyMap(), emptyMap(), 0, 0, 0)
            }
        }
    }
}

@Serializable
data class StorageUsage(
    val totalFiles: Int,
    val totalSize: Long,
    val filesByType: Map<String, Int>,
    val sizeByType: Map<String, Long>,
    val diskTotalSpace: Long,
    val diskUsableSpace: Long,
    val diskUsedSpace: Long
)