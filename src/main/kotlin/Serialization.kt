package org.anaphygon

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.anaphygon.util.ResponseWrapper
import org.anaphygon.util.ResponseWrapperSerializer
import org.anaphygon.util.JsonResponseWrapper
import org.anaphygon.data.model.FileMeta
import org.anaphygon.module.file.FileUploadStats
import kotlin.reflect.KClass

fun Application.configureSerialization() {
    // Create a JSON configuration with custom serializer modules
    val jsonConfig = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        
        // Enable serialization of classes without @Serializable
        encodeDefaults = true
        explicitNulls = false
        
        // Allow serializing Kotlin objects
        allowStructuredMapKeys = true
        
        // Workaround for generic type serialization issues
        classDiscriminator = "type"
        coerceInputValues = true
        
        // Configure serializers module with our ResponseWrapper serializer
        serializersModule = SerializersModule {
            // Register our star-projected ResponseWrapper serializer
            contextual(ResponseWrapper::class as KClass<ResponseWrapper<*>>, ResponseWrapperSerializer())
        }
    }

    // Install content negotiation with our custom JSON configuration
    install(ContentNegotiation) {
        json(jsonConfig)
    }

    // Alternative approach for handling ResponseWrapper
    this.intercept(ApplicationCallPipeline.Plugins) {
        call.response.pipeline.intercept(io.ktor.server.response.ApplicationSendPipeline.Transform) { subject ->
            if (subject is ResponseWrapper<*>) {
                // Simply convert to JsonResponseWrapper if the ResponseWrapper serializer fails
                try {
                    // Let the serializer handle it first
                    val json = jsonConfig.encodeToString(subject)
                    proceedWith(TextContent(json, ContentType.Application.Json))
                } catch (e: Exception) {
                    // Fallback to manual conversion to JsonResponseWrapper
                    val jsonObject = buildJsonObject {
                        put("success", subject.success)
                        
                        // Handle data
                        if (subject.data != null) {
                            when (subject.data) {
                                is String -> put("data", JsonPrimitive(subject.data as String))
                                is Number -> put("data", JsonPrimitive(subject.data as Number))
                                is Boolean -> put("data", JsonPrimitive(subject.data as Boolean))
                                else -> put("data", JsonPrimitive(subject.data.toString()))
                            }
                        } else {
                            put("data", null)
                        }
                        
                        // Add error field
                        if (subject.error != null) {
                            put("error", JsonPrimitive(subject.error))
                        } else {
                            put("error", null)
                        }
                    }
                    
                    proceedWith(TextContent(jsonObject.toString(), ContentType.Application.Json))
                }
            }
        }
    }

    routing {
        get("/json/kotlinx-serialization") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}