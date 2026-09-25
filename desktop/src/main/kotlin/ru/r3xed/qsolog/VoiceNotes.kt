package ru.r3xed.qsolog

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

/**
 * Voice notes for contacts: WAV, mono, 16 kHz, µ-law (8 bits per sample).
 * About 1 MB per minute. Java has no AAC encoder, so the files are larger than on Android, but play everywhere.
 */
class VoiceNotes(val dir: File = AppDirs.audio) {
    private val pcm = PCM

    private var line: TargetDataLine? = null
    private var buffer: ByteArrayOutputStream? = null
    private var reader: Thread? = null
    private var startedAt = 0L

    val isRecording get() = line != null

    fun file(name: String) = File(dir, name)

    /** Starts recording from the default microphone. Throws if there is none or access is denied. */
    fun start() {
        stopAndDiscard()
        val l = AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, pcm)) as TargetDataLine
        l.open(pcm)
        l.start()
        val out = ByteArrayOutputStream()
        line = l
        buffer = out
        startedAt = System.currentTimeMillis()
        reader = thread(name = "voice-note", isDaemon = true) {
            val chunk = ByteArray(3200) // 100 ms
            while (l.isOpen) {
                val n = l.read(chunk, 0, chunk.size)
                if (n <= 0) break
                synchronized(out) { out.write(chunk, 0, n) }
            }
        }
    }

    /** Stops recording. Returns the file name, or null if the note is too short to be useful. */
    fun stop(): String? {
        val l = line ?: return null
        val out = buffer
        val duration = System.currentTimeMillis() - startedAt
        line = null
        buffer = null
        l.stop()
        l.close()
        reader?.join(500)
        reader = null
        val bytes = out?.let { synchronized(it) { it.toByteArray() } } ?: return null
        if (duration < MIN_DURATION_MS || bytes.size < pcm.frameSize * 1600) return null
        val name = "qso_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now(ZoneOffset.UTC)) + ".wav"
        writeWav(bytes, File(dir, name))
        return name
    }

    fun stopAndDiscard() {
        stop()?.let { File(dir, it).delete() }
    }

    fun delete(name: String) {
        if (name.isNotBlank()) File(dir, name).delete()
    }

    /** Deletes recordings that no contact refers to (e.g. a card closed without saving). */
    fun cleanup(keep: Set<String>) {
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir.listFiles()?.forEach { f -> if (f.name !in keep && f.lastModified() < cutoff) f.delete() }
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val MIN_DURATION_MS = 700L
        private val PCM = AudioFormat(16_000f, 16, 1, true, false)

        /** 16 kHz 16-bit mono PCM (little-endian) → µ-law WAV. */
        fun writeWav(pcmBytes: ByteArray, file: File) {
            val source = AudioInputStream(ByteArrayInputStream(pcmBytes), PCM, (pcmBytes.size / PCM.frameSize).toLong())
            val ulaw = AudioSystem.getAudioInputStream(AudioFormat.Encoding.ULAW, source)
            AudioSystem.write(ulaw, AudioFileFormat.Type.WAVE, file)
        }

        fun durationMs(file: File): Long = try {
            AudioSystem.getAudioInputStream(file).use { (it.frameLength * 1000 / it.format.frameRate).toLong() }
        } catch (e: Exception) {
            0
        }

        /** Opens the file for playback; the caller starts, polls and closes the clip. */
        fun openClip(file: File): Clip? = try {
            AudioSystem.getAudioInputStream(file).use { input ->
                // Clips play PCM; decode µ-law (and anything else) first.
                val decoded = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, input)
                AudioSystem.getClip().apply { open(decoded) }
            }
        } catch (e: Exception) {
            null
        }
    }
}
