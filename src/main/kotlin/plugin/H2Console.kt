package org.anaphygon.plugin

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.h2.tools.Server
import java.sql.DriverManager

fun Application.configureH2Console() {
    // Start H2 Console
    val h2Server = Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082").start()

    environment.monitor.subscribe(ApplicationStopped) {
        h2Server.stop()
    }

    // Optional: Add endpoint to get DB connection info
    routing {
        get("/db-info") {
            val connection = DriverManager.getConnection("jdbc:h2:file:./build/db", "root", "")
            val metadata = connection.metaData
            call.respondText("Connected to ${metadata.databaseProductName} ${metadata.databaseProductVersion}")
            connection.close()
        }
    }
}