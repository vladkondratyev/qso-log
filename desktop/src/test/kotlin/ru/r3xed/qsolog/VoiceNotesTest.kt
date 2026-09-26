package ru.r3xed.qsolog

import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceNotesTest {
    @Test
    fun wavIsMuLawAndKeepsDuration() {
        // Two seconds of a 440 Hz tone, 16 kHz 16-bit mono little-endian.
        val samples = 32_000
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val v = (sin(2 * PI * 440 * i / 16_000) * 12_000).toInt()
            pcm[2 * i] = (v and 0xFF).toByte()
            pcm[2 * i + 1] = (v shr 8).toByte()
        }
        val file = File.createTempFile("note", ".wav").apply { deleteOnExit() }
        VoiceNotes.writeWav(pcm, file)

        AudioSystem.getAudioInputStream(file).use {
            assertEquals(AudioFormat.Encoding.ULAW, it.format.encoding)
            assertEquals(16_000f, it.format.sampleRate)
        }
        assertEquals(2000L, VoiceNotes.durationMs(file))
        // µ-law: one byte per sample, about 1 MB per minute.
        assertTrue(file.length() in 32_000L..32_200L)

        // Artist tag after the audio: the RIFF size covers it, a second tagging does not add another.
        val bytes = file.readBytes()
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("IARTRecorded in QSO-LOG".replace("IART", "IART\u0014\u0000\u0000\u0000")))
        val riff = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8) or ((bytes[6].toInt() and 0xFF) shl 16) or ((bytes[7].toInt() and 0xFF) shl 24)
        assertEquals(bytes.size - 8, riff)
        VoiceNotes.tagWav(file)
        assertEquals(bytes.size.toLong(), file.length())
    }
}
