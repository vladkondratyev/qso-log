package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.3.0 on the phone and the computer). */
    fun items(desktop: Boolean): List<String> = listOf(
        tr("Шкалы на пульте трансивера: S-метр на приёме, мощность, КСВ и ALC на передаче (ICOM, Hamlib rigctld), и шкала диапазона с участками CW, цифры и SSB — нажмите на неё, чтобы перестроиться."),
        if (desktop) tr("Выгрузка в LoTW, Club Log и QRZ.com Logbook: ⋮ → «Экспорт и импорт» → «Онлайн-журналы». LoTW — через TQSL, QSO-LOG запускает его сам. Учётные записи — в Настройки → «Онлайн-журналы».")
        else tr("Выгрузка в Club Log и QRZ.com Logbook прямо с телефона, для LoTW — файл, который подписывает TQSL: ⋮ → «Экспорт и импорт» → «Онлайн-журналы». Учётные записи — в Настройки → «Онлайн-журналы»."),
        tr("В карточке связи видно, когда она ушла в каждый онлайн-журнал; в следующий раз уходят только новые связи."),
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
