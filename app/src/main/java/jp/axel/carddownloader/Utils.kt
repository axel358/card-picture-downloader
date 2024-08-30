package jp.axel.carddownloader

import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object Utils {
    val missingCards = mutableListOf<String>()
    fun fileNameFromUri(uri: Uri): String {
        val schemeSpecificPart = uri.schemeSpecificPart
        return schemeSpecificPart.substring(
            schemeSpecificPart.lastIndexOf("/") + 1,
            schemeSpecificPart.lastIndexOf(".")
        )
    }

    fun copy(input: InputStream, output: OutputStream) {
        try {
            output.use { output ->
                BufferedInputStream(input).use { inputStream ->
                    BufferedOutputStream(output).use { outputStream ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.flush()
                    }
                }
            }
        } catch (_: IOException) {
        }
    }
}