package ru.r3xed.qsolog

/**
 * Every key of the app in one place, by screen: the F1 list on the computer and on a phone with an external keyboard
 * shows exactly this. [mac]: ⌘ instead of Ctrl+. [phone]: the Android app, a release ahead of the computer in the cards
 * (Space goes round the fields, Enter always logs, Alt+W wipes) and the log (Ctrl+D / Delete ask before deleting).
 */
object KeyHints {
    class Section(val title: String, val keys: List<Pair<String, String>>)

    fun sections(mac: Boolean, phone: Boolean = false): List<Section> {
        val m = if (mac) "⌘" else "Ctrl+"
        return listOf(
            Section(tr("Везде"), listOf(
                "${m}N · F9" to tr("новая связь"),
                "${m}E" to tr("исправить последнюю записанную связь"),
                "${m}F" to tr("поиск по журналу; Enter в поиске — новая связь с найденным, ↓ — к списку"),
                "${m}H" to tr("история поиска"),
                "${m}D · ${m}M · $m," to if (phone) tr("дашборд (Ctrl+D на выбранной записи — удалить её) · карта QSO · настройки") else tr("дашборд · карта QSO · настройки"),
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
                if (phone) "Delete · Ctrl+D" to tr("удалить запись или отмеченные: окно спросит, Enter — удалить")
                else "Delete" to tr("удалить запись (можно отменить)"),
                "Esc" to tr("снять отметки"),
            )),
            Section(tr("Карточка связи"), listOfNotNull(
                if (phone) tr("Пробел") to tr("по кругу: позывной → RST отправлен → RST принят → позывной (у контест-связи: позывной ↔ код принят)")
                else (tr("Пробел") + " · Enter") to tr("из позывного — к частоте или RST (Enter в позывном связь не записывает)"),
                if (phone) "Enter" to tr("из любого поля — записать (новая карточка — записать и открыть следующую)")
                else "Enter" to tr("в остальных полях — записать (новая карточка — записать и открыть следующую)"),
                "Tab · Shift+Tab" to tr("следующее и предыдущее поле"),
                "↑" to tr("в пустом позывном — исправить последнюю связь"),
                if (phone) "Alt+W · Ctrl+Esc" to tr("очистить новую карточку: курсор — в позывной") else null,
                if (phone) "Esc" to tr("закрыть карточку (с изменениями — спросит, Enter — закрыть)")
                else "Esc" to tr("очистить новую карточку, второй раз — закрыть"),
                if (phone) "Ctrl+D" to tr("удалить сохранённую связь (Enter — подтвердить)") else null,
                "F2 · F3 · F4 · F5" to tr("к позывному, частоте, RST отправлен, RST принят"),
                "F6" to tr("текущее время UTC"),
                "F7" to tr("голосовая заметка: начать или остановить"),
                ("F8" + if (mac) " · ⇧⌘S" else "") to tr("сохранить и открыть следующую"),
                ("F12" + if (mac) " · ⌘S" else "") to tr("сохранить"),
                "Alt+1…9 · Alt+Shift+1…9" to tr("диапазон · вид связи (n-й из включённых)"),
                "Shift+Enter" to tr("новая строка в комментарии"),
            )),
            Section(tr("Режим соревнований"), listOfNotNull(
                if (phone) tr("Пробел") to tr("позывной ↔ принятый код (RST пропускается)")
                else (tr("Пробел") + " · Enter") to tr("из позывного — к принятому коду"),
                if (phone) "Enter · F12" to tr("записать связь из любого поля")
                else "Enter · F12" to tr("в коде или RST — записать связь; F12 — из любого поля"),
                "↑ · PgUp" to tr("предыдущая связь (↑ — в пустом позывном)"),
                "PgDn" to tr("следующая связь"),
                if (phone) "Alt+W · Ctrl+Esc" to tr("очистить карточку") else null,
                if (phone) "Esc" to tr("закрыть карточку") else "Esc" to tr("очистить карточку, второй раз — закрыть"),
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

    /** The app's own on-screen keypad: its gestures, under the keys on the phone. */
    val keypadGestures get() = tr("Своя экранная клавиатура: провести пальцем влево и, не отрывая, обратно вправо — стереть поле; удержать палец и вести в сторону — двигать курсор. ⌫ справа в поле позывного стирает его целиком.")
}
