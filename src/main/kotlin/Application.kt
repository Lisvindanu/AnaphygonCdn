package org.anaphygon

import io.ktor.server.application.*
import org.anaphygon.plugin.configureH2Console
import org.anaphygon.plugin.configureStatic

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureHTTP()
    configureRouting()
    configureStatic() // Pastikan import configureStatic dari package yang benar
    configureH2Console()
}