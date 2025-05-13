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
import org.anaphygon.data.db.DatabaseFactory
import org.anaphygon.data.db.FileMetaTable
import org.anaphygon.util.ValidationUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

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
    private val dbUrl = "jdbc:h2:file:./data/cdn_db;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
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
                    user_id VARCHAR(36),
                    is_public BOOLEAN DEFAULT FALSE,
                    moderation_status VARCHAR(20) DEFAULT 'PENDING'
                )
            """
            stmt.execute(sql)
            logger.info("Created file_meta table successfully")
        }
    }

    suspend fun getPublicFiles(page: Int = 1, pageSize: Int = 20): List<FileMeta> {
        return withContext(Dispatchers.IO) {
            try {
                val result = mutableListOf<FileMeta>()
                val offset = (page - 1) * pageSize
                
                DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                    val sql = """
                        SELECT * FROM file_meta 
                        WHERE is_public = TRUE AND moderation_status = 'APPROVED' 
                        ORDER BY upload_date DESC 
                        LIMIT ? OFFSET ?
                    """
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setInt(1, pageSize)
                        stmt.setInt(2, offset)
                        
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
                                        userId = rs.getString("user_id"),
                                        isPublic = rs.getBoolean("is_public"),
                                        moderationStatus = rs.getString("moderation_status")
                                    )
                                )
                            }
                        }
                    }
                }
                
                logger.info("Found ${result.size} public files")
                result
            } catch (e: Exception) {
                logger.error("Error getting public files: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // Update the visibility of a file
    suspend fun updateFileVisibility(fileId: String, isPublic: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                logger.info("Updating visibility for file $fileId to isPublic=$isPublic")
                
                // First check if the file exists
                val fileExists = checkFileExists(fileId)
                if (!fileExists) {
                    logger.error("Cannot update visibility for file $fileId - file not found")
                    return@withContext false
                }
                
                // Ensure is_public column exists
                ensureIsPublicColumnExists()
                
                // Use direct SQL for more reliable execution
                DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                    val sql = "UPDATE file_meta SET is_public = ?, moderation_status = 'PENDING' WHERE id = ?"
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setBoolean(1, isPublic)
                        stmt.setString(2, fileId)

                        val rows = stmt.executeUpdate()
                        logger.info("File visibility updated: $rows row(s) affected")
                        return@withContext rows > 0
                    }
                }
            } catch (e: Exception) {
                logger.error("Error updating file visibility: ${e.message}", e)
                return@withContext false
            }
        }
    }

    private fun ensureIsPublicColumnExists() {
        try {
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                // Check if is_public column exists
                val metaData = conn.metaData
                val rs = metaData.getColumns(null, null, "FILE_META", "IS_PUBLIC")
                
                if (!rs.next()) {
                    // Column doesn't exist, create it
                    logger.info("Adding missing is_public column to file_meta table")
                    val sql = "ALTER TABLE file_meta ADD COLUMN is_public BOOLEAN DEFAULT FALSE"
                    conn.createStatement().use { stmt ->
                        stmt.executeUpdate(sql)
                    }
                } else {
                    logger.info("is_public column already exists")
                }
            }
        } catch (e: Exception) {
            logger.error("Error ensuring is_public column exists: ${e.message}")
        }
    }

    suspend fun moderateFile(fileId: String, status: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                logger.info("Moderating file $fileId with status $status")
                
                // First check if the file exists
                val fileExists = checkFileExists(fileId)
                if (!fileExists) {
                    logger.error("Cannot moderate file $fileId - file not found")
                    return@withContext false
                }
                
                // Check if moderation_status column exists
                ensureModerationColumnExists()
                
                // Use direct SQL for more reliable execution
                DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                    val sql = "UPDATE file_meta SET moderation_status = ? WHERE id = ?"
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, status)
                        stmt.setString(2, fileId)

                        val rows = stmt.executeUpdate()
                        logger.info("File moderation status updated: $rows row(s) affected")
                        return@withContext rows > 0
                    }
                }
            } catch (e: Exception) {
                logger.error("Error updating moderation status: ${e.message}", e)
                return@withContext false
            }
        }
    }

    private fun checkFileExists(fileId: String): Boolean {
        try {
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                val sql = "SELECT COUNT(*) FROM file_meta WHERE id = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, fileId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return rs.getInt(1) > 0
                        }
                    }
                }
            }
            return false
        } catch (e: Exception) {
            logger.error("Error checking if file exists: ${e.message}")
            return false
        }
    }

    private fun ensureModerationColumnExists() {
        try {
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                // Check if moderation_status column exists
                val metaData = conn.metaData
                val rs = metaData.getColumns(null, null, "FILE_META", "MODERATION_STATUS")
                
                if (!rs.next()) {
                    // Column doesn't exist, create it
                    logger.info("Adding missing moderation_status column to file_meta table")
                    val sql = "ALTER TABLE file_meta ADD COLUMN moderation_status VARCHAR(20) DEFAULT 'PENDING'"
                    conn.createStatement().use { stmt ->
                        stmt.executeUpdate(sql)
                    }
                } else {
                    logger.info("moderation_status column already exists")
                }
            }
        } catch (e: Exception) {
            logger.error("Error ensuring moderation column exists: ${e.message}")
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
                                        userId = rs.getString("user_id"),
                                        isPublic = try { rs.getBoolean("is_public") } catch (e: Exception) { false },
                                        moderationStatus = try { rs.getString("moderation_status") } catch (e: Exception) { "PENDING" }
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
                    INSERT INTO file_meta (id, file_name, stored_file_name, content_type, file_size, upload_date, user_id, is_public, moderation_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, fileMeta.id)
                    stmt.setString(2, fileMeta.fileName)
                    stmt.setString(3, fileMeta.storedFileName)
                    stmt.setString(4, fileMeta.contentType)
                    stmt.setLong(5, fileMeta.size)
                    stmt.setLong(6, fileMeta.uploadDate)
                    stmt.setString(7, fileMeta.userId)
                    stmt.setBoolean(8, fileMeta.isPublic ?: false)
                    stmt.setString(9, fileMeta.moderationStatus ?: "PENDING")

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
                                userId = rs.getString("user_id"),
                                isPublic = try { rs.getBoolean("is_public") } catch (e: Exception) { false },
                                moderationStatus = try { rs.getString("moderation_status") } catch (e: Exception) { "PENDING" }
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
                                    userId = rs.getString("user_id"),
                                    isPublic = try { rs.getBoolean("is_public") } catch (e: Exception) { false },
                                    moderationStatus = try { rs.getString("moderation_status") } catch (e: Exception) { "PENDING" }
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

    fun getDatabaseSchemaInfo(): Map<String, Any> {
        val schemaInfo = mutableMapOf<String, Any>()
        
        try {
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                val metaData = conn.metaData
                
                // Get table information
                val tables = mutableListOf<Map<String, String>>()
                val tableRs = metaData.getTables(null, null, null, arrayOf("TABLE"))
                while (tableRs.next()) {
                    val tableName = tableRs.getString("TABLE_NAME")
                    tables.add(mapOf("name" to tableName))
                }
                schemaInfo["tables"] = tables
                
                // Get file_meta columns
                val columns = mutableListOf<Map<String, String>>()
                val columnRs = metaData.getColumns(null, null, "FILE_META", null)
                while (columnRs.next()) {
                    val columnName = columnRs.getString("COLUMN_NAME")
                    val dataType = columnRs.getString("TYPE_NAME")
                    val nullable = columnRs.getString("IS_NULLABLE")
                    columns.add(mapOf(
                        "name" to columnName,
                        "type" to dataType,
                        "nullable" to nullable
                    ))
                }
                schemaInfo["file_meta_columns"] = columns
                
                // Check if specific columns exist
                schemaInfo["has_is_public"] = columns.any { it["name"]?.equals("IS_PUBLIC", ignoreCase = true) == true }
                schemaInfo["has_moderation_status"] = columns.any { it["name"]?.equals("MODERATION_STATUS", ignoreCase = true) == true }
                
                // Try to query a file to see column values
                try {
                    val sampleFile = getAllFilesFromDB().firstOrNull()
                    if (sampleFile != null) {
                        schemaInfo["sample_file"] = mapOf(
                            "id" to sampleFile.id,
                            "isPublic" to (sampleFile.isPublic ?: false),
                            "moderationStatus" to (sampleFile.moderationStatus ?: "PENDING")
                        )
                    }
                } catch (e: Exception) {
                    schemaInfo["sample_file_error"] = e.message ?: "Unknown error"
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting database schema info: ${e.message}")
            schemaInfo["error"] = e.message ?: "Unknown error"
        }
        
        return schemaInfo
    }

    fun executeDirectSqlModeration(fileId: String, status: String): String {
        try {
            logger.info("Executing direct SQL moderation for file $fileId with status $status")
            
            // First check if the file exists
            val fileExists = checkFileExists(fileId)
            if (!fileExists) {
                return "File not found: $fileId"
            }
            
            // Try to execute the SQL directly
            val result = StringBuilder()
            
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
                // First check the table structure
                val metaData = conn.metaData
                val columns = mutableListOf<String>()
                val rs = metaData.getColumns(null, null, "FILE_META", null)
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"))
                }
                result.append("Columns found: $columns\n")
                
                // Check if moderation_status column exists
                if (!columns.any { it.equals("MODERATION_STATUS", ignoreCase = true) }) {
                    // Try to add the column
                    try {
                        conn.createStatement().use { stmt ->
                            stmt.executeUpdate("ALTER TABLE file_meta ADD COLUMN moderation_status VARCHAR(20) DEFAULT 'PENDING'")
                            result.append("Added missing moderation_status column\n")
                        }
                    } catch (e: Exception) {
                        result.append("Failed to add moderation_status column: ${e.message}\n")
                    }
                }
                
                // Execute the update
                conn.prepareStatement("UPDATE file_meta SET moderation_status = ? WHERE id = ?").use { stmt ->
                    stmt.setString(1, status)
                    stmt.setString(2, fileId)
                    
                    val rowsAffected = stmt.executeUpdate()
                    result.append("Update executed. Rows affected: $rowsAffected\n")
                    
                    if (rowsAffected > 0) {
                        // Verify the update
                        conn.prepareStatement("SELECT moderation_status FROM file_meta WHERE id = ?").use { verifyStmt ->
                            verifyStmt.setString(1, fileId)
                            verifyStmt.executeQuery().use { verifyRs ->
                                if (verifyRs.next()) {
                                    val currentStatus = verifyRs.getString("moderation_status")
                                    result.append("Current status after update: $currentStatus\n")
                                } else {
                                    result.append("Could not verify update - file not found after update\n")
                                }
                            }
                        }
                    }
                }
            }
            
            return result.toString()
        } catch (e: Exception) {
            logger.error("Error in direct SQL moderation: ${e.message}", e)
            return "Error: ${e.message}\n${e.stackTraceToString()}"
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