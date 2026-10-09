package ru.r3xed.qsolog

/**
 * Every key of the app in one place, by screen: the F1 list on the computer and on a phone with an external keyboard
 * shows exactly this. [mac]: ⌘ instead of Ctrl+.
 */
object KeyHints {
    class Section(val title: String, val keys: List<Pair<String, String>>)

    fun sections(mac: Boolean): List<Section> {
        val m = if (mac) "⌘" else "Ctrl+"
        return listOf(
            Section(tr("Везде"), listOf(
                "${m}N · F9" to tr("новая связь"),
                "${m}E" to tr("исправить последнюю записанную связь"),
                "${m}F" to tr("поиск по журналу; Enter в поиске — новая связь с найденным, ↓ — к списку"),
                "${m}H" to tr("история поиска"),
                "${m}D · ${m}M · $m," to tr("дашборд · карта QSO · настройки"),
                "${m}K" to tr("CONTEST MODE: включить или выключить"),
                "${m}R" to tr("синхронизировать с Google Таблицей"),
                (if (mac) "⇧⌘E" else "Ctrl+Shift+E") to tr("экспорт журнала в ADIF"),
                ("F1" + if (mac) "" else " · Ctrl+/") to tr("эта подсказка"),
            )),
            Section(tr("Журнал"), listOf(
                "↑ ↓" to tr("по записям"),
                "PgUp · PgDn · Home · End" to tr("на 10 записей, к первой, к последней"),
                "Enter" to tr("открыть запись"),
                (tr("Пробел") + " · ${m}A") to tr("отметить запись · отметить все"),
                "Delete" to tr("удалить запись (можно отменить)"),
                "Esc" to tr("снять отметки"),
            )),
            Section(tr("Карточка связи"), listOf(
                (tr("Пробел") + " · Enter") to tr("из позывного — к частоте или RST (Enter в позывном связь не записывает)"),
                "Enter" to tr("в остальных полях — записать (новая карточка — записать и открыть следующую)"),
                "Tab · Shift+Tab" to tr("следующее и предыдущее поле"),
                "↑" to tr("в пустом позывном — исправить последнюю связь"),
                "Esc" to tr("очистить новую карточку, второй раз — закрыть"),
                "F2 · F3 · F4 · F5" to tr("к позывному, частоте, RST отправлен, RST принят"),
                "F6" to tr("текущее время UTC"),
                "F7" to tr("голосовая заметка: начать или остановить"),
                ("F8" + if (mac) " · ⇧⌘S" else "") to tr("сохранить и открыть следующую"),
                ("F12" + if (mac) " · ⌘S" else "") to tr("сохранить"),
                "Alt+1…9 · Alt+Shift+1…9" to tr("диапазон · вид связи (n-й из включённых)"),
                "Shift+Enter" to tr("новая строка в комментарии"),
            )),
            Section(tr("Режим соревнований"), listOf(
                (tr("Пробел") + " · Enter") to tr("из позывного — к принятому коду"),
                "Enter · F12" to tr("в коде или RST — записать связь; F12 — из любого поля"),
                "↑ · PgUp" to tr("предыдущая связь (↑ — в пустом позывном)"),
                "PgDn" to tr("следующая связь"),
                "Esc" to tr("очистить карточку, второй раз — закрыть"),
            )),
            Section(tr("История поиска"), listOf(
                "↑ ↓ · Enter" to tr("по записям · открыть с картой"),
                (tr("Пробел") + " · ${m}A") to tr("отметить запись · отметить все"),
                "F" to tr("в избранное или из избранного"),
                "E" to tr("изменить (отмеченные — все сразу)"),
                "N" to tr("новый QSO с этой станцией"),
                "M" to tr("все на карте или списком"),
                "Delete" to tr("удалить из истории"),
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
}
