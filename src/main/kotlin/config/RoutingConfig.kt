package org.anaphygon.config

import org.anaphygon.module.file.fileRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        fileRoutes()
    }
}