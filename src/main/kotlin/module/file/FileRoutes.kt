// src/main/kotlin/org/anaphygon/module/file/FileRoutes.kt
package org.anaphygon.module.file

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import org.anaphygon.util.ResponseWrapper
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonObject

fun Route.fileRoutes() {
    val fileService = FileService()
    val fileController = FileController(fileService, application)

    route("/api/files") {
        // Enable CORS for OPTIONS requests
        options {
            call.respond(HttpStatusCode.OK)
        }

        options("/{id}") {
            call.respond(HttpStatusCode.OK)
        }

        // Public endpoint to fetch all public and approved files
        get("/public") {
            try {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                val files = fileService.getPublicFiles(page, pageSize)

                // Create JSON response with files
                val response = buildJsonObject {
                    put("success", true)
                    put("data", buildJsonArray {
                        files.forEach { file ->
                            add(buildJsonObject {
                                put("id", file.id)
                                put("fileName", file.fileName)
                                put("contentType", file.contentType)
                                put("size", file.size)
                                put("uploadDate", file.uploadDate)
                                put("userId", file.userId ?: "")
                                put("isPublic", file.isPublic ?: false)
                                put("moderationStatus", file.moderationStatus ?: "PENDING")
                            })
                        }
                    })
                    put("error", null)
                }

                call.respondText(response.toString(), ContentType.Application.Json)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error(e.message ?: "Error retrieving public files"))
            }
        }

        // Public endpoints for individual files
        get("/{id}") {
            fileController.getFile(call)
        }

        // Thumbnail generation - public
        get("/{id}/thumbnail") {
            fileController.getThumbnail(call)
        }

        get("/{id}/thumbnail/{size}") {
            fileController.getThumbnail(call)
        }

        // Image resizing - public
        get("/{id}/resize") {
            fileController.resizeImage(call)
        }

        // Protected endpoints for file management
        authenticate("auth-jwt") {
            // User route to update visibility of their own files
            patch("/{id}/visibility") {
                val fileId = call.parameters["id"] ?: return@patch call.respond(
                    HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required")
                )

                try {
                    // Get user ID from JWT
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("id")?.asString()

                    // Get file to check ownership
                    val file = fileService.getFileInfo(fileId)

                    if (file == null) {
                        call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("File not found"))
                        return@patch
                    }

                    // Verify owner or admin
                    val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()
                    val isAdmin = roles.contains("ADMIN")

                    if (file.userId != userId && !isAdmin) {
                        call.respond(HttpStatusCode.Forbidden, ResponseWrapper.error("You don't have permission to modify this file"))
                        return@patch
                    }

                    // Get visibility from request body
                    val requestText = call.receiveText()
                    application.log.info("Received visibility request body: $requestText")
                    
                    // Parse the JSON manually to avoid serialization issues
                    val isPublic = try {
                        val jsonObject = kotlinx.serialization.json.Json.parseToJsonElement(requestText).jsonObject
                        jsonObject["isPublic"]?.jsonPrimitive?.boolean
                    } catch (e: Exception) {
                        application.log.error("Failed to parse request JSON: ${e.message}")
                        null
                    } ?: return@patch call.respond(
                        HttpStatusCode.BadRequest, ResponseWrapper.error("isPublic parameter required")
                    )

                    // Log detailed information for debugging
                    application.log.info("Attempting to update visibility for file $fileId to isPublic=$isPublic")
                    
                    // Update visibility
                    val success = fileService.updateFileVisibility(fileId, isPublic)

                    if (success) {
                        call.respond(HttpStatusCode.OK, ResponseWrapper.success("File visibility updated successfully"))
                    } else {
                        application.log.error("Failed to update visibility for file $fileId")
                        call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Failed to update file visibility"))
                    }
                } catch (e: Exception) {
                    application.log.error("Error updating file visibility: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Error updating file visibility: ${e.message}"))
                }
            }

            // Admin route for moderation
            patch("/{id}/moderate") {
                val fileId = call.parameters["id"] ?: return@patch call.respond(
                    HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required")
                )

                try {
                    // Verify admin
                    val principal = call.principal<JWTPrincipal>()
                    val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()

                    if (!roles.contains("ADMIN")) {
                        call.respond(HttpStatusCode.Forbidden, ResponseWrapper.error("Admin access required"))
                        return@patch
                    }

                    // Get status from request body
                    val requestText = call.receiveText()
                    application.log.info("Received moderation request body: $requestText")
                    
                    // Parse the JSON manually to avoid serialization issues
                    val status = try {
                        val jsonObject = kotlinx.serialization.json.Json.parseToJsonElement(requestText).jsonObject
                        jsonObject["status"]?.jsonPrimitive?.content
                    } catch (e: Exception) {
                        application.log.error("Failed to parse request JSON: ${e.message}")
                        null
                    } ?: return@patch call.respond(
                        HttpStatusCode.BadRequest, ResponseWrapper.error("Status required")
                    )

                    // Validate status
                    if (status !in listOf("APPROVED", "REJECTED", "PENDING")) {
                        call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("Invalid status. Must be APPROVED, REJECTED, or PENDING"))
                        return@patch
                    }

                    // Log detailed information for debugging
                    application.log.info("Attempting to moderate file $fileId with status $status")
                    
                    // Update moderation status
                    val success = fileService.moderateFile(fileId, status)

                    if (success) {
                        call.respond(HttpStatusCode.OK, ResponseWrapper.success("File moderation status updated successfully"))
                    } else {
                        application.log.error("Failed to update moderation status for file $fileId")
                        call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Failed to update moderation status"))
                    }
                } catch (e: Exception) {
                    application.log.error("Error updating moderation status: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Error updating moderation status: ${e.message}"))
                }
            }

            // Get user's files or all files if admin
            get {
                try {
                    // Get user ID from JWT token
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("id")?.asString()

                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ResponseWrapper.error("Authentication required"))
                        return@get
                    }

                    // Check if user is admin
                    val roles = principal.payload.getClaim("roles")?.asList(String::class.java) ?: emptyList()
                    val isAdmin = roles.contains("ADMIN")

                    // Get files based on role
                    val files = if (isAdmin) {
                        // Admins can see all files
                        fileService.getAllFiles()
                    } else {
                        // Regular users can only see their own files
                        fileService.getFilesByUserId(userId)
                    }

                    // Create proper JSON response with array of files
                    val response = buildJsonObject {
                        put("success", true)
                        put("data", buildJsonArray {
                            files.forEach { file ->
                                add(buildJsonObject {
                                    put("id", file.id)
                                    put("fileName", file.fileName)
                                    put("storedFileName", file.storedFileName)
                                    put("contentType", file.contentType)
                                    put("size", file.size)
                                    put("uploadDate", file.uploadDate)
                                    file.userId?.let { put("userId", it) }
                                    put("isPublic", file.isPublic ?: false)
                                    put("moderationStatus", file.moderationStatus ?: "PENDING")
                                })
                            }
                        })
                        put("error", null)
                    }

                    call.respondText(response.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Error retrieving files: ${e.message}"))
                }
            }

            // DB status - restricted to admins
            get("/db-status") {
                val principal = call.principal<JWTPrincipal>()
                val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()

                if (!roles.contains("ADMIN")) {
                    call.respond(HttpStatusCode.Forbidden, ResponseWrapper.error("Admin access required"))
                    return@get
                }

                try {
                    val filesFromDB = fileService.getAllFilesFromDB()

                    // Create proper JSON response with array of files
                    val response = buildJsonObject {
                        put("success", true)
                        put("data", buildJsonObject {
                            put("database_status", "connected")
                            put("file_count", filesFromDB.size)
                            put("schema_info", convertMapToJsonObject(fileService.getDatabaseSchemaInfo()))
                            put("files", buildJsonArray {
                                filesFromDB.forEach { file ->
                                    add(buildJsonObject {
                                        put("id", file.id)
                                        put("fileName", file.fileName)
                                        put("storedFileName", file.storedFileName)
                                        put("contentType", file.contentType)
                                        put("size", file.size)
                                        put("uploadDate", file.uploadDate)
                                        file.userId?.let { put("userId", it) }
                                        put("isPublic", file.isPublic ?: false)
                                        put("moderationStatus", file.moderationStatus ?: "PENDING")
                                    })
                                }
                            })
                        })
                        put("error", null)
                    }

                    call.respondText(response.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Database error: ${e.message}"))
                }
            }

            // Admin stats endpoints
            route("/stats") {
                options {
                    call.respond(HttpStatusCode.OK)
                }

                get {
                    fileController.getFileUploadStats(call)
                }

                get("/{userId}") {
                    fileController.getFileUploadStats(call)
                }
            }

            // Direct moderation endpoint for testing
            get("/test-moderate/{id}/{status}") {
                val principal = call.principal<JWTPrincipal>()
                val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()

                if (!roles.contains("ADMIN")) {
                    call.respond(HttpStatusCode.Forbidden, ResponseWrapper.error("Admin access required"))
                    return@get
                }

                val fileId = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required")
                )
                
                val status = call.parameters["status"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, ResponseWrapper.error("Status required")
                )
                
                if (status !in listOf("APPROVED", "REJECTED", "PENDING")) {
                    call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("Invalid status. Must be APPROVED, REJECTED, or PENDING"))
                    return@get
                }
                
                try {
                    // First check if file exists
                    val fileInfo = fileService.getFileInfo(fileId)
                    if (fileInfo == null) {
                        application.log.error("Test moderation: File $fileId not found")
                        call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("File not found"))
                        return@get
                    }
                    
                    application.log.info("Test moderation: File found: $fileInfo")
                    application.log.info("Test moderation: Attempting to moderate file $fileId with status $status")
                    
                    // Get database schema info for debugging
                    val schemaInfo = fileService.getDatabaseSchemaInfo()
                    application.log.info("Database schema info: $schemaInfo")
                    
                    val success = fileService.moderateFile(fileId, status)
                    
                    if (success) {
                        application.log.info("Test moderation: Successfully updated moderation status")
                        call.respond(HttpStatusCode.OK, ResponseWrapper.success("File moderation status updated successfully"))
                    } else {
                        application.log.error("Test moderation: Failed to update moderation status for file $fileId")
                        call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Failed to update moderation status"))
                    }
                } catch (e: Exception) {
                    application.log.error("Test moderation: Error updating moderation status: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Error updating moderation status: ${e.message}"))
                }
            }

            // Upload
            post {
                // Extract user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString()

                // Store the userId with the upload
                if (userId != null) {
                    call.attributes.put(FileController.USER_ID_KEY, userId)
                }

                fileController.uploadFile(call)
            }

            // Delete with permission check
            delete("/{id}") {
                try {
                    val fileId = call.parameters["id"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ResponseWrapper.error("File ID required")
                    )

                    // Get user ID from JWT token
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("id")?.asString()

                    // Get file info to check ownership
                    val fileInfo = fileService.getFileInfo(fileId)
                    if (fileInfo == null) {
                        call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("File not found"))
                        return@delete
                    }

                    // Check if user is admin or the file owner
                    val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()
                    val isAdmin = roles.contains("ADMIN")
                    val isOwner = fileInfo.userId == userId

                    if (!isAdmin && !isOwner) {
                        call.respond(HttpStatusCode.Forbidden, ResponseWrapper.error("You don't have permission to delete this file"))
                        return@delete
                    }

                    // User has permission, delete the file
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
                            put("error", "File not found or could not be deleted")
                        }
                        call.respondText(errorResponse.toString(), ContentType.Application.Json, HttpStatusCode.NotFound)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ResponseWrapper.error("Error deleting file: ${e.message}")
                    )
                }
            }

            // File info with permission check
            get("/{id}/info") {
                try {
                    val fileId = call.parameters["id"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ResponseWrapper.error("File ID required")
                    )

                    // Get user ID from JWT token
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("id")?.asString()

                    // Get file info
                    val fileInfo = fileService.getFileInfo(fileId)
                    if (fileInfo == null) {
                        call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("File not found"))
                        return@get
                    }

                    // Check if user is admin or the file owner
                    val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()
                    val isAdmin = roles.contains("ADMIN")
                    val isOwner = fileInfo.userId == userId

                    if (!isAdmin && !isOwner) {
                        call.respond(HttpStatusCode.Forbidden, ResponseWrapper.error("You don't have permission to access this file's info"))
                        return@get
                    }

                    // User has permission, return the file info
                    call.respond(HttpStatusCode.OK, ResponseWrapper.success(fileInfo))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ResponseWrapper.error("Error retrieving file info: ${e.message}")
                    )
                }
            }

            // Direct SQL moderation endpoint for emergency use
            get("/sql-moderate/{id}/{status}") {
                val principal = call.principal<JWTPrincipal>()
                val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()

                if (!roles.contains("ADMIN")) {
                    call.respond(HttpStatusCode.Forbidden, ResponseWrapper.error("Admin access required"))
                    return@get
                }

                val fileId = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required")
                )
                
                val status = call.parameters["status"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, ResponseWrapper.error("Status required")
                )
                
                if (status !in listOf("APPROVED", "REJECTED", "PENDING")) {
                    call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("Invalid status. Must be APPROVED, REJECTED, or PENDING"))
                    return@get
                }
                
                try {
                    application.log.info("SQL moderation: Attempting to moderate file $fileId with status $status")
                    
                    // Execute direct SQL update
                    val result = fileService.executeDirectSqlModeration(fileId, status)
                    
                    call.respond(HttpStatusCode.OK, ResponseWrapper.success(
                        "SQL moderation result: $result"
                    ))
                } catch (e: Exception) {
                    application.log.error("SQL moderation: Error updating moderation status: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Error updating moderation status: ${e.message}"))
                }
            }

            // Direct SQL visibility update endpoint for emergency use
            get("/test-visibility/{id}/{isPublic}") {
                val principal = call.principal<JWTPrincipal>()
                val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()
                val userId = principal?.payload?.getClaim("id")?.asString()

                val fileId = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required")
                )
                
                val isPublic = call.parameters["isPublic"]?.toBoolean() ?: return@get call.respond(
                    HttpStatusCode.BadRequest, ResponseWrapper.error("isPublic parameter required (true/false)")
                )
                
                try {
                    // Get file to check ownership
                    val file = fileService.getFileInfo(fileId)

                    if (file == null) {
                        call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("File not found"))
                        return@get
                    }

                    // Verify owner or admin
                    val isAdmin = roles.contains("ADMIN")
                    val isOwner = file.userId == userId

                    if (!isAdmin && !isOwner) {
                        call.respond(HttpStatusCode.Forbidden, ResponseWrapper.error("You don't have permission to modify this file"))
                        return@get
                    }
                    
                    application.log.info("Test visibility: Attempting to update visibility for file $fileId to isPublic=$isPublic")
                    
                    // Update visibility
                    val success = fileService.updateFileVisibility(fileId, isPublic)
                    
                    if (success) {
                        application.log.info("Test visibility: Successfully updated visibility")
                        call.respond(HttpStatusCode.OK, ResponseWrapper.success("File visibility updated successfully"))
                    } else {
                        application.log.error("Test visibility: Failed to update visibility for file $fileId")
                        call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Failed to update file visibility"))
                    }
                } catch (e: Exception) {
                    application.log.error("Test visibility: Error updating visibility: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Error updating file visibility: ${e.message}"))
                }
            }
        }
    }
}

private fun convertMapToJsonObject(map: Map<String, Any>): JsonObject {
    return buildJsonObject {
        map.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Number -> put(key, value)
                is Boolean -> put(key, value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    put(key, convertMapToJsonObject(value as Map<String, Any>))
                }
                is List<*> -> {
                    put(key, buildJsonArray {
                        value.forEach { item ->
                            when (item) {
                                is String -> add(item)
                                is Number -> add(item)
                                is Boolean -> add(item)
                                is Map<*, *> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    add(convertMapToJsonObject(item as Map<String, Any>))
                                }
                                null -> add(JsonNull)
                                else -> add(item.toString())
                            }
                        }
                    })
                }
                null -> put(key, JsonNull)
                else -> put(key, value.toString())
            }
        }
    }
}