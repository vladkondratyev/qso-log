package ru.r3xed.qsolog.morse

/**
 * Turns the tone going on and off into text: a mark longer than two dots is a dash, a silence longer than two dots
 * ends the letter, longer than five the word. [adaptive] (a straight key): the dot length follows the operator's
 * own speed; with the keyer the timing is exact and stays at [wpm].
 */
class MorseDecoder(wpm: Int, private val cyrillic: Boolean, private val adaptive: Boolean) {
    var dotMs = 1200.0 / wpm
        private set
    private var downAt = -1L
    private var upAt = -1L
    private val code = StringBuilder()
    private var spaced = true
    private val out = StringBuilder()

    /** What has been decoded so far ("?" for a signal that is no character). */
    val text: String get() = out.toString()
    /** The dots and dashes of the letter being keyed now. */
    val pending: String get() = code.toString()
    /** The operator's speed as heard (words per minute). */
    val wpm: Int get() = (1200 / dotMs).toInt()

    fun down(t: Long) {
        settle(t)
        downAt = t
    }

    fun up(t: Long) {
        if (downAt < 0) return
        val d = (t - downAt).toDouble()
        val dash = d > 2 * dotMs
        code.append(if (dash) '-' else '.')
        if (adaptive) dotMs = (0.75 * dotMs + 0.25 * (if (dash) d / 3 else d)).coerceIn(25.0, 400.0)
        downAt = -1
        upAt = t
    }

    /** Called now and then: the silence since the last element ends the letter and the word. True if the text changed. */
    fun settle(t: Long): Boolean {
        if (downAt >= 0 || upAt < 0) return false
        val gap = t - upAt
        var changed = false
        if (code.isNotEmpty() && gap > 2 * dotMs) {
            out.append(MorseCode.char(code.toString(), cyrillic) ?: '?')
            code.clear()
            spaced = false
            changed = true
        }
        if (!spaced && gap > 5 * dotMs) {
            out.append(' ')
            spaced = true
            changed = true
        }
        return changed
    }

    fun clear() {
        out.clear()
        code.clear()
        downAt = -1
        upAt = -1
        spaced = true
    }
}

/**
 * An electronic iambic keyer: while a paddle is held it sends dots or dashes; with both squeezed they alternate.
 * Mode A stops when the paddles are released; mode B remembers a paddle touched during an element and sends one
 * more of it. [toneAt] is called every few milliseconds with the paddles' state and says whether the tone sounds.
 */
class IambicKeyer(wpm: Int, private val modeB: Boolean) {
    val dotMs = 1200.0 / wpm
    private var element = 0 // 0 idle, 1 dot, 3 dash
    private var toneEnd = 0.0
    private var gapEnd = 0.0
    private var ditMem = false
    private var dahMem = false

    fun toneAt(t: Long, dit: Boolean, dah: Boolean): Boolean {
        val now = t.toDouble()
        if (element != 0) {
            // A paddle touched while the other element sounds is remembered (mode B), or counted only if still held (A).
            if (element == 1 && dah) dahMem = true
            if (element == 3 && dit) ditMem = true
            if (now < toneEnd) return true
            if (now < gapEnd) return false
            val last = element
            element = 0
            val next = when {
                last == 1 && (dah || (modeB && dahMem)) -> 3
                last == 3 && (dit || (modeB && ditMem)) -> 1
                dit -> 1
                dah -> 3
                else -> 0
            }
            ditMem = false
            dahMem = false
            if (next != 0) start(next, now)
            return element != 0 && now < toneEnd
        }
        if (dit) start(1, now) else if (dah) start(3, now)
        return element != 0
    }

    private fun start(e: Int, now: Double) {
        element = e
        toneEnd = now + e * dotMs
        gapEnd = toneEnd + dotMs
    }
}

/** Kinds of key the trainer works with. */
enum class KeyType { STRAIGHT, IAMBIC_A, IAMBIC_B }

