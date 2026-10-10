package ru.r3xed.qsolog.ui

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.swing.SwingUtilities
import ru.r3xed.qsolog.data.MorseTone

/** Plays a Morse signal of the reference; a new click stops the one still sounding. */
object MorseSound {
    private var clip: Clip? = null

    fun play(code: String, onDone: () -> Unit = {}) = playPcm(MorseTone.pcm(code), onDone)

    /** Plays ready sound: a whole group of the receiving trainer. */
    fun playPcm(pcm: ShortArray, onDone: () -> Unit = {}) {
        clip?.let { it.stop(); it.close() }
        clip = null
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

/**
 * The trainer's sidetone: a stream that sounds while the key (or the keyer) is down. The sound thread asks the session
 * every 128 samples (~3 ms) whether the tone is on, so the keyer's timing follows the audio clock.
 */
class MorseSidetone(private val session: ru.r3xed.qsolog.morse.MorseKeySession, private val hz: Double = 700.0) {
    @Volatile private var running = false

    fun start() {
        running = true
        kotlin.concurrent.thread(name = "morse-sidetone", isDaemon = true, priority = Thread.MAX_PRIORITY) { loop() }
    }

    fun stop() { running = false }

    private fun loop() {
        val rate = MorseTone.RATE
        val frames = 128
        val line = try {
            AudioSystem.getSourceDataLine(AudioFormat(rate.toFloat(), 16, 1, true, false)).apply { open(format, frames * 2 * 8); start() }
        } catch (e: Exception) {
            // No sound device: keying and decoding still work, silently.
            while (running) { session.toneAt(System.nanoTime() / 1_000_000); Thread.sleep(3) }
            return
        }
        val buf = ShortArray(frames)
        var phase = 0.0
        var env = 0.0
        val step = 1.0 / (rate * 0.005)
        val dPhase = 2 * Math.PI * hz / rate
        while (running) {
            val on = session.toneAt(System.nanoTime() / 1_000_000)
            for (i in 0 until frames) {
                env = if (on) minOf(1.0, env + step) else maxOf(0.0, env - step)
                buf[i] = (kotlin.math.sin(phase) * env * 0.5 * Short.MAX_VALUE).toInt().toShort()
                phase += dPhase
                if (phase > 2 * Math.PI) phase -= 2 * Math.PI
            }
            val bytes = MorseTone.bytes(buf)
            line.write(bytes, 0, bytes.size) // blocks: the loop runs at the speed of the sound
        }
        line.stop()
        line.close()
    }
}

/** The trainer's settings in the user's preferences of this computer. */
@androidx.compose.runtime.Composable
fun rememberMorseStore(): ru.r3xed.qsolog.morse.MorseStore = androidx.compose.runtime.remember {
    val p = java.util.prefs.Preferences.userRoot().node("ru/r3xed/qsolog/morse")
    object : ru.r3xed.qsolog.morse.MorseStore {
        override fun get(key: String): String? = p.get(key, null)
        override fun put(key: String, value: String) = p.put(key, value)
    }
}

/** A computer has no headset buttons for a key. */
const val MORSE_HEADSET_KEYS = false

/** The keys an adapter usually sends: Ctrl / brackets for the paddles, Space for a straight key. */
fun defaultMorseKeyMap() = ru.r3xed.qsolog.morse.MorseKeyMap(
    dot = setOf(androidx.compose.ui.input.key.Key.CtrlLeft, androidx.compose.ui.input.key.Key.LeftBracket).map { "${it.keyCode}" }.toSet(),
    dah = setOf(androidx.compose.ui.input.key.Key.CtrlRight, androidx.compose.ui.input.key.Key.RightBracket).map { "${it.keyCode}" }.toSet(),
    straight = setOf(androidx.compose.ui.input.key.Key.Spacebar).map { "${it.keyCode}" }.toSet(),
)

/** A key id as people call it. */
fun morseKeyLabel(id: String): String {
    val k = androidx.compose.ui.input.key.Key
    return when (id) {
        "${k.CtrlLeft.keyCode}" -> ru.r3xed.qsolog.tr("левый Ctrl")
        "${k.CtrlRight.keyCode}" -> ru.r3xed.qsolog.tr("правый Ctrl")
        "${k.LeftBracket.keyCode}" -> "["
        "${k.RightBracket.keyCode}" -> "]"
        "${k.Spacebar.keyCode}" -> ru.r3xed.qsolog.tr("Пробел")
        else -> id.toLongOrNull()?.let { java.awt.event.KeyEvent.getKeyText((it and 0xFFFFFFFFL).toInt()) } ?: id
    }
}

/** The computer does not list keyboards; the check below shows what arrives. */
fun connectedKeyDevices(): List<String> = emptyList()
const val MORSE_DEVICE_LIST = false
