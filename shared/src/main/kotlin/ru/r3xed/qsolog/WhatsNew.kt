package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release (2.7.0 on the phone and the computer). */
    fun items(desktop: Boolean): List<String> = listOfNotNull(
        tr("Ключ на внешней клавиатуре: в карточке QSO с видом CW клавиши (левый и правый Ctrl или [ и ]) работают прямым или ямбическим ключом, а под кнопками появляется лента переданных знаков; звук через динамик, скорость — PageUp и PageDown."),
        if (desktop) null else tr("Своя клавиатура в карточке QSO с видом CW: над буквами ряд F1–F8, скорость − и + и «Стоп»."),
        tr("Icom по сети (IC-705, IC-7610, IC-7760, IC-9700…): вход по имени и паролю пользователя трансивера, как в RS-BA1 и wfview, — в локальной сети и через точку доступа IC-705."),
        tr("LoTW без TQSL: загрузите сертификат .p12 — связи подписываются и уходят в LoTW по кнопке, а «Подтверждения LoTW» отмечают подтверждённые связи зелёным."),
        tr("Справочники HamQTH и QRZCQ: если QRZ.ru нашёл позывной без QTH или координат, их ищут дальше по списку."),
        if (desktop) tr("CAT через OmniRig (Windows); Kenwood TS-590S, TS-890S, TS-990S работают (линия RTS), программа сама подбирает скорость порта; чёткая манипуляция телеграфом в Windows.")
        else tr("Kenwood TS-590S, TS-890S, TS-990S работают (линия RTS), программа сама подбирает скорость порта."),
        tr("Импорт ADIF с выбором кодировки (UTF-8, Windows-1251, KOI8-R, CP866) и просмотром первых записей; проверка новой версии при запуске; время связей из JTDX больше не сдвигается на часовой пояс."),
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
