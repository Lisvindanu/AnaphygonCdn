// src/main/kotlin/org/anaphygon/Application.kt
package org.anaphygon

import io.ktor.server.application.*
import org.anaphygon.auth.configureAuth
import org.anaphygon.plugin.configureH2Console
import org.anaphygon.plugin.configureStatic

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Configure metrics first to make them available to other modules
    configureMonitoring()

    configureSerialization()
    configureDatabases()
    configureHTTP()
    configureAuth()
    configureRouting()
    configureStatic()
    configureH2Console()

    // Log application startup
    environment.monitor.subscribe(ApplicationStarted) {
        log.info("Application started successfully")
    }
}