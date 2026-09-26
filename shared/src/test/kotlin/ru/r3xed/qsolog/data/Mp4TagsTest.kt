package ru.r3xed.qsolog.data

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Mp4TagsTest {
    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val body = parts.fold(ByteArray(0)) { a, p -> a + p }
        return ByteBuffer.allocate(8 + body.size).putInt(8 + body.size).put(type.toByteArray(Charsets.ISO_8859_1)).put(body).array()
    }

    private fun stco(vararg offsets: Int) =
        box("stco", ByteBuffer.allocate(8 + 4 * offsets.size).putInt(0).putInt(offsets.size).also { b -> offsets.forEach { b.putInt(it) } }.array())

    private fun moov(stco: ByteArray, extra: ByteArray = ByteArray(0)) =
        box("moov", box("mvhd", ByteArray(20)), box("trak", box("mdia", box("minf", box("stbl", stco)))), extra)

    private val audio = ByteArray(64) { it.toByte() }
    private val ftyp = box("ftyp", "M4A ".toByteArray(), ByteArray(4))

    /** Chunk offsets of the first stco, wherever it is. */
    private fun offsets(b: ByteArray): List<Int> {
        val i = String(b, Charsets.ISO_8859_1).indexOf("stco")
        val n = ByteBuffer.wrap(b, i + 8, 4).int
        return (0 until n).map { ByteBuffer.wrap(b, i + 12 + 4 * it, 4).int }
    }

    @Test
    fun moovAfterAudio_offsetsUntouched() {
        val mdat = box("mdat", audio)
        val first = ftyp.size + 8
        val file = ftyp + mdat + moov(stco(first, first + 32))
        val out = Mp4Tags.withArtist(file)
        assertEquals("Recorded in QSO-LOG", Mp4Tags.artist(out))
        assertEquals(listOf(first, first + 32), offsets(out))
        // Audio bytes are where the offsets say.
        assertEquals(0, out[first].toInt()); assertEquals(32, out[first + 32].toInt())
    }

    @Test
    fun moovBeforeAudio_offsetsShifted() {
        val m = moov(stco(0, 0))
        val first = ftyp.size + m.size + 8
        val file = ftyp + moov(stco(first, first + 32)) + box("mdat", audio)
        val out = Mp4Tags.withArtist(file)
        assertEquals("Recorded in QSO-LOG", Mp4Tags.artist(out))
        val o = offsets(out)
        assertEquals(0, out[o[0]].toInt()); assertEquals(32, out[o[1]].toInt())
        assertTrue(o[0] > first)
    }

    @Test
    fun existingUdtaIsKept() {
        val loc = box("©xyz", "+55.7+037.6/".toByteArray())
        val file = ftyp + box("mdat", audio) + moov(stco(ftyp.size + 8), box("udta", loc))
        val out = Mp4Tags.withArtist(file)
        assertEquals("Recorded in QSO-LOG", Mp4Tags.artist(out))
        assertTrue(String(out, Charsets.ISO_8859_1).contains("+55.7+037.6/"))
        // Tagging twice leaves one tag.
        val twice = Mp4Tags.withArtist(out)
        assertEquals(out.size, twice.size)
    }

    @Test
    fun notAnMp4() {
        val junk = "RIFF....WAVE".toByteArray()
        assertTrue(Mp4Tags.withArtist(junk) === junk)
        assertNull(Mp4Tags.artist(junk))
    }
}
