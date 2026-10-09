package ru.r3xed.qsolog.data

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The sound of a Morse signal as 16-bit mono PCM, for the reference: a sine tone, dash = three dots, one dot of silence
 * between elements. Each element fades in and out over a few milliseconds, so the tone does not click.
 */
object MorseTone {
    const val RATE = 44_100

    /** [code] of dots and dashes (".-"); [wpm] words per minute (PARIS), [hz] the tone. */
    fun pcm(code: String, wpm: Int = 18, hz: Double = 700.0, volume: Double = 0.6): ShortArray {
        val dot = RATE * 1.2 / wpm // samples per dot: 1200 ms / WPM
        val ramp = (RATE * 0.005).toInt() // 5 ms
        val out = ArrayList<Short>()
        fun tone(len: Int) {
            for (i in 0 until len) {
                val env = min(1.0, min(i, len - 1 - i).toDouble() / ramp)
                out += (sin(2 * PI * hz * i / RATE) * env * volume * Short.MAX_VALUE).toInt().toShort()
            }
        }
        fun silence(len: Int) = repeat(len) { out += 0 }
        code.filter { it == '.' || it == '-' }.forEachIndexed { i, c ->
            if (i > 0) silence(dot.toInt())
            tone((if (c == '-') 3 * dot else dot).toInt())
        }
        if (out.isNotEmpty()) silence((dot * 2).toInt()) // a little tail, so the end is not cut by the device
        return out.toShortArray()
    }

    /** Little-endian bytes of [pcm], as sound APIs want them. */
    fun bytes(pcm: ShortArray): ByteArray {
        val b = ByteArray(pcm.size * 2)
        pcm.forEachIndexed { i, v -> b[2 * i] = (v.toInt() and 0xFF).toByte(); b[2 * i + 1] = (v.toInt() shr 8).toByte() }
        return b
    }
}
