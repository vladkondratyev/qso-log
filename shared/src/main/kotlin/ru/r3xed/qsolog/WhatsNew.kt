package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release: 2.9.7 — the users' requests after 2.9.6. */
    fun items(desktop: Boolean): List<String> = listOfNotNull(
        tr("Имя файла при экспорте: позывной, дата и время — UA3DEMO_2026-11-15_164059.adi. В папке файлы идут по порядку, несколько выгрузок за день не путаются."),
        tr("Карточка, открытая из WSJT-X, JTDX или MSHV, сразу в FT8 (или в виде программы), даже если трансивер в обычном USB."),
        tr("Kenwood TS-590/890/990: цифровой режим (DA1) больше не перестаёт опрашиваться после случайного ответа «?;» — кнопка FT8 на пульте горит."),
        tr("Lab599 TX-500 и TX-500MP в списке трансиверов (протокол TS-2000)."),
        tr("Пульт трансивера: строка под частотой больше не дёргается при смене показаний S-метра."),
        if (desktop) null else tr("LoTW: сертификаты .p12 из новых версий TQSL открываются и на старых Android (ошибка «PBKDF2 SecretKeyFactory not available»)."),
        tr("Исправлено закрытие программы при наборе позывного вида «A/P»."),
        if (desktop) null else tr("Если приложение закрылось с ошибкой, при следующем запуске оно предложит отправить отчёт разработчику."),
        tr("Новое: QSO-LOG Web — версия в браузере без установки, работает и без интернета: qso-log.ru/app"),
    )
}

/** A new GitHub issue with the app's version and the device filled in — no personal data, nothing from the log. */
object ProblemReport {
    /** [crash] — the error the app closed with (see [crashText]): it goes under "Что случилось". */
    fun url(appVersion: String, platform: String, device: String, crash: String? = null): String {
        val body = buildString {
            appendLine("**${tr("Что случилось")}:**")
            if (crash != null) {
                appendLine(tr("Приложение закрылось с ошибкой:"))
                appendLine("```")
                appendLine(crash)
                appendLine("```")
            }
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

    /**
     * The error as text for the report: the exception and its causes with the app's own lines of the stack (and the
     * first lines of each) — short enough for a link. No data of the log: only class names and line numbers.
     */
    fun crashText(t: Throwable): String {
        val sb = StringBuilder()
        var e: Throwable? = t
        var depth = 0
        while (e != null && depth < 4) {
            if (depth > 0) sb.append("Caused by: ")
            sb.append(e.javaClass.name).append(": ").append(e.message.orEmpty().take(200)).append('\n')
            val frames = e.stackTrace
            frames.withIndex().filter { (i, f) -> i < 6 || f.className.startsWith("ru.r3xed") }.take(25).forEach { (_, f) ->
                sb.append("  at ").append(f.className).append('.').append(f.methodName).append('(').append(f.fileName).append(':').append(f.lineNumber).append(")\n")
            }
            e = e.cause?.takeIf { it !== e }
            depth++
        }
        return sb.toString().take(4000).trimEnd()
    }
}
