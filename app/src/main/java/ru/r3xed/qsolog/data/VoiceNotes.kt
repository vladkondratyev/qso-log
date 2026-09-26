package ru.r3xed.qsolog.data

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Voice notes for contacts: AAC in .m4a, mono, 16 kHz, 32 kbit/s.
 * About 240 KB per minute, clear enough for callsigns and reports over a phone speaker.
 */
class VoiceNotes(private val context: Context) {
    val dir: File = File(context.filesDir, "audio").apply { mkdirs() }

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var startedAt = 0L

    val isRecording get() = recorder != null

    fun file(name: String) = File(dir, name)

    /** Starts recording to a new file. Throws if the microphone is unavailable. */
    fun start() {
        stopAndDiscard()
        val name = "qso_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now(ZoneOffset.UTC)) + ".m4a"
        val file = File(dir, name)
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            file.delete()
            throw e
        }
        recorder = r
        recordingFile = file
        startedAt = System.currentTimeMillis()
    }

    /** Stops recording. Returns the file name, or null if the note is too short to be useful. */
    fun stop(): String? {
        val r = recorder ?: return null
        val file = recordingFile
        val duration = System.currentTimeMillis() - startedAt
        recorder = null
        recordingFile = null
        val ok = try {
            r.stop()
            true
        } catch (e: Exception) {
            false // stop() throws when nothing was captured
        } finally {
            r.release()
        }
        if (!ok || duration < MIN_DURATION_MS || file == null) {
            file?.delete()
            return null
        }
        // MediaRecorder cannot set it: the Artist tag ("Recorded in QSO-LOG") is written into the file afterwards.
        Mp4Tags.tagFile(file)
        return file.name
    }

    fun stopAndDiscard() {
        stop()?.let { File(dir, it).delete() }
    }

    fun delete(name: String) {
        if (name.isNotBlank()) File(dir, name).delete()
    }

    /** Deletes recordings that no contact refers to (e.g. a form closed without saving). */
    fun cleanup(keep: Set<String>) {
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir.listFiles()?.forEach { f -> if (f.name !in keep && f.lastModified() < cutoff) f.delete() }
    }

    fun durationMs(name: String): Long = try {
        MediaPlayer().run {
            setDataSource(File(dir, name).absolutePath)
            prepare()
            val d = duration.toLong()
            release()
            d
        }
    } catch (e: Exception) {
        0
    }

    companion object {
        const val MIN_DURATION_MS = 700L
    }
}
