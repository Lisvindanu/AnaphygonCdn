// ResponseWrapper.kt
package org.anaphygon.util

import kotlinx.serialization.Serializable

@Serializable
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