package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /**
     * Points of this release: 2.9.5 — the users' requests (SWL, the scale, filling in the stations' data, bulk delete,
     * the HAMLOG mark, the rig panel, CW on Windows), then 2.9.4's HAMLOG again.
     */
    fun items(desktop: Boolean): List<String> = listOfNotNull(
        tr("Наблюдатели (SWL): станцию в «Моих станциях» можно отметить как наблюдательскую — номер наблюдателя вместо позывного, «Работал с» на месте «RST принят», SWL = Y в ADIF, SWL-рапорт на QSL-наклейке; в онлайн-журналы наблюдения не уходят."),
        if (desktop) tr("Масштаб интерфейса от 50% до 150% с шагом 10%: ⌘/Ctrl + и −, Ctrl с колесом мыши, меню «Вид» — крупнее становятся и таблица, и карточка.")
        else tr("Масштаб интерфейса от 50% до 150% с шагом 10% (Настройки → Интерфейс, Ctrl + и − с клавиатуры)."),
        tr("«Дозаполнить данные корреспондентов»: имя, QTH и локатор для связей, записанных без интернета, — для выбранных или всего журнала. Связи, для которых справочник не ответил, дозаполняются сами, когда интернет вернётся."),
        if (desktop) tr("Удаление нескольких связей: выберите их и нажмите корзину (или Delete); в таблице строки выбираются Ctrl-щелчком и Shift-щелчком.")
        else tr("Удаление нескольких связей: выберите их долгим нажатием и нажмите корзину."),
        tr("Связи, выгруженные в HAMLOG, отмечены фиолетовой полоской слева (цвет меняется в «Своей теме»)."),
        if (desktop) tr("Пульт трансивера: щелчок по цифре частоты — выше на этот разряд, правой кнопкой — ниже, колесо над цифрой — тем же шагом; показывается частота, на которую встал трансивер; шкала диапазона больше не пропадает, кнопки не уезжают.")
        else tr("Пульт трансивера показывает частоту, на которую встал трансивер; шкала диапазона больше не пропадает."),
        if (desktop) tr("Телеграф через COM-порт компьютера под Windows: более чёткая манипуляция; точность каждой передачи записывается в файл cw-timing.txt.") else null,
        tr("Исправлено: экран «Прохождение» мог закрываться с ошибкой цвета."),
        tr("HAMLOG.online: связи уходят в облачный журнал HAMLOG — по кнопке из журнала или сразу после записи. Подключение — кодом на сайте HAMLOG (Настройки → Онлайн-журналы), без паролей."),
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
