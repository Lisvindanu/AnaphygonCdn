// src/main/kotlin/org/anaphygon/module/file/ImageTransformer.kt
package org.anaphygon.module.file

import org.anaphygon.util.Logger
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageTransformer {
    private val logger = Logger("ImageTransformer")

    fun resizeImage(imageData: ByteArray, width: Int, height: Int, format: String = "jpg"): ByteArray? {
        return try {
            val inputStream = ByteArrayInputStream(imageData)
            val originalImage = ImageIO.read(inputStream)

            val resizedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val graphics = resizedImage.createGraphics()
            graphics.drawImage(
                originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH),
                0, 0, null
            )
            graphics.dispose()

            val outputStream = ByteArrayOutputStream()
            ImageIO.write(resizedImage, format, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            logger.error("Error resizing image: ${e.message}")
            null
        }
    }

    fun generateThumbnail(imageData: ByteArray, size: Int = 200, format: String = "jpg"): ByteArray? {
        return resizeImage(imageData, size, size, format)
    }

    fun isImage(contentType: String): Boolean {
        return contentType.startsWith("image/")
    }

    fun getFormatFromContentType(contentType: String): String {
        return when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }
}