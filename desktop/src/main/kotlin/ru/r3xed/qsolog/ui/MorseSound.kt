package ru.r3xed.qsolog.ui

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.swing.SwingUtilities
import ru.r3xed.qsolog.data.MorseTone

/** Plays a Morse signal of the reference; a new click stops the one still sounding. */
object MorseSound {
    private var clip: Clip? = null

    fun play(code: String, onDone: () -> Unit = {}) {
        clip?.let { it.stop(); it.close() }
        clip = null
        val pcm = MorseTone.pcm(code)
        if (pcm.isEmpty()) return
        val c = try {
            AudioSystem.getClip().apply {
                open(AudioFormat(MorseTone.RATE.toFloat(), 16, 1, true, false), MorseTone.bytes(pcm), 0, pcm.size * 2)
            }
        } catch (e: Exception) {
            return // no sound device: the table still shows the signal
        }
        clip = c
        c.addLineListener { e ->
            if (e.type == javax.sound.sampled.LineEvent.Type.STOP) SwingUtilities.invokeLater {
                onDone()
                if (clip === c) { c.close(); clip = null }
            }
        }
        c.start()
    }
}
