// src/main/kotlin/org/anaphygon/module/file/FileService.kt
package org.anaphygon.module.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.anaphygon.data.model.FileMeta
import org.anaphygon.monitoring.FileMetrics
import org.anaphygon.util.Logger
import io.ktor.server.application.*
import org.anaphygon.FILE_METRICS_KEY // Import the key from Monitoring.kt
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import kotlinx.serialization.Serializable
import org.anaphygon.config.SecureConfig
import org.anaphygon.util.ValidationUtils

@Serializable
data class FileUploadStats(
    val userId: String? = null,
    val username: String? = null,
    val totalFiles: Int,
    val totalSize: Long,
    val fileTypes: Map<String, Int>,
    val lastUpload: Long? = null,
    val files: List<FileMeta>
)

class FileService {
    private val uploadsDir = "uploads"
    private val logger = Logger("FileService")
    private var fileMetrics: FileMetrics? = null

    // JDBC Properties
    private val dbUrl = "jdbc:h2:file:./build/cdn_db;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
    private val dbUser = "root"
    private val dbPassword = ""

    init {
        // Create uploads directory if it doesn't exist
        val directory = File(uploadsDir)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // Initialize database instead of resetting it
        initializeDatabase()
    }

    private val imageTransformer = ImageTransformer()

    // Initialize with application for metrics
    fun initialize(application: Application) {
        try {
            fileMetrics = application.attributes[FILE_METRICS_KEY]
            logger.info("FileService initialized with metrics: ${fileMetrics != null}")
        } catch (e: Exception) {
            logger.error("Error initializing metrics: ${e.message}", e)
        }
    }

