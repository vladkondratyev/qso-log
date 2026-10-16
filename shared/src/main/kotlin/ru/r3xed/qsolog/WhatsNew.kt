package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.2.0 on the phone and the computer). */
    fun items(desktop: Boolean): List<String> = listOf(
        tr("Пульт трансивера: нажмите на строку TRX (или Ctrl+T) — частота крупно, её можно вести пальцем или мышью как ручку, кнопки диапазонов и видов. Кнопки диапазона и вида в новой карточке тоже перестраивают трансивер."),
        tr("Телеграф с клавиш F1–F8, как в N1MM: CQ, позывные, RST и контрольный номер передаются линией DTR или RTS USB-адаптера (CP2102 и другие). Настройки → «Телеграф (CW)»."),
        if (desktop) tr("Дополнительные настройки: свои сочетания клавиш и программы, которые запускаются вместе с QSO-LOG, — например, JTDX и Hamlib rigctld, чтобы JTDX и QSO-LOG работали с одним трансивером.")
        else tr("Дополнительные настройки: свои сочетания клавиш внешней клавиатуры и приложения, которые запускаются вместе с QSO-LOG."),
        tr("Без CAT частота и вид берутся из JTDX и WSJT-X по UDP — в карточке видно «JTDX 14 074.000 · FT8»."),
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
