package ru.r3xed.qsolog.keys

import ru.r3xed.qsolog.tr

/**
 * A key with its modifiers. [key] is a name the same on every platform: "N", "7", "F9", "Esc", "Enter", "Space", "Tab",
 * "Up", "Down", "Left", "Right", "PgUp", "PgDn", "Home", "End", "Delete", "Backspace", or a sign ("/", ",", ".", "-",
 * "=", ";", "'", "[", "]", "\\", "`"; "|" is never one). [ctrl] is Ctrl, ⌘ on the Mac.
 */
data class Chord(val key: String, val ctrl: Boolean = false, val alt: Boolean = false, val shift: Boolean = false) {
    fun label(mac: Boolean): String {
        val k = when (key) {
            "Space" -> tr("Пробел")
            "Up" -> "↑"
            "Down" -> "↓"
            "Left" -> "←"
            "Right" -> "→"
            else -> key
        }
        return if (mac) (if (alt) "⌥" else "") + (if (shift) "⇧" else "") + (if (ctrl) "⌘" else "") + k
        else (if (ctrl) "Ctrl+" else "") + (if (alt) "Alt+" else "") + (if (shift) "Shift+" else "") + k
    }

    fun encode(): String = (if (ctrl) "ctrl+" else "") + (if (alt) "alt+" else "") + (if (shift) "shift+" else "") + key

    /** A letter, digit or sign without Ctrl and Alt: it types into a field, so it cannot be a command there. */
    val types: Boolean get() = !ctrl && !alt && key.length == 1

    companion object {
        fun decode(s: String): Chord? {
            val parts = s.trim().split('+')
            // "+" itself, or "ctrl++": the key is the last part, and an empty one means the plus sign.
            val key = parts.last().ifEmpty { if (s.endsWith("+")) "+" else return null }
            val mods = parts.dropLast(1).map { it.lowercase() }.filter { it.isNotEmpty() }
            return Chord(key, ctrl = "ctrl" in mods, alt = "alt" in mods, shift = "shift" in mods)
        }

        /** Names of the keys that can be keys of a chord (see [Chord.key]); the platform code turns its key codes into them. */
        val NAMED = setOf(
            "Esc", "Enter", "Space", "Tab", "Up", "Down", "Left", "Right", "PgUp", "PgDn", "Home", "End", "Delete", "Backspace",
        ) + (1..12).map { "F$it" }
    }
}

private fun c(key: String, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) = Chord(key, ctrl, alt, shift)

/** Where a key works. Keys of overlapping scopes cannot be the same (see [KeyMap.conflicts]). */
enum class KeyScope {
    GLOBAL, LOG, CARD, CONTEST, HISTORY,
    /** Telegraph messages: on top of the card's keys while the card is in CW and the key line is on. */
    CW;

    val title: String
        get() = when (this) {
            GLOBAL -> tr("Везде")
            LOG -> tr("Журнал")
            CARD -> tr("Карточка связи")
            CONTEST -> tr("Режим соревнований")
            HISTORY -> tr("История поиска")
            CW -> tr("Телеграф (CW)")
        }

    fun overlaps(o: KeyScope): Boolean = when {
        this == CW || o == CW -> this == o
        this == o || this == GLOBAL || o == GLOBAL -> true
        else -> setOf(this, o) == setOf(CARD, CONTEST)
    }
}

/** Everything a key can be given to in "Дополнительные настройки → Клавиши". */
enum class KeyAction(val scope: KeyScope, vararg defaults: Chord) {
    NEW_QSO(KeyScope.GLOBAL, c("N", ctrl = true), c("F9")),
    EDIT_LAST(KeyScope.GLOBAL, c("E", ctrl = true)),
    SEARCH(KeyScope.GLOBAL, c("F", ctrl = true)),
    CONTEST_MODE(KeyScope.GLOBAL, c("K", ctrl = true)),
    SYNC(KeyScope.GLOBAL, c("R", ctrl = true)),
    RIG_PANEL(KeyScope.GLOBAL, c("T", ctrl = true)),
    KEYS_HELP(KeyScope.GLOBAL, c("F1"), c("/", ctrl = true)),

