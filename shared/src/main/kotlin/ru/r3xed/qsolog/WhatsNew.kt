package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.1.0 on the phone and the computer). */
    fun items(desktop: Boolean): List<String> = listOf(
        tr("Цифровые виды связи: связи из WSJT-X, JTDX и MSHV сами попадают в журнал — с частотой, видом (FT8, FT4 и другими), отчётами в дБ и локатором. Имя и QTH подтягиваются с QRZ.ru, повторы не записываются."),
        if (desktop) tr("Включается в Настройки → «Цифровые виды — WSJT-X, JTDX, MSHV». В WSJT-X: Settings → Reporting → UDP Server — 127.0.0.1, порт 2237; связь записывается по OK в окне «Log QSO».")
        else tr("На телефоне: WSJT-X, JTDX или MSHV на компьютере в той же сети Wi-Fi шлёт связи на адрес телефона — он показан в Настройки → «Цифровые виды — WSJT-X, JTDX, MSHV»."),
        tr("Можно слушать групповой адрес (multicast), если связи из WSJT-X нужны ещё и JTAlert или GridTracker. Принимается и простой ADIF по UDP."),
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
