package org.anaphygon.config

import io.github.cdimascio.dotenv.dotenv

object Env {
    private val env = dotenv {
        ignoreIfMissing = true
    }

    operator fun get(key: String): String? = env[key]

    fun getOrDefault(key: String, defaultValue: String): String = env[key] ?: defaultValue
}