    private fun initializeDatabase() {
        try {
            // Ensure H2 driver is loaded
            Class.forName("org.h2.Driver")

            // Get connection
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                // Check if table exists
                val tableExists = tableExists(conn, "file_meta")

                // Only create table if it doesn't exist
                if (!tableExists) {
                    createTable(conn)
                    logger.info("Created file_meta table")
                } else {
                    logger.info("file_meta table already exists")
                }
            }
        } catch (e: Exception) {
            logger.error("Error initializing database: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun tableExists(conn: Connection, tableName: String): Boolean {
        val dbMetadata = conn.metaData
        val rs = dbMetadata.getTables(null, null, tableName.uppercase(), null)
        return rs.next()
    }

    private fun createTable(conn: Connection) {
        conn.createStatement().use { stmt ->
            val sql = """
                CREATE TABLE file_meta (
                    id VARCHAR(36) PRIMARY KEY,
                    file_name VARCHAR(255) NOT NULL,
                    stored_file_name VARCHAR(255) NOT NULL,
                    content_type VARCHAR(100) NOT NULL,
                    file_size BIGINT NOT NULL,
                    upload_date BIGINT NOT NULL,
                    user_id VARCHAR(36)
                )
            """
            stmt.execute(sql)
            logger.info("Created file_meta table successfully")
        }
    }

    suspend fun getThumbnail(fileId: String, size: Int = 200): FileData? {
        return withContext(Dispatchers.IO) {
            try {
                val fileMeta = getFileFromDB(fileId)

                if (fileMeta != null && fileMeta.contentType.startsWith("image/")) {
                    val path = Paths.get(uploadsDir, fileMeta.storedFileName)

                    if (Files.exists(path)) {
                        val content = Files.readAllBytes(path)
                        val format = imageTransformer.getFormatFromContentType(fileMeta.contentType)
                        val thumbnail = imageTransformer.generateThumbnail(content, size, format)

                        if (thumbnail != null) {
                            // Create a proper name for the thumbnail
                            val thumbnailName = "thumbnail_${size}_${fileMeta.fileName}"
                            FileData(thumbnailName, thumbnail)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.error("Error generating thumbnail: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    // Add this method to FileService.kt class
    suspend fun getFilesByUserId(userId: String): List<FileMeta> {
        return withContext(Dispatchers.IO) {
            try {
                val result = mutableListOf<FileMeta>()

                DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                    val sql = "SELECT * FROM file_meta WHERE user_id = ? ORDER BY upload_date DESC"
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, userId)

                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                result.add(
                                    FileMeta(
                                        id = rs.getString("id"),
                                        fileName = rs.getString("file_name"),
                                        storedFileName = rs.getString("stored_file_name"),
                                        contentType = rs.getString("content_type"),
                                        size = rs.getLong("file_size"),
                                        uploadDate = rs.getLong("upload_date"),
                                        userId = rs.getString("user_id")
                                    )
                                )
                            }
                        }
                    }
                }

                logger.info("Found ${result.size} files for user $userId")
                result
            } catch (e: Exception) {
                logger.error("Error getting files for user $userId: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun resizeImage(fileId: String, width: Int, height: Int): FileData? {
        return withContext(Dispatchers.IO) {
            try {
                val fileMeta = getFileFromDB(fileId)

                if (fileMeta != null && fileMeta.contentType.startsWith("image/")) {
                    val path = Paths.get(uploadsDir, fileMeta.storedFileName)

                    if (Files.exists(path)) {
                        val content = Files.readAllBytes(path)
                        val format = imageTransformer.getFormatFromContentType(fileMeta.contentType)
                        val resized = imageTransformer.resizeImage(content, width, height, format)

                        if (resized != null) {
                            // Create a proper name for the resized image
                            val resizedName = "resized_${width}x${height}_${fileMeta.fileName}"
                            FileData(resizedName, resized)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.error("Error resizing image: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    // Replace the existing code that saves files with this version:
    suspend fun saveFile(originalFileName: String, content: ByteArray, userId: String? = null): String {
        val startTime = System.currentTimeMillis()
        val fileId = UUID.randomUUID().toString()

        // Sanitize file name and validate
        val fileNameValidation = ValidationUtils.validateFileName(originalFileName)
        if (!fileNameValidation.isValid) {
            logger.error("Invalid filename: ${fileNameValidation.message}")
            throw IllegalArgumentException("Invalid filename: ${fileNameValidation.message}")
        }

        val fileExtension = originalFileName.substringAfterLast('.', "")
        val secureFileName = "$fileId.$fileExtension"

        return withContext(Dispatchers.IO) {
            try {
                // Validate path to prevent traversal attacks
                val pathValidation = ValidationUtils.validateFilePath(SecureConfig.uploadsDir, secureFileName)
                if (!pathValidation.isValid) {
                    throw SecurityException(pathValidation.message)
                }

                // Save file to disk with secure path resolution
                val directory = File(SecureConfig.uploadsDir)
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val path = Paths.get(SecureConfig.uploadsDir, secureFileName).normalize()

                // Double check the normalized path is within uploads directory
                if (!path.startsWith(Paths.get(SecureConfig.uploadsDir).normalize())) {
                    throw SecurityException("Path traversal attempt detected")
                }

                Files.write(path, content)

                // Create metadata object
                val fileMeta = FileMeta(
                    id = fileId,
                    fileName = originalFileName,
                    storedFileName = secureFileName,
                    contentType = getContentTypeFromFileName(originalFileName),
                    size = content.size.toLong(),
                    uploadDate = System.currentTimeMillis(),
                    userId = userId
                )

                // Save metadata to database
                logger.info("Saving file metadata to database: $fileMeta")
                saveFileToDB(fileMeta)

                // Record metrics
                val duration = System.currentTimeMillis() - startTime
                fileMetrics?.recordUpload(
                    content.size.toLong(),
                    duration,
                    fileExtension.ifEmpty { "unknown" }
                )

                fileId
            } catch (e: Exception) {
                logger.error("Error saving file: ${e.message}")
                throw e
            }
        }
    }

    private fun saveFileToDB(fileMeta: FileMeta) {
        try {
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                val sql = """
                    INSERT INTO file_meta (id, file_name, stored_file_name, content_type, file_size, upload_date, user_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, fileMeta.id)
                    stmt.setString(2, fileMeta.fileName)
                    stmt.setString(3, fileMeta.storedFileName)
                    stmt.setString(4, fileMeta.contentType)
                    stmt.setLong(5, fileMeta.size)
                    stmt.setLong(6, fileMeta.uploadDate)
                    stmt.setString(7, fileMeta.userId)

                    val rows = stmt.executeUpdate()
                    logger.info("File metadata saved to database successfully: $rows row(s) affected")
                }
            }
        } catch (e: Exception) {
            logger.error("Error saving file metadata to database: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // Similarly update the getFile method to use secure path resolution:
    suspend fun getFile(fileId: String): FileData? {
        val startTime = System.currentTimeMillis()
        var fileType = "unknown"

        return withContext(Dispatchers.IO) {
            try {
                // Get file metadata from database
                val fileMeta = getFileFromDB(fileId)

                if (fileMeta != null) {
                    // Validate path to prevent traversal attacks
                    val pathValidation = ValidationUtils.validateFilePath(
                        SecureConfig.uploadsDir,
                        fileMeta.storedFileName
                    )

                    if (!pathValidation.isValid) {
                        logger.warn("Path validation failed: ${pathValidation.message}")
                        return@withContext null
                    }

                    // Extract file type for metrics
                    fileType = fileMeta.fileName.substringAfterLast('.', "").lowercase()

                    // Read file from disk using stored filename with secure path resolution
                    val path = Paths.get(SecureConfig.uploadsDir, fileMeta.storedFileName).normalize()

                    // Double check the normalized path is within uploads directory
                    if (!path.startsWith(Paths.get(SecureConfig.uploadsDir).normalize())) {
                        logger.warn("Path traversal attempt detected")
                        return@withContext null
                    }

                    if (Files.exists(path)) {
                        val content = Files.readAllBytes(path)

                        // Record metrics
                        val duration = System.currentTimeMillis() - startTime
                        fileMetrics?.recordDownload(fileId, fileType, duration)

                        FileData(fileMeta.fileName, content)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.error("Error getting file: ${e.message}")
                null
            }
        }
    }

    private fun getFileFromDB(fileId: String): FileMeta? {
        try {
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                val sql = "SELECT * FROM file_meta WHERE id = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, fileId)

                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return FileMeta(
                                id = rs.getString("id"),
                                fileName = rs.getString("file_name"),
                                storedFileName = rs.getString("stored_file_name"),
                                contentType = rs.getString("content_type"),
                                size = rs.getLong("file_size"),
                                uploadDate = rs.getLong("upload_date"),
                                userId = rs.getString("user_id")
                            )
                        }
                        return null
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting file from database: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    suspend fun deleteFile(fileId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get file metadata
                val fileMeta = getFileFromDB(fileId)

                if (fileMeta != null) {
                    // Delete file from disk
                    val path = Paths.get(uploadsDir, fileMeta.storedFileName)
                    var fileDeleted = false

                    if (Files.exists(path)) {
                        Files.delete(path)
                        fileDeleted = true
                    }

                    // Delete metadata from database
                    val metaDeleted = deleteFileFromDB(fileId)

                    fileDeleted && metaDeleted
                } else {
                    // Fallback to filesystem search
                    val directory = File(uploadsDir)
                    val files = directory.listFiles { file ->
                        file.name.startsWith(fileId)
                    }

                    if (files == null || files.isEmpty()) {
                        false
                    } else {
                        val file = files[0]
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                logger.error("Error deleting file: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    private fun deleteFileFromDB(fileId: String): Boolean {
        try {
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                val sql = "DELETE FROM file_meta WHERE id = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, fileId)

                    val rows = stmt.executeUpdate()
                    logger.info("File metadata deleted from database: $rows row(s) affected")
                    return rows > 0
                }
            }
        } catch (e: Exception) {
            logger.error("Error deleting file metadata from database: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    suspend fun getFileInfo(fileId: String): FileMeta? {
        return withContext(Dispatchers.IO) {
            try {
                // Try to get from database first
                val fileMetaFromDB = getFileFromDB(fileId)

                if (fileMetaFromDB != null) {
                    return@withContext fileMetaFromDB
                }

                // Fallback to filesystem
                val directory = File(uploadsDir)
                val files = directory.listFiles { file ->
                    file.name.startsWith(fileId)
                }

                if (files == null || files.isEmpty()) {
                    null
                } else {
                    val file = files[0]
                    FileMeta(
                        id = fileId,
                        fileName = file.name,
                        storedFileName = file.name,
                        contentType = getContentTypeFromFileName(file.name),
                        size = file.length(),
                        uploadDate = file.lastModified()
                    )
                }
            } catch (e: Exception) {
                logger.error("Error getting file info: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getAllFiles(): List<FileMeta> {
        return withContext(Dispatchers.IO) {
            try {
                // Get all files from database
                val filesFromDB = getAllFilesFromDB()

                if (filesFromDB.isNotEmpty()) {
                    return@withContext filesFromDB
                }

                // Fallback to filesystem
                val directory = File(uploadsDir)
                val files = directory.listFiles() ?: emptyArray()

                files.map { file ->
                    val fileId = file.name.substringBeforeLast(".", "")
                    FileMeta(
                        id = fileId,
                        fileName = file.name,
                        storedFileName = file.name,
                        contentType = getContentTypeFromFileName(file.name),
                        size = file.length(),
                        uploadDate = file.lastModified()
                    )
                }
            } catch (e: Exception) {
                logger.error("Error getting all files: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    fun getAllFilesFromDB(): List<FileMeta> {
        try {
            val result = mutableListOf<FileMeta>()

            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                val sql = "SELECT * FROM file_meta"
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        while (rs.next()) {
                            result.add(
                                FileMeta(
                                    id = rs.getString("id"),
                                    fileName = rs.getString("file_name"),
                                    storedFileName = rs.getString("stored_file_name"),
                                    contentType = rs.getString("content_type"),
                                    size = rs.getLong("file_size"), // Note: Column is file_size but property is size
                                    uploadDate = rs.getLong("upload_date"),
                                    userId = rs.getString("user_id")
                                )
                            )
                        }
                    }
                }
            }

            return result
        } catch (e: Exception) {
            logger.error("Error getting all files from database: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun getContentTypeFromFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            // Add more mappings as needed
            else -> "application/octet-stream"
        }
    }

    suspend fun getFileStatsByUser(userId: String): FileUploadStats {
        return withContext(Dispatchers.IO) {
            try {
                val files = mutableListOf<FileMeta>()
                var totalSize = 0L
                val fileTypes = mutableMapOf<String, Int>()
                var lastUpload: Long? = null

                // Don't try to query users table at all - we'll get username from the client side

                // Get files for this user
                DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                    val sql = "SELECT * FROM file_meta WHERE user_id = ? ORDER BY upload_date DESC"
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, userId)

                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                val file = FileMeta(
                                    id = rs.getString("id"),
                                    fileName = rs.getString("file_name"),
                                    storedFileName = rs.getString("stored_file_name"),
                                    contentType = rs.getString("content_type"),
                                    size = rs.getLong("file_size"),
                                    uploadDate = rs.getLong("upload_date"),
                                    userId = rs.getString("user_id")
                                )

                                files.add(file)
                                totalSize += file.size

                                // Track file types
                                val extension = file.fileName.substringAfterLast('.', "unknown")
                                fileTypes[extension] = fileTypes.getOrDefault(extension, 0) + 1

                                // Track last upload
                                if (lastUpload == null || file.uploadDate > lastUpload!!) {
                                    lastUpload = file.uploadDate
                                }
                            }
                        }
                    }
                }

                FileUploadStats(
                    userId = userId,
                    username = null, // Don't provide username from backend
                    totalFiles = files.size,
                    totalSize = totalSize,
                    fileTypes = fileTypes,
                    lastUpload = lastUpload,
                    files = files
                )
            } catch (e: Exception) {
                logger.error("Error getting file stats for user $userId: ${e.message}")
                e.printStackTrace()
                FileUploadStats(
                    userId = userId,
                    totalFiles = 0,
                    totalSize = 0,
                    fileTypes = emptyMap(),
                    files = emptyList()
                )
            }
        }
    }

    suspend fun getAllFileStats(): List<FileUploadStats> {
        return withContext(Dispatchers.IO) {
            try {
                val userFiles = mutableMapOf<String?, MutableList<FileMeta>>()

                DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                    // Get all files without joining the users table
                    val sql = """
                        SELECT * FROM file_meta
                        ORDER BY upload_date DESC
                    """

                    conn.createStatement().use { stmt ->
                        stmt.executeQuery(sql).use { rs ->
                            while (rs.next()) {
                                val userId = rs.getString("user_id")
                                val file = FileMeta(
                                    id = rs.getString("id"),
                                    fileName = rs.getString("file_name"),
                                    storedFileName = rs.getString("stored_file_name"),
                                    contentType = rs.getString("content_type"),
                                    size = rs.getLong("file_size"),
                                    uploadDate = rs.getLong("upload_date"),
                                    userId = userId
                                )

                                val files = userFiles.getOrPut(userId) { mutableListOf() }
                                files.add(file)
                            }
                        }
                    }
                }

                // Create stats for each user
                val userStats = mutableListOf<FileUploadStats>()

                for ((userId, files) in userFiles) {
                    // Don't try to get username - let the frontend handle it

                    var totalSize = 0L
                    val fileTypes = mutableMapOf<String, Int>()
                    var lastUpload: Long? = null

                    for (file in files) {
                        totalSize += file.size

                        // Track file types
                        val extension = file.fileName.substringAfterLast('.', "unknown")
                        fileTypes[extension] = fileTypes.getOrDefault(extension, 0) + 1

                        // Track last upload
                        if (lastUpload == null || file.uploadDate > lastUpload!!) {
                            lastUpload = file.uploadDate
                        }
                    }

                    userStats.add(
                        FileUploadStats(
                            userId = userId,
                            username = null, // Don't provide username from backend
                            totalFiles = files.size,
                            totalSize = totalSize,
                            fileTypes = fileTypes,
                            lastUpload = lastUpload,
                            files = files
                        )
                    )
                }

                userStats
            } catch (e: Exception) {
                logger.error("Error getting file stats for all users: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
}

data class FileData(val fileName: String, val content: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileData

        if (fileName != other.fileName) return false
        if (!content.contentEquals(other.content)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}