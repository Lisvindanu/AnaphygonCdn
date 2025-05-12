// src/main/kotlin/org/anaphygon/module/file/FileRoutes.kt
package org.anaphygon.module.file

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.anaphygon.util.ResponseWrapper
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add

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

        // Public endpoints
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
        }
    }
}