package ru.r3xed.qsolog.data

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Writes the "Artist" tag (iTunes-style moov/udta/meta/ilst/©ART) into an MP4/M4A file.
 * Android's MediaRecorder has no API for it, so the finished voice note is rewritten once after recording.
 * Only the moov box changes; if it lies before the audio data, the chunk offsets (stco/co64) are shifted to match.
 */
object Mp4Tags {
    const val ARTIST = "Recorded in QSO-LOG"

    private class Box(val type: String, val offset: Int, val size: Int, val header: Int) {
        val end get() = offset + size
        val body get() = offset + header
    }

    private fun int(b: ByteArray, i: Int) =
        ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

    private fun long(b: ByteArray, i: Int) = (int(b, i).toLong() and 0xFFFFFFFFL shl 32) or (int(b, i + 4).toLong() and 0xFFFFFFFFL)

    private fun putInt(b: ByteArray, i: Int, v: Int) {
        b[i] = (v ushr 24).toByte(); b[i + 1] = (v ushr 16).toByte(); b[i + 2] = (v ushr 8).toByte(); b[i + 3] = v.toByte()
    }

    private fun putLong(b: ByteArray, i: Int, v: Long) {
        putInt(b, i, (v ushr 32).toInt()); putInt(b, i + 4, v.toInt())
    }

    private fun boxes(b: ByteArray, start: Int, end: Int): List<Box> {
        val out = mutableListOf<Box>()
        var i = start
        while (i + 8 <= end) {
            var size = int(b, i).toLong() and 0xFFFFFFFFL
            val type = String(b, i + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) { size = long(b, i + 8); header = 16 }
            if (size == 0L) size = (end - i).toLong()
            if (size < header || i + size > end) break
            out += Box(type, i, size.toInt(), header)
            i += size.toInt()
        }
        return out
    }

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val body = parts.fold(ByteArray(0)) { acc, p -> acc + p }
        val out = ByteArray(8 + body.size)
        putInt(out, 0, out.size)
        type.toByteArray(Charsets.ISO_8859_1).copyInto(out, 4)
        body.copyInto(out, 8)
        return out
    }

    /** udta/meta with an ilst holding only ©ART. */
    private fun metaBox(artist: String): ByteArray {
        val zero4 = ByteArray(4)
        val hdlr = box("hdlr", zero4, zero4, "mdir".toByteArray(), "appl".toByteArray(), ByteArray(8), ByteArray(1))
        val data = box("data", byteArrayOf(0, 0, 0, 1), zero4, artist.toByteArray(Charsets.UTF_8))
        val ilst = box("ilst", box("©ART", data))
        return box("meta", zero4, hdlr, ilst)
    }

    /** The file with the artist tag; the input unchanged when it is not an MP4 it understands. */
    fun withArtist(b: ByteArray, artist: String = ARTIST): ByteArray {
        val top = boxes(b, 0, b.size)
        val moov = top.firstOrNull { it.type == "moov" } ?: return b
        val mdat = top.firstOrNull { it.type == "mdat" }
        val children = boxes(b, moov.body, moov.end)
        val body = ByteArrayOutputStream()
        for (c in children) if (c.type != "udta") body.write(b, c.offset, c.size)
        // Keep what an existing udta holds (e.g. the location), replacing only its meta.
        val oldUdta = children.firstOrNull { it.type == "udta" }
        val udtaBody = ByteArrayOutputStream()
        if (oldUdta != null) for (c in boxes(b, oldUdta.body, oldUdta.end)) if (c.type != "meta") udtaBody.write(b, c.offset, c.size)
        udtaBody.write(metaBox(artist))
        body.write(box("udta", udtaBody.toByteArray()))
        val newMoov = box("moov", body.toByteArray())
        val delta = newMoov.size - moov.size
        if (mdat != null && moov.offset < mdat.offset && delta != 0) shiftChunkOffsets(newMoov, 8, newMoov.size, delta)
        return b.copyOfRange(0, moov.offset) + newMoov + b.copyOfRange(moov.end, b.size)
    }

    private val CONTAINERS = setOf("trak", "mdia", "minf", "stbl", "edts", "dinf")

    /** Audio data moved by [delta] bytes: every chunk offset in stco/co64 moves with it. */
    private fun shiftChunkOffsets(b: ByteArray, start: Int, end: Int, delta: Int) {
        for (c in boxes(b, start, end)) when (c.type) {
            in CONTAINERS -> shiftChunkOffsets(b, c.body, c.end, delta)
            "stco" -> {
                val n = int(b, c.body + 4)
                for (k in 0 until n) { val p = c.body + 8 + 4 * k; putInt(b, p, int(b, p) + delta) }
            }
            "co64" -> {
                val n = int(b, c.body + 4)
                for (k in 0 until n) { val p = c.body + 8 + 8 * k; putLong(b, p, long(b, p) + delta) }
            }
        }
    }

    /** Reads ©ART back (for tests and checks); null if absent. */
    fun artist(b: ByteArray): String? {
        fun find(start: Int, end: Int, path: List<String>): Box? {
            val bx = boxes(b, start, end).firstOrNull { it.type == path[0] } ?: return null
            if (path.size == 1) return bx
            // meta is a full box: 4 bytes of version/flags before its children.
            val inner = if (bx.type == "meta") bx.body + 4 else bx.body
            return find(inner, bx.end, path.drop(1))
        }
        val data = find(0, b.size, listOf("moov", "udta", "meta", "ilst", "©ART", "data")) ?: return null
        return String(b, data.body + 8, data.end - data.body - 8, Charsets.UTF_8)
    }

    /** Tags the file in place; leaves it untouched if anything goes wrong. */
    fun tagFile(file: File, artist: String = ARTIST) {
        try {
            val src = file.readBytes()
            val out = withArtist(src, artist)
            if (out === src) return
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(out)
            if (!tmp.renameTo(file)) { file.writeBytes(out); tmp.delete() }
        } catch (_: Exception) {
        }
    }
}
