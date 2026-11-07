package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.9.2: the conditions seen from the station's QTH; where to find them differs). */
    fun items(desktop: Boolean): List<String> = listOf(
        if (desktop) tr("«Прохождение» (меню «Вид», дашборд или индексы в строке состояния): всё — от QTH станции в работе.")
        else tr("«Прохождение» («Статистика» или меню ⋮): всё — от QTH станции в работе."),
        tr("У вас: день или ночь и когда восход или закат, MUF ближайшего ионозонда и до какого диапазона открыты дальние трассы, поглощение, полярное сияние, свежие вспышки."),
        tr("Оценка диапазонов N0NBH днём и ночью, Kp за сутки и прогноз на 3 дня, поток F10.7 за месяц, вспышки, УКВ-явления."),
        tr("PSK Reporter: что слышно в вашем квадрате по диапазонам и направлениям и кто слышит ваш позывной."),
        tr("Маяки NCDXF: какой маяк сейчас на каком диапазоне, расстояние и азимут от вас; нажатие настраивает трансивер."),
        tr("Слои карты QSO: «Меня слышат», MUF ионозондов, поглощение, полярное сияние."),
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