    HISTORY(KeyScope.LOG, c("H", ctrl = true)),
    DASHBOARD(KeyScope.LOG, c("D", ctrl = true)),
    MAP(KeyScope.LOG, c("M", ctrl = true)),
    SETTINGS(KeyScope.LOG, c(",", ctrl = true)),
    EXPORT_ADIF(KeyScope.LOG, c("E", ctrl = true, shift = true)),
    LOG_DELETE(KeyScope.LOG, c("Delete"), c("Backspace")),

    LOG_ENTER(KeyScope.CARD, c("Enter")),
    SPACE_NEXT(KeyScope.CARD, c("Space")),
    CLEAR_CARD(KeyScope.CARD, c("W", alt = true), c("Esc", ctrl = true)),
    CLOSE_CARD(KeyScope.CARD, c("Esc")),
    DELETE_SAVED(KeyScope.CARD, c("D", ctrl = true)),
    FOCUS_CALL(KeyScope.CARD, c("F2")),
    FOCUS_FREQ(KeyScope.CARD, c("F3")),
    FOCUS_RST_SENT(KeyScope.CARD, c("F4")),
    FOCUS_RST_RCVD(KeyScope.CARD, c("F5")),
    TIME_NOW(KeyScope.CARD, c("F6")),
    VOICE(KeyScope.CARD, c("F7")),
    SAVE_NEXT(KeyScope.CARD, c("F8")),
    SAVE(KeyScope.CARD, c("F12")),

    CONTEST_PREV(KeyScope.CONTEST, c("PgUp"), c("Left", alt = true)),
    CONTEST_NEXT(KeyScope.CONTEST, c("PgDn"), c("Right", alt = true)),

    H_FAVORITE(KeyScope.HISTORY, c("F")),
    H_EDIT(KeyScope.HISTORY, c("E")),
    H_NEW(KeyScope.HISTORY, c("N")),
    H_MAP(KeyScope.HISTORY, c("M")),
    H_DELETE(KeyScope.HISTORY, c("Delete")),

    CW_1(KeyScope.CW, c("F1")),
    CW_2(KeyScope.CW, c("F2")),
    CW_3(KeyScope.CW, c("F3")),
    CW_4(KeyScope.CW, c("F4")),
    CW_5(KeyScope.CW, c("F5")),
    CW_6(KeyScope.CW, c("F6")),
    CW_7(KeyScope.CW, c("F7")),
    CW_8(KeyScope.CW, c("F8")),
    CW_STOP(KeyScope.CW, c("Esc")),
    CW_FASTER(KeyScope.CW, c("PgUp"), c("Up", alt = true)),
    CW_SLOWER(KeyScope.CW, c("PgDn"), c("Down", alt = true));

    val defaults: List<Chord> = defaults.toList()

    /** The telegraph message number 0–7 of CW_1…CW_8, else -1. */
    val cwMessage: Int get() = if (name.startsWith("CW_") && name.length == 4) name[3] - '1' else -1

    val title: String
        get() = when (this) {
            NEW_QSO -> tr("новая связь")
            EDIT_LAST -> tr("исправить последнюю записанную связь")
            SEARCH -> tr("поиск по журналу; Enter в поиске — новая связь с найденным, ↓ — к списку")
            CONTEST_MODE -> tr("CONTEST MODE: включить или выключить")
            SYNC -> tr("синхронизировать с Google Таблицей")
            RIG_PANEL -> tr("пульт трансивера: частота, диапазон, вид")
            KEYS_HELP -> tr("эта подсказка")
            HISTORY -> tr("история поиска")
            DASHBOARD -> tr("дашборд (на выбранной записи — удалить её)")
            MAP -> tr("карта QSO")
            SETTINGS -> tr("настройки")
            EXPORT_ADIF -> tr("экспорт журнала в ADIF")
            LOG_DELETE -> tr("удалить запись или отмеченные: окно спросит, Enter — удалить")
            LOG_ENTER -> tr("из любого поля — записать (новая карточка — записать и открыть следующую)")
            SPACE_NEXT -> tr("по кругу: позывной → RST отправлен → RST принят → позывной (у контест-связи: позывной ↔ код принят)")
            CLEAR_CARD -> tr("очистить новую карточку: курсор — в позывной")
            CLOSE_CARD -> tr("закрыть карточку (с изменениями — спросит, Enter — закрыть)")
            DELETE_SAVED -> tr("удалить сохранённую связь (Enter — подтвердить)")
            FOCUS_CALL -> tr("к позывному")
            FOCUS_FREQ -> tr("к частоте")
            FOCUS_RST_SENT -> tr("к RST отправленному")
            FOCUS_RST_RCVD -> tr("к RST принятому")
            TIME_NOW -> tr("текущее время UTC")
            VOICE -> tr("голосовая заметка: начать или остановить")
            SAVE_NEXT -> tr("сохранить и открыть следующую")
            SAVE -> tr("сохранить")
            CONTEST_PREV -> tr("предыдущая связь")
            CONTEST_NEXT -> tr("следующая связь")
            H_FAVORITE -> tr("в избранное или из избранного")
            H_EDIT -> tr("изменить (отмеченные — все сразу)")
            H_NEW -> tr("новый QSO с этой станцией")
            H_MAP -> tr("все на карте или списком")
            H_DELETE -> tr("удалить из истории")
            CW_STOP -> tr("остановить передачу")
            CW_FASTER -> tr("скорость передачи +2 WPM")
            CW_SLOWER -> tr("скорость передачи −2 WPM")
            else -> tr("передать сообщение %s", cwMessage + 1)
        }
}

