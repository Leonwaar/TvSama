package fr.nekotv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.math.abs

/** Decode the published LiveLoader transport envelopes before Media3 sees the TS packets. */
internal object WrappedTs {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)
    private val pixelTag = "TIKTIKPX".toByteArray(Charsets.US_ASCII)
    private val rawTag = "TIKTIKRAW".toByteArray(Charsets.US_ASCII)
    private val gzipTag = "TIKTIKTSGZ".toByteArray(Charsets.US_ASCII)

    fun isWrapped(bytes: ByteArray): Boolean = bytes.starts(png) ||
        bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WEBP" ||
        bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() ||
        bytes.ascii(0, 6).startsWith("TIKTIK")

    fun decode(bytes: ByteArray): ByteArray? {
        if (!isWrapped(bytes)) return null
        checkSize(bytes.size.toLong())
        val result = when {
            bytes.starts(png) -> pngPayload(bytes)
            bytes.ascii(0, 4) == "RIFF" -> webpPayload(bytes)
            else -> {
                val raw = bytes.indexOf(rawTag)
                val gz = bytes.indexOf(gzipTag)
                when {
                    raw >= 0 -> bytes.copyOfRange(raw + rawTag.size, bytes.size)
                    gz >= 0 -> GZIPInputStream(ByteArrayInputStream(bytes, gz + gzipTag.size, bytes.size - gz - gzipTag.size)).use(::readBounded)
                    else -> throw IOException("Enveloppe de segment non reconnue")
                }
            }
        }
        if (!validTs(result)) throw IOException("Enveloppe sans paquets MPEG-TS valides")
        return result
    }

    fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            checkSize(output.size().toLong() + count)
            output.write(buffer, 0, count)
        }
    }

    private fun validTs(bytes: ByteArray) = bytes.size >= 188 * 3 &&
        bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte() && bytes[376] == 0x47.toByte()
    private fun checkSize(size: Long) { if (size !in 0..MAX_BYTES.toLong()) throw IOException("Segment trop volumineux") }
    private fun ByteArray.starts(tag: ByteArray, offset: Int = 0) = offset >= 0 && size - offset >= tag.size && tag.indices.all { this[offset + it] == tag[it] }
    private fun ByteArray.ascii(offset: Int, count: Int) = if (offset >= 0 && size - offset >= count) String(this, offset, count, Charsets.US_ASCII) else ""
    private fun ByteArray.indexOf(tag: ByteArray): Int { for (i in 0..size - tag.size) if (starts(tag, i)) return i; return -1 }
    private fun ByteArray.u32(offset: Int, little: Boolean = false): Long {
        if (offset < 0 || size - offset < 4) throw IOException("Segment tronqué")
        var value = 0L
        for (i in 0..3) value = value shl 8 or (this[offset + if (little) 3 - i else i].toLong() and 255)
        return value
    }

    private fun webpPayload(bytes: ByteArray): ByteArray {
        var offset = 12
        while (offset <= bytes.size - 8) {
            val length = bytes.u32(offset + 4, little = true)
            if (length > bytes.size - offset - 8) throw IOException("WebP tronqué")
            if (bytes.ascii(offset, 4) == "EXIF") return bytes.copyOfRange(offset + 8, offset + 8 + length.toInt())
            offset += 8 + length.toInt() + (length.toInt() and 1)
        }
        throw IOException("Paquets MPEG-TS absents du WebP")
    }

    private fun pngPayload(bytes: ByteArray): ByteArray {
        var offset = 8; var width = 0; var height = 0; var channels = 0
        val compressed = ByteArrayOutputStream()
        var ended = false
        while (offset <= bytes.size - 12) {
            val length = bytes.u32(offset)
            if (length > bytes.size - offset - 12) throw IOException("PNG tronqué")
            val data = offset + 8
            when (bytes.ascii(offset + 4, 4)) {
                "IHDR" -> {
                    if (length != 13L) throw IOException("En-tête PNG invalide")
                    width = bytes.u32(data).also(::checkSize).toInt()
                    height = bytes.u32(data + 4).also(::checkSize).toInt()
                    channels = when (bytes[data + 9].toInt()) { 2 -> 3; 6 -> 4; else -> 0 }
                    if (width <= 0 || height <= 0 || channels == 0 || bytes[data + 8].toInt() != 8 ||
                        bytes[data + 10].toInt() != 0 || bytes[data + 11].toInt() != 0 || bytes[data + 12].toInt() != 0)
                        throw IOException("Format PNG de segment non pris en charge")
                    checkSize(width.toLong() * height * channels + height)
                }
                "IDAT" -> compressed.write(bytes, data, length.toInt())
                "IEND" -> {
                    offset += 12 + length.toInt()
                    ended = true
                    if (offset < bytes.size && validTs(bytes.copyOfRange(offset, bytes.size))) return bytes.copyOfRange(offset, bytes.size)
                    break
                }
            }
            offset += 12 + length.toInt()
        }
        if (!ended || channels == 0) throw IOException("PNG de segment incomplet")
        val filtered = InflaterInputStream(ByteArrayInputStream(compressed.toByteArray())).use(::readBounded)
        val stride = width * channels
        if (filtered.size.toLong() != (stride.toLong() + 1) * height) throw IOException("Pixels PNG tronqués")
        val rgb = ByteArray(width * height * 3)
        var previous = ByteArray(stride)
        var source = 0; var destination = 0
        repeat(height) {
            val filter = filtered[source++].toInt() and 255
            val row = ByteArray(stride)
            for (i in 0 until stride) {
                val a = if (i >= channels) row[i - channels].toInt() and 255 else 0
                val b = previous[i].toInt() and 255
                val c = if (i >= channels) previous[i - channels].toInt() and 255 else 0
                val predictor = when (filter) {
                    0 -> 0; 1 -> a; 2 -> b; 3 -> (a + b) / 2
                    4 -> { val p = a + b - c; val pa = abs(p - a); val pb = abs(p - b); val pc = abs(p - c)
                        if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c }
                    else -> throw IOException("Filtre PNG invalide")
                }
                row[i] = ((filtered[source++].toInt() and 255) + predictor).toByte()
            }
            for (x in 0 until width) for (channel in 0..2) rgb[destination++] = row[x * channels + channel]
            previous = row
        }
        if (!rgb.starts(pixelTag) || rgb.size < 12) throw IOException("PNG sans signature de transport vidéo")
        val length = rgb.u32(8)
        if (length <= 0 || length > rgb.size - 12) throw IOException("Charge vidéo PNG invalide")
        return GZIPInputStream(ByteArrayInputStream(rgb, 12, length.toInt())).use(::readBounded)
    }
}
