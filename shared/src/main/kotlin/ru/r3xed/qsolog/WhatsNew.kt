package ru.r3xed.qsolog

import java.net.URLEncoder

/**
 * "Что нового": shown once after an update (not on the first install), and from the settings. Only the newest
 * version's points, in plain words; the full history is on the releases page.
 */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/vladkondratyev/qso-log/releases"

    /** Points of this release: 3.1 — the users' requests after 3.0 (photo, panes, contest participants) and the log. */
    fun items(desktop: Boolean): List<String> = listOfNotNull(
        tr("Фото в старой связи: в сохранённой карточке без фото есть кнопка «Загрузить фото» — фото берётся из справочников и хранится с этой связью."),
        tr("Фото можно сохранить в полном размере: откройте его крупно и нажмите «Сохранить фото»."),
        if (desktop) tr("Ширину журнала рядом с карточкой можно менять: перетащите линию между ними, двойной щелчок возвращает как было.")
        else tr("Планшет в горизонтальном положении: журнал остаётся слева от карточки, линию между ними можно перетаскивать."),
        tr("Соревнования: список участников по алфавиту (станции этого соревнования из журнала, в том числе прошлых лет). Клик подставляет позывной и постоянную часть контрольного номера, повторы подсвечены красным."),
        tr("Первая связь с каждой страной отмечена звёздочкой в журнале."),
        tr("Нажмите на дату над связями — откроется календарь, журнал перейдёт к выбранному дню."),
        if (desktop) null else tr("Плотный журнал (меню ⋮): одна строка на связь, на экране вдвое больше связей."),
        tr("С версии 3.0: большой журнал занимает в несколько раз меньше памяти."),
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
