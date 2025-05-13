package org.anaphygon.plugin

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.anaphygon.util.Logger
import org.h2.tools.Server
import java.net.ServerSocket
import java.sql.DriverManager

fun Application.configureH2Console() {
    val logger = Logger("H2Console")
    
    try {
        // Find an available port
        val availablePort = findAvailablePort(8082, 8182)
        
        // Start H2 Console with the available port
        val h2Server = Server.createWebServer(
            "-web", 
            "-webAllowOthers", 
            "-webPort", 
            availablePort.toString()
        ).start()
        
        logger.info("H2 Console started on port $availablePort")
        
        environment.monitor.subscribe(ApplicationStopped) {
            h2Server.stop()
            logger.info("H2 Console stopped")
        }
    } catch (e: Exception) {
        logger.warn("Could not start H2 Console: ${e.message}. Application will continue without H2 Console.")
    }

    // Optional: Add endpoint to get DB connection info
    routing {
        get("/db-info") {
            try {
                val connection = DriverManager.getConnection("jdbc:h2:file:./data/cdn_db", "root", "")
                val metadata = connection.metaData
                call.respondText("Connected to ${metadata.databaseProductName} ${metadata.databaseProductVersion}")
                connection.close()
            } catch (e: Exception) {
                call.respondText("Error connecting to database: ${e.message}")
            }
        }
    }
}

/**
 * Find an available port in the given range
 * @param startPort The port to start checking from
 * @param endPort The port to end checking at
 * @return An available port number
 * @throws IllegalStateException if no ports are available in the range
 */
private fun findAvailablePort(startPort: Int, endPort: Int): Int {
    for (port in startPort..endPort) {
        try {
            ServerSocket(port).use {
                return port
            }
        } catch (e: Exception) {
            // Port is in use, try the next one
            continue
        }
    }
    throw IllegalStateException("No available ports in range $startPort-$endPort")
}