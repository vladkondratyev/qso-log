package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.0.0 on the phone and the computer): the computer also gets what the phone had since 1.18.0. */
    fun items(desktop: Boolean): List<String> = listOf(
        tr("CAT — связь с трансивером: частота, диапазон и вид связи сами попадают в новую карточку QSO, по желанию — и S-метр в отправленный RST. В карточке видно, что сейчас на трансивере."),
        tr("ICOM по протоколу CI-V (IC-705, IC-7300, IC-7610, IC-9700 и другие) и совместимые Xiegu; Kenwood, Elecraft, Yaesu (новый CAT и FT-817/857/897), а также любой трансивер через Hamlib rigctld."),
        if (desktop) tr("Подключение на компьютере: COM-порт (USB-кабель трансивера или интерфейс CAT/CI-V) или сеть. Настройки → «CAT — трансивер».")
        else tr("Подключение на телефоне: USB-кабель через переходник OTG, Bluetooth (например, IC-705) или сеть. Настройки → «CAT — трансивер»."),
    ) + if (!desktop) emptyList() else listOf(
        tr("Внешняя клавиатура как в классических логгерах: Пробел ходит по полям позывной → RST → RST (в контесте — сразу к номеру), Enter записывает связь из любого поля, Alt+W или Ctrl+Esc очищает карточку, Esc закрывает её."),
        tr("Удаление с клавиатуры: Delete или Ctrl+D на записи журнала или в открытой карточке, Enter в окне подтверждения — удалить."),
        tr("Меню ⋮ разложено по группам, CONTEST MODE включается прямо из меню, «Экспорт и импорт…» — ADIF, CSV и ЕРМАК/Cabrillo в одном окне."),
        tr("Справка: первая вкладка «Клавиши», в «Морзе» — «Текст»: любой текст звучит азбукой Морзе с подсветкой звучащего знака."),
        tr("«Фиксировать время сохранения карточки QSO»: в новой карточке идут часы. ⌫ в поле позывного и поиска стирает его целиком."),
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
