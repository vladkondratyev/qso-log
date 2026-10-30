package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.8.0; the locator from the location only on the phone). */
    fun items(desktop: Boolean): List<String> = listOf(
        tr("DX-кластер (меню ⋮): споты с отметками по журналу — новая страна, новый диапазон, новый вид; нажатие на спот открывает карточку QSO и перестраивает трансивер."),
        if (desktop) tr("Мои станции: дом, выезд, клуб — у каждой свой позывной, локатор и мощность; переключение — нажатием на позывной под логотипом, фильтр журнала «Станция».")
        else tr("Мои станции: дом, выезд, клуб — у каждой свой позывной, локатор и мощность; переключение — нажатием на позывной под логотипом, фильтр журнала «Станция», локатор по геолокации."),
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
