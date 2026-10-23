package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.7.1 on the phone and the computer). */
    fun items(desktop: Boolean): List<String> = listOfNotNull(
        tr("Карточка QSO по двойному щелчку в WSJT-X и JTDX: сразу видны QTH, карта и расстояние, а связь, записанная в WSJT-X, сохраняется вместе с этой карточкой (включается в настройках цифровых видов)."),
        if (desktop) null else tr("Ключ на экране: кнопки «·» и «—» или «КЛЮЧ» на своей клавиатуре в карточке QSO с видом CW (включается в настройках CW)."),
        if (desktop) null else tr("Ряд F1–F8 на своей клавиатуре: все клавиши одного размера, «Стоп» занимает две; скорость меняется в карточке."),
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
