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
            // Get all files
            get {
                try {
                    // Get user ID from JWT token
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("id")?.asString()
                    
                    val files = fileService.getAllFiles()
                    
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

            // Delete
            delete("/{id}") {
                fileController.deleteFile(call)
            }

            // File info
            get("/{id}/info") {
                fileController.getFileInfo(call)
            }
        }
    }
}