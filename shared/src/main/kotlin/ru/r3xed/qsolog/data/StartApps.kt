package ru.r3xed.qsolog.data

/**
 * A program started together with QSO-LOG ("Дополнительные настройки → Автозапуск"). On Android [target] is the app's
 * package; on the computer the program's path (or a command found on PATH, like rigctld), with [args] and the option
 * to close it when QSO-LOG closes.
 */
data class StartApp(
    val target: String,
    val title: String,
    val args: String = "",
    val closeOnExit: Boolean = false,
    val enabled: Boolean = true,
)

object StartApps {
    /** One program per line: target, title, arguments, flags — tab-separated. */
    fun encode(list: List<StartApp>): String = list.joinToString("\n") { a ->
        listOf(a.target, a.title, a.args, (if (a.closeOnExit) "c" else "") + (if (a.enabled) "" else "off"))
            .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
    }

    fun decode(s: String?): List<StartApp> = s.orEmpty().lines().mapNotNull { line ->
        val p = line.split('\t')
        val target = p.getOrNull(0)?.trim().orEmpty()
        if (target.isEmpty()) return@mapNotNull null
        val flags = p.getOrNull(3).orEmpty()
        StartApp(target, p.getOrNull(1)?.ifBlank { null } ?: target, p.getOrNull(2).orEmpty(), "c" in flags, !flags.contains("off"))
    }

    /**
     * The arguments of [args] as the shell would split them: spaces separate, "…" and '…' keep spaces inside
     * ("C:\Program Files\…" stays one).
     */
    fun splitArgs(args: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var any = false
        for (ch in args) {
            when {
                quote != null && ch == quote -> quote = null
                quote == null && (ch == '"' || ch == '\'') -> { quote = ch; any = true }
                quote == null && ch.isWhitespace() -> { if (any || cur.isNotEmpty()) out += cur.toString(); cur.clear(); any = false }
                else -> cur.append(ch)
            }
        }
        if (any || cur.isNotEmpty()) out += cur.toString()
        return out
    }

    /**
     * Hamlib rigctld holding the radio's port so that JTDX (Radio → "Hamlib NET rigctl") and QSO-LOG (CAT → rigctld) both
     * reach the radio: "-m <model> -r <port> -s <speed>". The model numbers are listed by "rigctl -l".
     */
    fun rigctld(model: Int, port: String, baud: Int): StartApp =
        StartApp("rigctld", "Hamlib rigctld", "-m $model -r $port -s $baud -T 127.0.0.1 -t 4532", closeOnExit = true)
}
