package fr.nekotv

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class WrappedTsTest {
    private val ts = ByteArray(188 * 5) { (it * 17).toByte() }.apply { for (i in indices step 188) this[i] = 0x47 }
    private fun gzip(bytes: ByteArray) = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
    private fun int(value: Int, little: Boolean = false) = ByteArray(4) { i -> (value ushr ((if (little) i else 3 - i) * 8)).toByte() }
    private fun chunk(name: String, bytes: ByteArray): ByteArray {
        val data = name.toByteArray() + bytes
        return int(bytes.size) + data + int(CRC32().apply { update(data) }.value.toInt())
    }
    private fun pngPayload(filter: Int, alpha: Boolean = false): ByteArray {
        val gz = gzip(ts)
        val payload = "TIKTIKPX".toByteArray() + int(gz.size) + gz
        val width = 16; val height = (payload.size + width * 3 - 1) / (width * 3)
        val channels = if (alpha) 4 else 3
        val rgb = payload.copyOf(width * height * 3)
        val scanlines = ByteArrayOutputStream()
        var previous = ByteArray(width * channels)
        repeat(height) { y ->
            val row = ByteArray(width * channels) { x -> if (x % channels == 3) 255.toByte() else rgb[y * width * 3 + x / channels * 3 + x % channels] }
            scanlines.write(filter)
            for (i in row.indices) {
                val a = if (i >= channels) row[i - channels].toInt() and 255 else 0
                val b = previous[i].toInt() and 255
                val c = if (i >= channels) previous[i - channels].toInt() and 255 else 0
                val predictor = when (filter) {
                    0 -> 0; 1 -> a; 2 -> b; 3 -> (a + b) / 2
                    else -> { val p = a + b - c; val ds = listOf(kotlin.math.abs(p - a), kotlin.math.abs(p - b), kotlin.math.abs(p - c)); listOf(a, b, c)[ds.indexOf(ds.minOrNull())] }
                }
                scanlines.write((row[i].toInt() and 255) - predictor)
            }
            previous = row
        }
        val compressed = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(scanlines.toByteArray()) } }.toByteArray()
        return byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10) +
            chunk("IHDR", int(width) + int(height) + byteArrayOf(8, if (alpha) 6 else 2, 0, 0, 0)) +
            chunk("IDAT", compressed) + chunk("IEND", byteArrayOf())
    }
    @Test fun pngTransportHandlesEveryPublishedFilterAndAlphaWithoutChangingPackets() {
        for (filter in 0..4) for (alpha in listOf(false, true)) assertArrayEquals(ts, WrappedTs.decode(pngPayload(filter, alpha)))
    }
    @Test fun webpExifAndRawGzipEnvelopesPreserveTsPackets() {
        val exif = "EXIF".toByteArray() + int(ts.size, little = true) + ts
        val webp = "RIFF".toByteArray() + int(exif.size + 4, little = true) + "WEBP".toByteArray() + exif
        assertArrayEquals(ts, WrappedTs.decode(webp))
        assertArrayEquals(ts, WrappedTs.decode("TIKTIKRAW".toByteArray() + ts))
        assertArrayEquals(ts, WrappedTs.decode("TIKTIKTSGZ".toByteArray() + gzip(ts)))
    }
    @Test fun ordinaryMediaIsUntouchedAndBrokenEnvelopesAreRejected() {
        assertNull(WrappedTs.decode(ts))
        assertNull(WrappedTs.decode("<html>captcha</html>".toByteArray()))
        assertNull(WrappedTs.decode(byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray()))
        assertTrue(runCatching { WrappedTs.decode(pngPayload(4).dropLast(10).toByteArray()) }.isFailure)
        assertTrue(runCatching { WrappedTs.decode("TIKTIKRAWbad packets".toByteArray()) }.isFailure)
    }
}
