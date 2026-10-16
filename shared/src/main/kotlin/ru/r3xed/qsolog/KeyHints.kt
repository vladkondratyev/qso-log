package ru.r3xed.qsolog

import ru.r3xed.qsolog.keys.KeyAction
import ru.r3xed.qsolog.keys.KeyMap

/**
 * Every key of the app in one place, by screen: the F1 list on the computer and on a phone with an external keyboard
 * shows exactly this. The keys of [KeyAction]s are the user's (see [KeyMap]); arrows, Tab and the like are fixed.
 * [mac]: ⌘ instead of Ctrl+. [cwMessages]: the telegraph messages, shown when the key line is on.
 */
object KeyHints {
    class Section(val title: String, val keys: List<Pair<String, String>>)

    fun sections(mac: Boolean, cwMessages: List<String>? = null): List<Section> {
        val m = if (mac) "⌘" else "Ctrl+"
        fun k(a: KeyAction, what: String = a.title): Pair<String, String>? = KeyMap.label(a, mac).ifEmpty { null }?.let { it to what }
        return listOfNotNull(
            Section(tr("Везде"), listOfNotNull(
                k(KeyAction.NEW_QSO), k(KeyAction.EDIT_LAST), k(KeyAction.SEARCH), k(KeyAction.CONTEST_MODE), k(KeyAction.SYNC),
                k(KeyAction.RIG_PANEL), k(KeyAction.KEYS_HELP),
            )),
            Section(tr("Журнал"), listOfNotNull(
                "↑ ↓" to tr("по записям"),
                "PgUp · PgDn · Home · End" to tr("на 10 записей, к первой, к последней"),
                "Enter" to tr("открыть запись"),
                (tr("Пробел") + " · ${m}A") to tr("отметить запись · отметить все"),
                k(KeyAction.LOG_DELETE),
                "Esc" to tr("снять отметки"),
                k(KeyAction.HISTORY), k(KeyAction.DASHBOARD), k(KeyAction.MAP), k(KeyAction.SETTINGS), k(KeyAction.EXPORT_ADIF),
            )),
            Section(tr("Карточка связи"), listOfNotNull(
                k(KeyAction.SPACE_NEXT), k(KeyAction.LOG_ENTER),
                "Tab · Shift+Tab" to tr("следующее и предыдущее поле"),
                "↑" to tr("в пустом позывном — исправить последнюю связь"),
                k(KeyAction.CLEAR_CARD), k(KeyAction.CLOSE_CARD), k(KeyAction.DELETE_SAVED),
                k(KeyAction.FOCUS_CALL), k(KeyAction.FOCUS_FREQ), k(KeyAction.FOCUS_RST_SENT), k(KeyAction.FOCUS_RST_RCVD),
                k(KeyAction.TIME_NOW), k(KeyAction.VOICE), k(KeyAction.SAVE_NEXT), k(KeyAction.SAVE),
                "Alt+1…9 · Alt+Shift+1…9" to tr("диапазон · вид связи (n-й из включённых)"),
                "Shift+Enter" to tr("новая строка в комментарии"),
            )),
            Section(tr("Режим соревнований"), listOfNotNull(
                k(KeyAction.SPACE_NEXT, tr("позывной ↔ принятый код (RST пропускается)")),
                (listOf(KeyMap.label(KeyAction.LOG_ENTER, mac), KeyMap.label(KeyAction.SAVE, mac)).filter { it.isNotEmpty() }.joinToString(" · ")
                    .ifEmpty { null })?.let { it to tr("записать связь из любого поля") },
                k(KeyAction.CONTEST_PREV, tr("предыдущая связь (↑ — в пустом позывном)")), k(KeyAction.CONTEST_NEXT),
                k(KeyAction.CLEAR_CARD, tr("очистить карточку")), k(KeyAction.CLOSE_CARD, tr("закрыть карточку")),
            )),
            cwMessages?.let { msgs ->
                Section(tr("Телеграф (CW)"), listOfNotNull(
                    *(0 until 8).map { i ->
                        val a = KeyAction.entries.first { it.cwMessage == i }
                        msgs.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { k(a, it) }
                    }.toTypedArray(),
                    k(KeyAction.CW_STOP), k(KeyAction.CW_FASTER), k(KeyAction.CW_SLOWER),
                ))
            },
            Section(tr("История поиска"), listOfNotNull(
                "↑ ↓ · Enter" to tr("по записям · открыть с картой"),
                (tr("Пробел") + " · ${m}A") to tr("отметить запись · отметить все"),
                k(KeyAction.H_FAVORITE), k(KeyAction.H_EDIT), k(KeyAction.H_NEW), k(KeyAction.H_MAP), k(KeyAction.H_DELETE),
                "Esc" to tr("назад"),
            )),
            Section(tr("Дашборд"), listOf(
                "1 · 2 · 3 · 4" to tr("7 дней · 30 дней · год · всё время"),
                "Esc" to tr("назад"),
            )),
        )
    }

    /** Typed instead of a callsign, then Enter or Space. */
    val commands get() = tr("Команды в поле позывного (вместо позывного, затем Enter или Пробел): 20m или 20 — диапазон, CW, SSB, FT8 — вид связи, 14195 или 7.074 — частота. Поле очищается, карточка остаётся.")

    /** The app's own on-screen keypad: its gestures, under the keys on the phone. */
    val keypadGestures get() = tr("Своя экранная клавиатура: провести пальцем влево и, не отрывая, обратно вправо — стереть поле; удержать палец и вести в сторону — двигать курсор. ⌫ справа в поле позывного стирает его целиком.")
}
