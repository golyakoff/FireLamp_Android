package net.agolyakov.firelamp.utils

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

object HashUtils {

    /**
     * Calculates SHA256 hash of a file
     */
    fun calculateSha256(file: File): String {
        return file.inputStream().use { calculateSha256(it) }
    }

    /**
     * Calculates SHA256 hash of an input stream
     */
    fun calculateSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }

        return bytesToHex(digest.digest())
    }

    /**
     * Calculates SHA256 hash of a byte array
     */
    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data)
        return bytesToHex(digest.digest())
    }

    /**
     * Verifies file hash against expected hash
     */
    fun verifyFileHash(file: File, expectedHash: String): Boolean {
        val actualHash = calculateSha256(file)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * Verifies input stream hash against expected hash
     */
    fun verifyStreamHash(inputStream: InputStream, expectedHash: String): Boolean {
        val actualHash = calculateSha256(inputStream)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * Extracts clean hash from GitHub digest format "sha256:abcdef..."
     */
    fun extractCleanHash(githubDigest: String?): String? {
        return githubDigest?.removePrefix("sha256:")?.lowercase(Locale.US)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }
}