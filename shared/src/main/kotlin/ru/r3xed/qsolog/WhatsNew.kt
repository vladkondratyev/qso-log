package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release: 2.9.6 — the users' requests after 2.9.5. */
    fun items(desktop: Boolean): List<String> = listOfNotNull(
        if (desktop) tr("Горячие клавиши для всех разделов и действий меню: DX-кластер ⌘⇧C, Прохождение ⌘⇧P, Дипломы ⌘⇧A, Справка ⌘⇧R, онлайн-журналы ⌘U, LoTW ⌘⇧L, импорт ADIF ⌘O, копия журнала ⌘B, таблица ⌘⇧T, следующая станция ⌘⇧N (на Windows и Linux — Ctrl). Клавиши видны в меню и в ⌘K; меняются в настройках.")
        else tr("С внешней клавиатурой — горячие клавиши для разделов: DX-кластер Ctrl+Shift+C, Прохождение Ctrl+Shift+P, Дипломы Ctrl+Shift+A, Справка Ctrl+Shift+R, онлайн-журналы Ctrl+U, импорт ADIF Ctrl+O, следующая станция Ctrl+Shift+N; список — F1."),
        tr("DX-кластер с клавиатуры: ↑ ↓ по спотам, Enter — перестроить трансивер и открыть карточку, Shift+Enter — только карточка, S — свой спот, цифры — диапазон."),
        tr("Дата и время в карточке набираются цифрами: 1234 → 12:34, 931 → 09:31, 1411 → 14.11 этого года; ↑ и ↓ — на минуту или день."),
        if (desktop) tr("Переключить станцию (например, на номер наблюдателя SWL) можно щелчком по позывному в строке состояния — и в режиме таблицы; станция-наблюдатель помечена SWL.")
        else tr("Станция-наблюдатель помечена SWL рядом с позывным; новую станцию можно добавить прямо из списка станций."),
        tr("Связи, выгруженные в HAMLOG, отмечены фиолетовой точкой рядом с точками экспорта, как ADIF и CSV."),
        tr("Kenwood TS-590/890/990: цифровой режим (USB с DA1) распознаётся — кнопка FT8 на пульте горит."),
        if (desktop) tr("Экспорт и импорт открываются в папке, куда сохраняли в прошлый раз.") else null,
        if (desktop) tr("Ширина колонок таблицы меняется перетаскиванием края заголовка, двойной щелчок — по содержимому. Наведите на точки экспорта — видно, куда и когда выгружено.") else null,
        if (desktop) tr("Исправлено: после изменения масштаба колесом мыши могло появиться окно «Error» и программа закрывалась.") else null,
    )
}

/** A new GitHub issue with the app's version and the device filled in — no personal data, nothing from the log. */
object ProblemReport {
    fun url(appVersion: String, platform: String, device: String): String {
        val body = buildString {
            appendLine("**${tr("Что случилось")}:**")
            appendLine()
            appendLine("**${tr("Что ожидали")}:**")
            appendLine()
            appendLine("**${tr("Как повторить")}:**")
            appendLine("1. ")
            appendLine()
            appendLine("---")
            appendLine("QSO-LOG $appVersion · $platform · $device")
        }
        return "https://github.com/vladkondratyev/qso-log/issues/new?body=" + URLEncoder.encode(body, "UTF-8").replace("+", "%20")
    }
}
