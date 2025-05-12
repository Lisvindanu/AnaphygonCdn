package org.anaphygon.plugin

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureStatic() {
    // Pastikan direktori uploads ada
    val uploadsDir = "uploads" // Atau gunakan dari konfigurasi jika ada
    File(uploadsDir).mkdirs()

    routing {
        // Serve files directly from the uploads directory
        static("/cdn") {
            files(uploadsDir)
        }
    }
}