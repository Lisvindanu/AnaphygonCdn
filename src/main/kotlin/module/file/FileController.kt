package org.anaphygon.module.file

import org.anaphygon.data.model.FileMeta
import org.anaphygon.util.ResponseWrapper
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add

class FileController(private val fileService: FileService) {
    companion object {
        val USER_ID_KEY = AttributeKey<String>("userId")
    }

    constructor(fileService: FileService, application: Application) : this(fileService) {
        fileService.initialize(application)
    }
    
    suspend fun uploadFile(call: ApplicationCall) {
        try {
            // Get user ID from call attributes
            val userId = call.attributes.getOrNull(USER_ID_KEY)
        
            // Receive multipart data
            val multipart = call.receiveMultipart()

            var fileName = ""
            var fileContent: ByteArray? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName ?: UUID.randomUUID().toString()
                        // Gunakan cara aman untuk membaca file
                        fileContent = withContext(Dispatchers.IO) {
                            part.streamProvider().readBytes() // Tetap gunakan streamProvider untuk Ktor 3.x
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (fileContent != null) {
                val fileId = fileService.saveFile(fileName, fileContent!!, userId)
                
                // Create proper JSON response
                val jsonResponse = buildJsonObject {
                    put("success", true)
                    put("data", fileId)
                    put("error", null)
                }
                
                call.respondText(jsonResponse.toString(), ContentType.Application.Json, HttpStatusCode.Created)
            } else {
                val errorResponse = buildJsonObject {
                    put("success", false)
                    put("data", null)
                    put("error", "No file content received")
                }
                call.respondText(errorResponse.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
        } catch (e: Exception) {
            e.printStackTrace() // Tambahkan ini untuk debug
            val errorResponse = buildJsonObject {
                put("success", false)
                put("data", null)
                put("error", e.message ?: "Unknown error occurred")
            }
            call.respondText(errorResponse.toString(), ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }

    suspend fun getThumbnail(call: ApplicationCall) {
        val fileId = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required"))
        val size = call.parameters["size"]?.toIntOrNull() ?: 200

        try {
            val thumbnail = fileService.getThumbnail(fileId, size)
            if (thumbnail != null) {
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, thumbnail.fileName).toString()
                )
                call.respondBytes(thumbnail.content, ContentType.Image.JPEG)
            } else {
                call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("Could not generate thumbnail"))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error(e.message ?: "Unknown error occurred"))
        }
    }

    suspend fun resizeImage(call: ApplicationCall) {
        val fileId = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required"))
        val width = call.parameters["width"]?.toIntOrNull() ?: return call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("Width required"))
        val height = call.parameters["height"]?.toIntOrNull() ?: return call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("Height required"))

        try {
            val resized = fileService.resizeImage(fileId, width, height)
            if (resized != null) {
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, resized.fileName).toString()
                )
                call.respondBytes(resized.content, ContentType.Image.JPEG)
            } else {
                call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("Could not resize image"))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error(e.message ?: "Unknown error occurred"))
        }
    }

    suspend fun getFile(call: ApplicationCall) {
        val fileId = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required"))

        try {
            val file = fileService.getFile(fileId)
            if (file != null) {
                // Set content disposition header
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, file.fileName).toString()
                )

                // Set caching headers based on content type
                val contentType = getContentTypeFromFileName(file.fileName)

                // Cache for 7 days for images, 1 day for documents, 1 hour for other content
                val cacheTimeSeconds = when {
                    contentType.match(ContentType.Image.Any) -> 60 * 60 * 24 * 7 // 7 days
                    contentType.contentType == "application" -> 60 * 60 * 24 // 1 day
                    else -> 60 * 60 // 1 hour
                }

                call.response.header(HttpHeaders.CacheControl, "max-age=$cacheTimeSeconds, public")

                // Set Last-Modified header
                val fileInfo = fileService.getFileInfo(fileId)
                if (fileInfo != null) {
                    call.response.header(
                        HttpHeaders.LastModified,
                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
                            java.time.Instant.ofEpochMilli(fileInfo.uploadDate).atZone(java.time.ZoneOffset.UTC)
                        )
                    )
                }

                // Set ETag header
                val etag = getEtag(fileId)
                if (etag != null) {
                    call.response.header(HttpHeaders.ETag, etag)
                }

                call.respondBytes(file.content, contentType)
            } else {
                call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("File not found"))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error(e.message ?: "Unknown error occurred"))
        }
    }

    private suspend fun getEtag(fileId: String): String? {
        val file = fileService.getFile(fileId) ?: return null
        return java.security.MessageDigest.getInstance("MD5").digest(file.content).toHex()
    }

    // Helper function to convert byte array to hex string
    private fun ByteArray.toHex(): String {
        return this.joinToString("") { "%02x".format(it) }
    }

    suspend fun deleteFile(call: ApplicationCall) {
        val fileId = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required"))

        try {
            val success = fileService.deleteFile(fileId)
            if (success) {
                val successResponse = buildJsonObject {
                    put("success", true)
                    put("data", "File deleted successfully")
                    put("error", null)
                }
                call.respondText(successResponse.toString(), ContentType.Application.Json, HttpStatusCode.OK)
            } else {
                val errorResponse = buildJsonObject {
                    put("success", false)
                    put("data", null)
                    put("error", "File not found")
                }
                call.respondText(errorResponse.toString(), ContentType.Application.Json, HttpStatusCode.NotFound)
            }
        } catch (e: Exception) {
            val errorResponse = buildJsonObject {
                put("success", false)
                put("data", null)
                put("error", e.message ?: "Unknown error occurred")
            }
            call.respondText(errorResponse.toString(), ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }

    suspend fun getFileInfo(call: ApplicationCall) {
        val fileId = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required"))

        try {
            val fileInfo = fileService.getFileInfo(fileId)
            if (fileInfo != null) {
                call.respond(HttpStatusCode.OK, ResponseWrapper.success(fileInfo))
            } else {
                call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("File not found"))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error(e.message ?: "Unknown error occurred"))
        }
    }

    suspend fun getFileUploadStats(call: ApplicationCall) {
        try {
            // Check if user is admin
            val principal = call.principal<JWTPrincipal>()
            val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()
            
            if (!roles.contains("ADMIN")) {
                call.respond(HttpStatusCode.Forbidden, ResponseWrapper.error("Admin access required"))
                return
            }
            
            val userId = call.parameters["userId"]
            
            // Build the response manually using JsonObject to avoid serialization issues
            if (userId != null) {
                // Get stats for specific user
                val stats = fileService.getFileStatsByUser(userId)
                
                // Build the response manually
                val response = buildJsonObject {
                    put("success", true)
                    put("data", buildJsonObject {
                        put("userId", userId)
                        stats.username?.let { put("username", it) }
                        put("totalFiles", stats.totalFiles)
                        put("totalSize", stats.totalSize)
                        
                        // File types
                        put("fileTypes", buildJsonObject {
                            stats.fileTypes.forEach { (type, count) ->
                                put(type, count)
                            }
                        })
                        
                        stats.lastUpload?.let { put("lastUpload", it) }
                        
                        // Files array
                        put("files", buildJsonArray {
                            stats.files.forEach { file ->
                                add(buildJsonObject {
                                    put("id", file.id)
                                    put("fileName", file.fileName)
                                    put("storedFileName", file.storedFileName)
                                    put("contentType", file.contentType)
                                    put("size", file.size)
                                    put("uploadDate", file.uploadDate)
                                    file.userId?.let { put("userId", it) }
                                })
                            }
                        })
                    })
                    put("error", null)
                }
                
                call.respondText(response.toString(), ContentType.Application.Json, HttpStatusCode.OK)
            } else {
                // Get stats for all users
                val allStats = fileService.getAllFileStats()
                
                // Build the response manually
                val response = buildJsonObject {
                    put("success", true)
                    put("data", buildJsonArray {
                        allStats.forEach { stats ->
                            add(buildJsonObject {
                                stats.userId?.let { put("userId", it) }
                                stats.username?.let { put("username", it) }
                                put("totalFiles", stats.totalFiles)
                                put("totalSize", stats.totalSize)
                                
                                // File types
                                put("fileTypes", buildJsonObject {
                                    stats.fileTypes.forEach { (type, count) ->
                                        put(type, count)
                                    }
                                })
                                
                                stats.lastUpload?.let { put("lastUpload", it) }
                                
                                // Files array
                                put("files", buildJsonArray {
                                    stats.files.forEach { file ->
                                        add(buildJsonObject {
                                            put("id", file.id)
                                            put("fileName", file.fileName)
                                            put("storedFileName", file.storedFileName)
                                            put("contentType", file.contentType)
                                            put("size", file.size)
                                            put("uploadDate", file.uploadDate)
                                            file.userId?.let { put("userId", it) }
                                        })
                                    }
                                })
                            })
                        }
                    })
                    put("error", null)
                }
                
                call.respondText(response.toString(), ContentType.Application.Json, HttpStatusCode.OK)
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error(e.message ?: "Unknown error occurred"))
        }
    }

    private fun getContentTypeFromFileName(fileName: String): ContentType {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "png" -> ContentType.Image.PNG
            "gif" -> ContentType.Image.GIF
            "svg" -> ContentType.Image.SVG
            "webp" -> ContentType.parse("image/webp")
            "pdf" -> ContentType.parse("application/pdf")
            "doc", "docx" -> ContentType.parse("application/msword")
            "xls", "xlsx" -> ContentType.parse("application/vnd.ms-excel")
            "txt" -> ContentType.Text.Plain
            "html", "htm" -> ContentType.Text.Html
            "css" -> ContentType.Text.CSS
            "js" -> ContentType.parse("application/javascript")
            "json" -> ContentType.Application.Json
            "xml" -> ContentType.Application.Xml
            "zip" -> ContentType.parse("application/zip")
            "rar" -> ContentType.parse("application/x-rar-compressed")
            "mp3" -> ContentType.parse("audio/mpeg")
            "mp4" -> ContentType.parse("video/mp4")
            "avi" -> ContentType.parse("video/x-msvideo")
            "mov" -> ContentType.parse("video/quicktime")
            // Add more mappings as needed
            else -> ContentType.Application.OctetStream
        }
    }
}