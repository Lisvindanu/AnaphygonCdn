package org.anaphygon

import io.ktor.server.application.*
import org.anaphygon.auth.configureAuth
import org.anaphygon.auth.service.JwtService
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.config.SecureConfig
import org.anaphygon.plugin.configureH2Console
import org.anaphygon.plugin.configureStatic
import org.anaphygon.config.configureRouting
import org.anaphygon.email.EmailService
import org.anaphygon.security.configureCsrfProtection
import org.anaphygon.security.configureRateLimiting
import org.jetbrains.exposed.sql.Database
import org.anaphygon.data.db.DatabaseFactory

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Create Database connection with secure config
    val database = Database.connect(
        url = SecureConfig.dbUrl,
        driver = environment.config.property("database.driverClassName").getString(),
        user = SecureConfig.dbUser,
        password = SecureConfig.dbPassword
    )

    // Initialize database schema and run migrations FIRST
    DatabaseFactory.init(database)

    // Initialize auth schema SECOND
    org.anaphygon.auth.db.DatabaseInit.init(database)

    // Initialize services THIRD
    val userRoleService = UserRoleService(database)
    val jwtService = JwtService()
    val emailService = EmailService()

    // Configure in correct order:
    // 1. Basic infrastructure
    configureSerialization()
    configureDatabases()

    // 2. Monitoring (before request handling)
    configureMonitoring()

    // 3. Security in correct order
    configureHTTP()  // CORS configuration
    configureCsrfProtection()
    configureRateLimiting()
    configureAuth(jwtService)

    // 4. Route configuration last
    configureRouting(userRoleService, jwtService, emailService)
    configureStatic()
    configureH2Console()

    // Log application startup
    environment.monitor.subscribe(ApplicationStarted) {
        log.info("Application started successfully")
    }
}