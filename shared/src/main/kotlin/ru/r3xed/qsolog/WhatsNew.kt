package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.7.3; the compact log only on the computer). */
    fun items(desktop: Boolean): List<String> = listOfNotNull(
        tr("Данные из прошлой связи: если со станцией уже работали, имя, QTH и локатор берутся из журнала, а не из справочников (Настройки → Данные о корреспонденте)."),
        tr("Компактный журнал: Esc на экране «Готов к записи» — строки связей в две линии, на экране помещается вдвое больше; Esc ещё раз — как было.").takeIf { desktop },
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
