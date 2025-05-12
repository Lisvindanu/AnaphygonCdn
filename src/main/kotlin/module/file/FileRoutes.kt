// src/main/kotlin/org/anaphygon/module/file/FileRoutes.kt
package org.anaphygon.module.file

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.anaphygon.util.ResponseWrapper

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
                    val files = fileService.getAllFiles()
                    call.respond(HttpStatusCode.OK, ResponseWrapper.success(files))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Error retrieving files: ${e.message}"))
                }
            }

            // DB status - restricted to admins
            get("/db-status") {
                val principal = call.principal<JWTPrincipal>()
                val role = principal?.payload?.getClaim("role")?.asString()

                if (role != "admin") {
                    call.respond(HttpStatusCode.Forbidden, ResponseWrapper.error("Admin access required"))
                    return@get
                }

                try {
                    val filesFromDB = fileService.getAllFilesFromDB()
                    call.respond(HttpStatusCode.OK, ResponseWrapper.success(mapOf(
                        "database_status" to "connected",
                        "file_count" to filesFromDB.size,
                        "files" to filesFromDB
                    )))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Database error: ${e.message}"))
                }
            }

            // Upload
            post {
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