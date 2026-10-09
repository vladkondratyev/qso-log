package ru.r3xed.qsolog.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import ru.r3xed.qsolog.data.MorseTone

/** Plays a Morse signal of the reference; a new tap stops the one still sounding. */
object MorseSound {
    private var track: AudioTrack? = null
    private val main = Handler(Looper.getMainLooper())
    private var done: Runnable? = null

    fun play(code: String, onDone: () -> Unit = {}) {
        stop()
        val pcm = MorseTone.pcm(code)
        if (pcm.isEmpty()) return
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(MorseTone.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        } catch (e: Exception) {
            return
        }
        t.write(pcm, 0, pcm.size)
        t.play()
        track = t
        val r = Runnable { onDone(); if (track === t) stop() }
        done = r
        main.postDelayed(r, pcm.size * 1000L / MorseTone.RATE)
    }

    private fun stop() {
        done?.let(main::removeCallbacks)
        done = null
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}
