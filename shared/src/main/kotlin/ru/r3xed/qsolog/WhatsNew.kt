package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.4.0 on the phone and the computer). */
    fun items(desktop: Boolean): List<String> = listOf(
        tr("Обмен QTH-локаторами в соревнованиях на УКВ: в контест-карточке «Мой локатор» и «Локатор корр.», локатор знакомой станции подставляется из прошлого тура, в отчёте ЕРМАК / Cabrillo локаторы идут после номеров. Настройки → CONTEST MODE."),
        if (desktop) tr("QSO-LOG делится трансивером с JTDX и WSJT-X: включите в настройках CAT «Делиться трансивером», а в JTDX выберите Rig «Hamlib NET rigctl», 127.0.0.1:4532. Выключать CAT в журнале больше не нужно — программа ставит частоту, вид и включает передачу через QSO-LOG.")
        else tr("QSO-LOG делится трансивером с другими программами (сервер Hamlib rigctld): включите в настройках CAT «Делиться трансивером» — JTDX или WSJT-X на компьютере подключаются к телефону по Wi-Fi."),
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
