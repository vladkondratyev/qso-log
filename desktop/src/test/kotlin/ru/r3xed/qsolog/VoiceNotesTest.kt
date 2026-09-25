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
    }
}
