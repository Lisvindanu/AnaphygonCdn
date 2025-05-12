plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "org.anaphygon"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenCentral()
}

dependencies {
    // Use direct library references from the catalog
    implementation("io.ktor:ktor-server-auth:${libs.versions.ktor.version.get()}")
    implementation("io.ktor:ktor-server-auth-jwt:${libs.versions.ktor.version.get()}")
    implementation("com.auth0:java-jwt:4.4.0")


    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    // Added for .env support
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    // Add missing bcrypt dependency for password hashing
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("org.jetbrains.exposed:exposed-java-time:0.41.1") // Use appropriate version

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}