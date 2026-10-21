package net.agolyakov.firelamp.protocol

import java.io.ByteArrayOutputStream

/** A value from the lamp that does not match the protocol (too short, bad string length...). */
class ProtocolException(message: String) : Exception(message)

/** The lamp speaks a protocol version this app does not know. */
class UnsupportedProtocolException(val version: Int) :
    Exception("Unsupported FireLamp protocol version $version")

/** Sequential little-endian reader over a characteristic value. */
class ByteReader(private val bytes: ByteArray) {
    var position = 0
        private set

    val remaining: Int get() = bytes.size - position

    private fun need(count: Int) {
        if (remaining < count) {
            throw ProtocolException("Value too short: need $count bytes at $position, have $remaining")
        }
    }

    fun u8(): Int {
        need(1)
        return bytes[position++].toInt() and 0xFF
    }

    fun i8(): Int {
        need(1)
        return bytes[position++].toInt()
    }

    fun bool(): Boolean = u8() != 0

    fun u16(): Int = u8() or (u8() shl 8)

    fun u32(): Long = u16().toLong() or (u16().toLong() shl 16)

    fun bytes(count: Int): ByteArray {
        need(count)
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    /** Length-prefixed UTF-8 string. */
    fun str8(): String = String(bytes(u8()), Charsets.UTF_8)

    /** The rest of the value as a UTF-8 string (a characteristic holding a single string). */
    fun restAsString(): String = String(bytes(remaining), Charsets.UTF_8)
}

/** Little-endian builder for characteristic values; arguments are range-checked. */
class ByteWriter {
    private val out = ByteArrayOutputStream()

    fun u8(value: Int) = apply {
        require(value in 0..0xFF) { "u8 out of range: $value" }
        out.write(value)
    }

    fun bool(value: Boolean) = u8(if (value) 1 else 0)

    fun u16(value: Int) = apply {
        require(value in 0..0xFFFF) { "u16 out of range: $value" }
        out.write(value and 0xFF)
        out.write(value ushr 8)
    }

    fun u32(value: Long) = apply {
        require(value in 0..0xFFFF_FFFFL) { "u32 out of range: $value" }
        u16((value and 0xFFFF).toInt())
        u16((value ushr 16).toInt())
    }

    fun bytes(value: ByteArray) = apply { out.write(value) }

    /** Length-prefixed UTF-8 string of at most [maxBytes] bytes. */
    fun str8(value: String, maxBytes: Int = 255) = apply {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= maxBytes) { "String longer than $maxBytes bytes: \"$value\"" }
        u8(encoded.size)
        out.write(encoded)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

inline fun bytesOf(block: ByteWriter.() -> Unit): ByteArray = ByteWriter().apply(block).toByteArray()

/** UTF-8 length in bytes, which is what the protocol limits (not characters). */
fun String.utf8Length(): Int = toByteArray(Charsets.UTF_8).size
