package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.9.0; the keys of the scale differ: ⌘ on the Mac, Ctrl elsewhere and on the phone). */
    fun items(desktop: Boolean): List<String> = listOf(
        if (desktop) tr("Масштаб интерфейса (Настройки → Интерфейс, меню «Вид»): 100, 85 и 70% — компактнее кнопки, поля и отступы, больше журнала и карточки в окне. Клавиши ⌘+ / ⌘− (Ctrl на Windows и Linux).")
        else tr("Масштаб интерфейса (Настройки → Интерфейс): 100, 70 и 50%. При 50% карточка связи раскладывается в две колонки. С клавиатуры — Ctrl + / Ctrl −."),
        tr("Нажатие на свой позывной под логотипом показывает настройки станции: локатор, QTH, мощность, трансивер, антенну."),
        tr("Журнал показывает связи выбранной станции: выбрали другую станцию — лента переключилась на неё («Станция: все» — снова все связи)."),
        tr("«Записывать только имя» (Настройки → Справочники и онлайн-журналы → Данные о корреспонденте): «Иван» вместо «Иван Петрович Демонстров»."),
        tr("Справочники не дали QTH — «Указать QTH на карте» в карточке: поиск населённого пункта по названию, локатор по точке на карте."),
        tr("Фото корреспондента с QRZ.ru и QRZ.com в карточке, пока она открыта (никуда не сохраняется)."),
        if (desktop) tr("OmniRig: частота из лога теперь ставится и на трансиверах, у которых OmniRig меняет только VFO A / VFO B. Префикс R1Y — Мурманская область.")
        else tr("Префикс R1Y — Мурманская область."),
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
