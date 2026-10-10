package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /**
     * Points of this release. The phone (0.39.1) is a release ahead of the computer (1.18.0): each has its own list
     * until the computer catches up.
     */
    fun items(desktop: Boolean): List<String> = if (desktop) desktopItems() else listOf(
        tr("Список горячих клавиш — первая вкладка справки «Клавиши» и кнопка «Список клавиш» в настройках, блок «Ввод связи». Открыть его можно и без подключённой клавиатуры."),
        tr("В меню ⋮ переключатель CONTEST MODE и справочник контестов теперь под «Справкой и калькуляторами»."),
        tr("Меню ⋮ разложено по группам: дашборд, карта и история поиска; «Экспорт и импорт…» и синхронизация; справка; режим CONTEST; настройки."),
    )

    private fun desktopItems(): List<String> = listOfNotNull(
        tr("Справочник контестов (меню ⋮ в режиме CONTEST): название, код, время и туры для минитестов. Каждая связь помечается соревнованием и туром, в журнале — тур и таймер до его конца, отчёт ЕРМАК/Cabrillo — по выбранному соревнованию."),
        tr("Повторы в соревновании считаются по всему соревнованию, а с флажком «Обнулять повторы» — в каждом туре заново."),
        tr("Первый запуск проще: только позывной и локатор, источники данных можно подключить потом."),
        tr("«Что нового» и «Сообщить о проблеме» — в настройках, под номером версии."),
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
