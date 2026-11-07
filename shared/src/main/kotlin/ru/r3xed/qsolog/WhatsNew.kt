package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.9.1; the phone has the bottom bar and background alerts, the computer menus, ⌘K and the status bar). */
    fun items(desktop: Boolean): List<String> = listOfNotNull(
        if (desktop) tr("Меню как в других программах: Файл · Правка · Вид · Трансивер · Сервис · Справка; ⌘K / Ctrl+K — поиск любой команды; строка состояния внизу: трансивер, кластер, связи за сегодня, прохождение.")
        else tr("Нижняя панель: Журнал · Кластер · Карта · Статистика."),
        tr("Автокопия журнала в ADIF раз в день или неделю (Настройки → Журнал связей), хранятся последние 10 копий."),
        tr("Фильтр журнала: не отправлено в LoTW / eQSL / Club Log / QRZ.com, очередь бумажных QSL, диапазон, вид; «Мои фильтры» сохраняются под именем."),
        tr("Дипломы: DXCC, WAZ, WAS, регионы России, RDA — сработано и подтверждено по диапазонам, чего не хватает."),
        tr("DX-кластер: отметка «новая область», уведомления о спотах и список ожидания; споты и серая зона на карте QSO, индексы прохождения на дашборде."),
        tr("Выезд: активация POTA, WWFF, SOTA, IOTA, RDA — поля в каждой связи, счётчик и выгрузка ADIF."),
        tr("Печать QSL-наклеек и отметка «QSL отправлены» для выбранных связей; несколько сертификатов LoTW — по одному на позывной."),
        tr("Поиск по настройкам и красная точка у блока, где что-то не так. CAT: Yaesu FT-840 / 890 / 900 / 990 (старый протокол)."),
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
