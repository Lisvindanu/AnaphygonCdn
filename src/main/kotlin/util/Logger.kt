// Logger.kt
package org.anaphygon.util

import org.slf4j.LoggerFactory

class Logger(tag: String) {
    private val logger = LoggerFactory.getLogger(tag)

    fun info(message: String) {
        logger.info(message)
    }

    fun debug(message: String) {
        logger.debug(message)
    }

    fun warn(message: String) {
        logger.warn(message)
    }

    fun error(message: String) {
        logger.error(message)
    }

    fun error(message: String, throwable: Throwable) {
        logger.error(message, throwable)
    }
}