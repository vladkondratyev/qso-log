package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.6.0 on the phone and the computer). */
    fun items(desktop: Boolean): List<String> = listOf(
        tr("eQSL: связи выгружаются туда, как в LoTW, Club Log и QRZ.com, а «Экспорт и импорт» → «Карточки eQSL» загружает полученные карточки — у найденных связей появится отметка «eQSL получена», а связи, которых нет в журнале, можно добавить."),
        tr("Телеграф по сети: в настройках CW можно выбрать cwdaemon — QSO-LOG передаёт сообщения F1–F8 и скорость по UDP на компьютер, к которому подключён трансивер."),
        tr("CAT через DXLab Commander: QSO-LOG берёт у него частоту, вид и передачу и перестраивает трансивер через него. Hamlib rigctld и встроенные драйверы остаются как были."),
        if (desktop) tr("Исправлено: если у нового позывного на QRZ.ru не указан RDA, область или зоны, в карточке больше не остаются значения от позывного, введённого до него.")
        else tr("Исправлено: если у нового позывного на QRZ.ru не указан RDA, область или зоны, в карточке больше не остаются значения от позывного, введённого до него. «Запускать вместе» на Xiaomi, Redmi и Poco подсказывает, какое разрешение включить, и умеет не возвращаться в QSO-LOG сразу."),
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