/**
 * One keying session of the trainer: the key's state (from the screen or an external key), the keyer for paddles,
 * and the decoder. [toneAt] is asked by the sound thread every few milliseconds; the screen reads [decoder] under
 * the session's lock.
 */
class MorseKeySession(val wpm: Int, val type: KeyType, cyrillic: Boolean) {
    @Volatile var straight = false
    @Volatile var dit = false
    @Volatile var dah = false
    private val keyer = if (type == KeyType.STRAIGHT) null else IambicKeyer(wpm, type == KeyType.IAMBIC_B)
    val decoder = MorseDecoder(wpm, cyrillic, adaptive = type == KeyType.STRAIGHT)
    private var tone = false

    @Synchronized
    fun toneAt(t: Long): Boolean {
        val on = keyer?.toneAt(t, dit, dah) ?: straight
        if (on != tone) {
            if (on) decoder.down(t) else decoder.up(t)
            tone = on
        }
        return on
    }

    @Synchronized
    fun <T> read(t: Long, block: (MorseDecoder) -> T): T {
        decoder.settle(t)
        return block(decoder)
    }

    @Synchronized
    fun clear() = decoder.clear()

    /** One input of an external key or the screen: [which] 0 straight, 1 dot paddle, 2 dash paddle. */
    fun press(which: Int, down: Boolean) {
        when (which) {
            0 -> if (type == KeyType.STRAIGHT) straight = down else dit = down
            1 -> if (type == KeyType.STRAIGHT) straight = down else dit = down
            2 -> if (type == KeyType.STRAIGHT) straight = down else dah = down
        }
    }
}

/**
 * Which keys of an external key's adapter mean what: platform key ids (Android key codes, the computer's key codes)
 * for the dot paddle, the dash paddle and a straight key. A key belongs to one role at a time.
 */
data class MorseKeyMap(val dot: Set<String>, val dah: Set<String>, val straight: Set<String>) {
    /** 1 dot, 2 dash, 0 straight, -1 not assigned. */
    fun role(id: String): Int = when (id) {
        in dot -> 1
        in dah -> 2
        in straight -> 0
        else -> -1
    }

    /** [id] now means [role] (and nothing else). */
    fun assign(id: String, role: Int): MorseKeyMap = MorseKeyMap(
        dot = (dot - id).let { if (role == 1) it + id else it },
        dah = (dah - id).let { if (role == 2) it + id else it },
        straight = (straight - id).let { if (role == 0) it + id else it },
    )

    fun save(store: MorseStore) {
        store.put("keys_dot", dot.joinToString(","))
        store.put("keys_dah", dah.joinToString(","))
        store.put("keys_straight", straight.joinToString(","))
    }

    companion object {
        fun load(store: MorseStore, default: MorseKeyMap): MorseKeyMap {
            fun set(k: String, d: Set<String>) = store.get(k)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: d
            return MorseKeyMap(set("keys_dot", default.dot), set("keys_dah", default.dah), set("keys_straight", default.straight))
        }
    }
}

/**
 * The trainer that is open now, so the platform's key handling can send an external key's presses to it: the session,
 * the key map, and while the connection panel is open, every key seen ([seen]) and the key to assign ([learn]).
 */
object MorseKeyBus {
    @Volatile var session: MorseKeySession? = null
    @Volatile var keyMap: MorseKeyMap? = null
    /** Every press and release of a key while the trainer is open: id and down. */
    @Volatile var seen: ((String, Boolean) -> Unit)? = null
    /** The next key pressed is assigned (the panel's "press the key" step). */
    @Volatile var learn: ((String) -> Unit)? = null

    /**
     * A key from the platform while the trainer is open. Returns true when it was taken (the platform then does not
     * use it for anything else, e.g. the volume).
     */
    fun key(id: String, down: Boolean, repeat: Boolean): Boolean {
        val s = session ?: return false
        if (!repeat) seen?.invoke(id, down)
        learn?.let { if (down && !repeat) { learn = null; it(id) }; return true }
        val role = keyMap?.role(id) ?: -1
        if (role < 0) return false
        if (!repeat) s.press(role, down)
        return true
    }
}
