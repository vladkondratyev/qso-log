package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /**
     * Points of this release: 2.9.4 — HAMLOG.online first, then 2.9.3 again (the users' requests; the table and PTT
     * are the computer's, the search keypad the phone's).
     */
    fun items(desktop: Boolean): List<String> = listOfNotNull(
        tr("HAMLOG.online: связи уходят в облачный журнал HAMLOG — по кнопке из журнала или сразу после записи. Подключение — кодом на сайте HAMLOG (Настройки → Онлайн-журналы), без паролей."),
        tr("Поле позывного подсвечивается: красным — повтор на этом диапазоне и виде, оранжевым — диапазон тот же, вид другой, зелёным — новый диапазон. «Уже работали» — сеткой «диапазоны × CW / PH / DIGI»."),
        if (desktop) tr("«Журнал таблицей» (меню «Вид»): весь журнал компактной таблицей, свои колонки, сортировка по щелчку, группировка и фильтры; новое QSO — карточкой поверх таблицы.")
        else tr("Своя клавиатура работает и в поиске по журналу."),
        if (desktop) tr("Телеграф: задержка отпускания и опережение PTT настраиваются (раньше PTT держался 0,7 с), PTT можно вывести на отдельный COM-порт.") else null,
        tr("Два набора макросов F1–F8: повседневные и для контеста, переключаются сами."),
        tr("Трансвертеры (настройки CAT): 28 → 432, 144 → 1296 МГц — в журнал и на шкалу идёт частота в эфире."),
        tr("«Своя тема»: любые цвета интерфейса; контрастная обводка активного поля ввода — в любой теме."),
        tr("Поле IOTA корреспондента, клавиша Ё стирает позывной, полный заголовок Cabrillo (POWER, OPERATORS, NAME, ADDRESS, EMAIL, CLUB…)."),
        tr("Синхронизация настроек программы через вторую Google Таблицу (по желанию — и учётные записи сервисов)."),
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