/**
 * The keys of every action: the defaults, with what the user changed in the settings on top. The platform code turns a
 * key event into a [Chord] and asks [hit].
 */
object KeyMap {
    /** The actions the user changed; an empty list — the action has no key. */
    @Volatile var custom: Map<KeyAction, List<Chord>> = emptyMap()

    fun chords(a: KeyAction): List<Chord> = custom[a] ?: a.defaults

    fun hit(a: KeyAction, c: Chord?): Boolean = c != null && c in chords(a)

    /** "Ctrl+N · F9"; empty when the action has no key. */
    fun label(a: KeyAction, mac: Boolean): String = chords(a).joinToString(" · ") { it.label(mac) }

    /** Actions of scopes overlapping [a]'s that already have [chord]. */
    fun conflicts(a: KeyAction, chord: Chord, map: Map<KeyAction, List<Chord>> = custom): List<KeyAction> =
        KeyAction.entries.filter { o -> o != a && o.scope.overlaps(a.scope) && chord in (map[o] ?: o.defaults) }

    /** Why [chord] cannot be [a]'s key, or null when it can. */
    fun refusal(a: KeyAction, chord: Chord): String? = when {
        chord.types && a.scope != KeyScope.LOG && a.scope != KeyScope.HISTORY ->
            tr("Буква или цифра без Ctrl или Alt нужна для набора текста: добавьте Ctrl или Alt")
        chord.key == "Tab" && !chord.ctrl && !chord.alt -> tr("Tab переходит по полям — выберите другую клавишу")
        else -> null
    }

    /**
     * [map] with [a]'s keys set to [chords]; any of them that another action of an overlapping scope had is taken from it.
     * Back to the defaults, an action leaves the map.
     */
    fun assign(map: Map<KeyAction, List<Chord>>, a: KeyAction, chords: List<Chord>): Map<KeyAction, List<Chord>> {
        val out = map.toMutableMap()
        chords.forEach { ch ->
            conflicts(a, ch, out).forEach { o -> out[o] = (out[o] ?: o.defaults) - ch }
        }
        out[a] = chords.distinct()
        // Whatever equals its defaults again is not stored.
        return out.filter { (k, v) -> v != k.defaults }
    }

    /**
     * "NEW_QSO=ctrl+N F9|CLEAR_CARD=" — what [custom] keeps in the settings. Chords are split by spaces and actions by
     * "|": neither is a key name ("Space" is), while "," and ";" are.
     */
    fun encode(map: Map<KeyAction, List<Chord>>): String =
        map.entries.sortedBy { it.key.ordinal }.joinToString("|") { (a, l) -> a.name + "=" + l.joinToString(" ") { it.encode() } }

    fun decode(s: String?): Map<KeyAction, List<Chord>> = s.orEmpty().split('|').mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq < 0) return@mapNotNull null
        val a = KeyAction.entries.firstOrNull { it.name == part.substring(0, eq) } ?: return@mapNotNull null
        a to part.substring(eq + 1).split(' ').filter { it.isNotBlank() }.mapNotNull { Chord.decode(it) }
    }.toMap()
}
