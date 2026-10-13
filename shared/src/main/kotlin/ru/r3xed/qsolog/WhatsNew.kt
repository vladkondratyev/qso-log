package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /**
     * Points of this release. The phone (0.40.2) is releases ahead of the computer (1.18.0): each has its own list
     * until the computer catches up.
     */
    fun items(desktop: Boolean): List<String> = if (desktop) desktopItems() else listOf(
        tr("QRZ.com снова подгружает данные о корреспонденте на Android: вход больше не обрывается ошибкой «gzip finished without exhausting source»."),
        tr("Стереть поле своей клавиатурой теперь — провести пальцем влево и, не отрывая, обратно вправо: случайный свайп в одну сторону поле не стирает."),
        tr("Внешняя клавиатура как в классических логгерах: Пробел ходит по полям позывной → RST → RST (в контесте — сразу к номеру), Enter записывает связь из любого поля, Alt+W или Ctrl+Esc очищает карточку, Esc закрывает её."),
        tr("Удаление с клавиатуры: Ctrl+D или Delete на записи журнала или в открытой карточке, Enter в окне подтверждения — удалить."),
        tr("Своя клавиатура: провести пальцем влево и, не отрывая, обратно вправо — стереть поле; удержать палец — курсор ходит между буквами (время удержания — в настройках «Ввод связи»). ⌫ в поле позывного и поиска стирает его целиком."),
        tr("Настройки «Ввод связи»: «Не выключать экран во время работы приложения» и «Фиксировать время сохранения карточки QSO» — в новой карточке идут часы."),
        tr("Справка → Морзе → «Текст»: любой текст, латиница и кириллица, звучит азбукой Морзе с выбранной скоростью, звучащий знак подсвечивается, внизу — лента с кодами."),
        tr("На кнопке «Добавить QSO» — подсказка Ctrl+N · F9, в «Журнале связей» — включена ли синхронизация с Google."),
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
