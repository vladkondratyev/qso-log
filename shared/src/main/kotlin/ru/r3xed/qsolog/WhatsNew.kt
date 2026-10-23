package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.7.2 on the phone and the computer). */
    fun items(desktop: Boolean): List<String> = listOf(
        tr("QRZ.com по API: имя, QTH, локатор и зоны через интерфейс для программ (с подпиской XML); спрашивается перед сайтом QRZ.com."),
        tr("Справочники спрашиваются одновременно: карточка заполняется быстрее, а данные по-прежнему берутся по порядку списка — сначала QRZ.ru, потом QRZ.com, HamQTH, QRZCQ."),
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
