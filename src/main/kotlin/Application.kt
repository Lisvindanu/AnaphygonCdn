// src/main/kotlin/org/anaphygon/Application.kt
package org.anaphygon

import io.ktor.server.application.*
import org.anaphygon.auth.configureAuth
import org.anaphygon.auth.service.JwtService
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.plugin.configureH2Console
import org.anaphygon.plugin.configureStatic
import org.anaphygon.config.configureRouting
import org.anaphygon.security.configureCsrfProtection
import org.anaphygon.security.configureRateLimiting
import org.jetbrains.exposed.sql.Database

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Create Database connection
    val database = Database.connect(
        url = environment.config.property("database.jdbcURL").getString(),
        driver = environment.config.property("database.driverClassName").getString(),
        user = "root",
        password = ""
    )

    // Initialize services
    val userRoleService = UserRoleService(database)
    val jwtService = JwtService()

    // Configure metrics first to make them available to other modules
    configureMonitoring()

    // Initialize user roles and permissions
    org.anaphygon.auth.db.DatabaseInit.init(database)

    // Configure remaining modules
    configureSerialization()
    configureDatabases()
    configureHTTP()
    configureCsrfProtection()
    configureRateLimiting()
    configureAuth(jwtService)  // Pass jwtService here
    configureRouting()
    configureStatic()
    configureH2Console()

    // Log application startup
    environment.monitor.subscribe(ApplicationStarted) {
        log.info("Application started successfully")
    }
}