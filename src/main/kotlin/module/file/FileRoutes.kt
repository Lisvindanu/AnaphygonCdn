package org.anaphygon.module.file

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.anaphygon.util.ResponseWrapper

fun Route.fileRoutes() {
    val fileService = FileService()
    val fileController = FileController(fileService)

    route("/api/files") {
        // Endpoint untuk mendapatkan semua file
        get {
            try {
                val files = fileService.getAllFiles()
                call.respond(HttpStatusCode.OK, ResponseWrapper.success(files))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error("Error retrieving files: ${e.message}"))
            }
        }

        // Endpoint untuk melihat status database
        get("/db-status") {
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

        post {
            fileController.uploadFile(call)
        }

        get("/{id}") {
            fileController.getFile(call)
        }

        delete("/{id}") {
            fileController.deleteFile(call)
        }

        get("/{id}/info") {
            fileController.getFileInfo(call)
        }
    }
}