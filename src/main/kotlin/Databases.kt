package org.anaphygon

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.sql.DriverManager

fun Application.configureDatabases() {
    // Basic DB health check endpoint
    routing {
        get("/db/health") {
            try {
                val dbUrl = "jdbc:h2:file:./data/cdn_db"
                val connection = DriverManager.getConnection(dbUrl, "root", "")
                val metadata = connection.metaData
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "status" to "connected",
                        "database" to "${metadata.databaseProductName} ${metadata.databaseProductVersion}"
                    )
                )
                connection.close()
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("status" to "error", "message" to e.message)
                )
            }
        }
    }
}