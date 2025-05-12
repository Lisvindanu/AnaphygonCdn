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

class FileController(private val fileService: FileService) {

    suspend fun uploadFile(call: ApplicationCall) {
        try {
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
                val fileId = fileService.saveFile(fileName, fileContent!!)
                call.respond(HttpStatusCode.Created, ResponseWrapper.success(fileId))
            } else {
                call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("No file content received"))
            }
        } catch (e: Exception) {
            e.printStackTrace() // Tambahkan ini untuk debug
            call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error(e.message ?: "Unknown error occurred"))
        }
    }


    suspend fun getFile(call: ApplicationCall) {
        val fileId = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required"))

        try {
            val file = fileService.getFile(fileId)
            if (file != null) {
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.fileName).toString()
                )
                call.respondBytes(file.content, getContentTypeFromFileName(file.fileName))
            } else {
                call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("File not found"))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error(e.message ?: "Unknown error occurred"))
        }
    }

    suspend fun deleteFile(call: ApplicationCall) {
        val fileId = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, ResponseWrapper.error("File ID required"))

        try {
            val success = fileService.deleteFile(fileId)
            if (success) {
                call.respond(HttpStatusCode.OK, ResponseWrapper.success("File deleted successfully"))
            } else {
                call.respond(HttpStatusCode.NotFound, ResponseWrapper.error("File not found"))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ResponseWrapper.error(e.message ?: "Unknown error occurred"))
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