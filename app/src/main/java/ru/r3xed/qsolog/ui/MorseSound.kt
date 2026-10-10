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

    fun play(code: String, onDone: () -> Unit = {}) = playPcm(MorseTone.pcm(code), onDone)

    /** Plays ready sound: a whole group of the receiving trainer. */
    fun playPcm(pcm: ShortArray, onDone: () -> Unit = {}) {
        stop()
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

/**
 * The trainer's sidetone: a stream that sounds while the key (or the keyer) is down. The sound thread asks the session
 * every 128 samples (~3 ms) whether the tone is on, so the keyer's timing follows the audio clock.
 */
class MorseSidetone(private val session: ru.r3xed.qsolog.morse.MorseKeySession, private val hz: Double = 700.0) {
    @Volatile private var running = false

    fun start() {
        running = true
        kotlin.concurrent.thread(name = "morse-sidetone", priority = Thread.MAX_PRIORITY) { loop() }
    }

    fun stop() { running = false }

    private fun loop() {
        val rate = MorseTone.RATE
        val frames = 128
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(min)
                .build()
        } catch (e: Exception) {
            return
        }
        track.play()
        val buf = ShortArray(frames)
        var phase = 0.0
        var env = 0.0
        val step = 1.0 / (rate * 0.005) // 5 ms fade, no clicks
        val dPhase = 2 * Math.PI * hz / rate
        while (running) {
            val on = session.toneAt(System.nanoTime() / 1_000_000)
            for (i in 0 until frames) {
                env = if (on) minOf(1.0, env + step) else maxOf(0.0, env - step)
                buf[i] = (kotlin.math.sin(phase) * env * 0.5 * Short.MAX_VALUE).toInt().toShort()
                phase += dPhase
                if (phase > 2 * Math.PI) phase -= 2 * Math.PI
            }
            track.write(buf, 0, frames) // blocks: the loop runs at the speed of the sound
        }
        runCatching { track.stop() }
        track.release()
    }
}

/** The trainer's settings in the app's preferences. */
@androidx.compose.runtime.Composable
fun rememberMorseStore(): ru.r3xed.qsolog.morse.MorseStore {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember {
        val p = ctx.getSharedPreferences("morse_trainer", android.content.Context.MODE_PRIVATE)
        object : ru.r3xed.qsolog.morse.MorseStore {
            override fun get(key: String) = p.getString(key, null)
            override fun put(key: String, value: String) { p.edit().putString(key, value).apply() }
        }
    }
}

/** On a phone a key can also be wired as a headset button. */
const val MORSE_HEADSET_KEYS = true

/** The keys an adapter usually sends: Ctrl / brackets for the paddles, Space or a headset button for a straight key. */
fun defaultMorseKeyMap() = ru.r3xed.qsolog.morse.MorseKeyMap(
    dot = setOf(android.view.KeyEvent.KEYCODE_CTRL_LEFT, android.view.KeyEvent.KEYCODE_LEFT_BRACKET, android.view.KeyEvent.KEYCODE_VOLUME_UP).map { "$it" }.toSet(),
    dah = setOf(android.view.KeyEvent.KEYCODE_CTRL_RIGHT, android.view.KeyEvent.KEYCODE_RIGHT_BRACKET, android.view.KeyEvent.KEYCODE_VOLUME_DOWN).map { "$it" }.toSet(),
    straight = setOf(android.view.KeyEvent.KEYCODE_SPACE, android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE).map { "$it" }.toSet(),
)

/** A key id as people call it. */
fun morseKeyLabel(id: String): String {
    val code = id.toIntOrNull() ?: return id
    return when (code) {
        android.view.KeyEvent.KEYCODE_CTRL_LEFT -> ru.r3xed.qsolog.tr("левый Ctrl")
        android.view.KeyEvent.KEYCODE_CTRL_RIGHT -> ru.r3xed.qsolog.tr("правый Ctrl")
        android.view.KeyEvent.KEYCODE_LEFT_BRACKET -> "["
        android.view.KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
        android.view.KeyEvent.KEYCODE_VOLUME_UP -> ru.r3xed.qsolog.tr("громкость +")
        android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> ru.r3xed.qsolog.tr("громкость −")
        android.view.KeyEvent.KEYCODE_SPACE -> ru.r3xed.qsolog.tr("Пробел")
        android.view.KeyEvent.KEYCODE_HEADSETHOOK -> ru.r3xed.qsolog.tr("кнопка гарнитуры")
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> ru.r3xed.qsolog.tr("кнопка гарнитуры (play/pause)")
        else -> android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }
}

/** External keyboards (adapters) the phone sees now, by name. */
fun connectedKeyDevices(): List<String> = android.view.InputDevice.getDeviceIds().toList()
    .mapNotNull { android.view.InputDevice.getDevice(it) }
    .filter { d ->
        !d.isVirtual && (d.sources and android.view.InputDevice.SOURCE_KEYBOARD) == android.view.InputDevice.SOURCE_KEYBOARD &&
            (if (android.os.Build.VERSION.SDK_INT >= 29) d.isExternal else d.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC)
    }
    .map { it.name }.distinct()
const val MORSE_DEVICE_LIST = true
