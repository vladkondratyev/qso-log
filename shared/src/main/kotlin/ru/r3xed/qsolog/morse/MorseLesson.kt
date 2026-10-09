package ru.r3xed.qsolog.morse

import ru.r3xed.qsolog.data.MorseTone
import kotlin.random.Random

/** The trainer's settings, kept between starts. */
data class MorseSettings(
    val wpm: Int = 18,
    val type: KeyType = KeyType.IAMBIC_A,
    val cyrillic: Boolean = true,
    /** How many characters of the Koch order are learned (2…all). */
    val level: Int = 2,
    /** Effective speed for receiving: letters at [wpm], longer gaps between them (Farnsworth). */
    val gapWpm: Int = 10,
)

object MorseLesson {
    /** A group of [n] random characters from the first [level] of the Koch order; the newest one is always in it. */
    fun group(cyrillic: Boolean, level: Int, n: Int = 5, rnd: Random = Random.Default): String {
        val set = MorseCode.koch(cyrillic).take(level.coerceIn(2, MorseCode.koch(cyrillic).length))
        val chars = MutableList(n) { set[rnd.nextInt(set.length)] }
        chars[rnd.nextInt(n)] = set.last()
        return chars.joinToString("")
    }

    /**
     * Typed text in the trainer's alphabet: a letter with the same signal counts the same (K typed for К), so the
     * keyboard layout does not matter.
     */
    fun normalize(typed: String, cyrillic: Boolean): String = typed.uppercase().map { c ->
        MorseCode.code(c)?.let { MorseCode.char(it, cyrillic) } ?: c
    }.joinToString("")

    /** Right characters by position (spaces ignored) and the characters missed or mistaken. */
    fun check(expected: String, answer: String, cyrillic: Boolean? = null): Pair<Int, List<Char>> {
        val e = expected.filter { !it.isWhitespace() }.uppercase()
        val a = answer.filter { !it.isWhitespace() }.let { if (cyrillic != null) normalize(it, cyrillic) else it.uppercase() }
        var right = 0
        val wrong = mutableListOf<Char>()
        e.forEachIndexed { i, c -> if (a.getOrNull(i) == c) right++ else wrong += c }
        return right to wrong
    }

    /**
     * The sound of [text]: letters at [wpm], the gaps between letters and words stretched to [gapWpm] (Farnsworth),
     * so a beginner hears every letter at its real speed.
     */
    fun pcm(text: String, wpm: Int, gapWpm: Int): ShortArray {
        val dot = MorseTone.RATE * 1.2 / wpm
        // Farnsworth: the extra time of a slower word, spread over the 19 gap units of PARIS.
        val slow = if (gapWpm in 1 until wpm) (60.0 / gapWpm - 31 * 1.2 / wpm) / 19 * MorseTone.RATE else dot
        val parts = mutableListOf<ShortArray>()
        text.uppercase().forEach { c ->
            if (c == ' ') parts += ShortArray((7 * slow).toInt())
            else MorseCode.code(c)?.let { code ->
                parts += MorseTone.pcm(code, wpm, tail = false)
                parts += ShortArray((3 * slow).toInt())
            }
        }
        val out = ShortArray(parts.sumOf { it.size })
        var i = 0
        parts.forEach { it.copyInto(out, i); i += it.size }
        return out
    }
}

/** Where the platform keeps the trainer's settings (Android preferences, the computer's user preferences). */
interface MorseStore {
    fun get(key: String): String?
    fun put(key: String, value: String)

    fun load(): MorseSettings {
        val d = MorseSettings(cyrillic = ru.r3xed.qsolog.I18n.current != ru.r3xed.qsolog.Lang.EN)
        return MorseSettings(
            wpm = get("wpm")?.toIntOrNull()?.coerceIn(5, 40) ?: d.wpm,
            type = get("type")?.let { v -> KeyType.entries.firstOrNull { it.name == v } } ?: d.type,
            cyrillic = get("cyrillic")?.let { it == "1" } ?: d.cyrillic,
            level = get("level")?.toIntOrNull()?.coerceAtLeast(2) ?: d.level,
            gapWpm = get("gap_wpm")?.toIntOrNull()?.coerceIn(3, 40) ?: d.gapWpm,
        )
    }

    fun save(s: MorseSettings) {
        put("wpm", "${s.wpm}"); put("type", s.type.name); put("cyrillic", if (s.cyrillic) "1" else "0")
        put("level", "${s.level}"); put("gap_wpm", "${s.gapWpm}")
    }
}
