// ResponseWrapper.kt
package org.anaphygon.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Non-generic version for JSON responses
@Serializable
data class JsonResponseWrapper(
    val success: Boolean,
    val data: JsonElement? = null,
    val error: String? = null
)

// Generic version - note we don't use @Serializable because we handle serialization
// manually in the controllers that need it
data class ResponseWrapper<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
) {
    companion object {
        fun <T> success(data: T): ResponseWrapper<T> {
            return ResponseWrapper(true, data, null)
        }

        fun error(message: String): ResponseWrapper<String> {
            return ResponseWrapper(false, null, message)
        }
    }
}

/**
 * Custom serializer for ResponseWrapper to handle generic types
 */
class ResponseWrapperSerializer : KSerializer<ResponseWrapper<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ResponseWrapper") {
        element<Boolean>("success")
        element<JsonElement?>("data")
        element<String?>("error")
    }

    override fun serialize(encoder: Encoder, value: ResponseWrapper<*>) {
        val jsonObject = buildJsonObject {
            put("success", value.success)
            
            // Handle data field
            if (value.data != null) {
                when (value.data) {
                    is String -> put("data", JsonPrimitive(value.data as String))
                    is Number -> put("data", JsonPrimitive(value.data as Number))
                    is Boolean -> put("data", JsonPrimitive(value.data as Boolean))
                    is JsonElement -> put("data", value.data as JsonElement)
                    else -> put("data", JsonPrimitive(value.data.toString()))
                }
            } else {
                put("data", JsonNull)
            }
            
            // Handle error field
            if (value.error != null) {
                put("error", JsonPrimitive(value.error))
            } else {
                put("error", JsonNull)
            }
        }
        
        encoder.encodeSerializableValue(JsonObject.serializer(), jsonObject)
    }

    override fun deserialize(decoder: Decoder): ResponseWrapper<*> {
        throw UnsupportedOperationException("Deserialization of ResponseWrapper is not supported")
    }
}