package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.5.0 on the phone and the computer). */
    fun items(desktop: Boolean): List<String> = listOf(
        tr("Настройки стали короче: на главной странице — станция, ввод связи, соревнования, диапазоны, журнал и интерфейс. CAT, CW, цифровые виды и автозапуск — на странице «Оборудование и программы», учётные записи QRZ и онлайн-журналы — на странице «Справочники и онлайн-журналы»."),
        if (desktop) tr("Часть журнала для экспорта: «Файл» → «Выбрать часть журнала…» или кнопка-фильтр над выбранными записями — период, диапазоны, виды. Выбранные записи теперь выгружаются все, в том числе в ЕРМАК / Cabrillo.")
        else tr("Часть журнала для экспорта: «Экспорт и импорт» → «Часть журнала…» или кнопка-фильтр над выбранными записями — период, диапазоны, виды. Выбранные записи теперь выгружаются все, в том числе в ЕРМАК / Cabrillo. Долгое нажатие на TRX открывает настройки CAT."),
        tr("Позывные с /P, /M, /QRP и другими приставками ищутся на QRZ.ru и QRZ.com по основному позывному, а в журнал записываются как введены. «Уже работали» показывает связи со всеми вариантами позывного."),
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
