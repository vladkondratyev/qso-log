package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release: 3.0 — a large log in less memory (the Windows "Java heap space") and GitHub #12. */
    fun items(desktop: Boolean): List<String> = listOfNotNull(
        if (desktop) tr("Большой журнал (100 000 связей и больше) занимает в несколько раз меньше памяти. Программа больше не закрывается с ошибкой «Java heap space» на Windows и может взять до половины памяти компьютера.")
        else tr("Большой журнал (100 000 связей и больше) занимает в несколько раз меньше памяти телефона."),
        if (desktop) null else tr("После правки связи журнал остаётся на том же месте и не прыгает в начало списка."),
        tr("Поиск по журналу быстрее: журнал больше не читается из базы второй раз."),
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
