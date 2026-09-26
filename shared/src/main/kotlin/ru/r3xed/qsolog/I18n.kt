package ru.r3xed.qsolog

import java.util.Locale

/** Interface languages. [SYSTEM] follows the OS: Russian for ru/uk/be, English otherwise. */
enum class Lang(val code: String, val title: String) {
    SYSTEM("system", "Как в системе / System"),
    RU("ru", "Русский"),
    EN("en", "English"),
}

/**
 * The interface language. Texts are written in Russian right in the code and passed through [tr];
 * the English ones are in [EN] (I18nEn.kt), keyed by the Russian text. A missing translation shows the Russian text.
 */
object I18n {
    /** The language picked in the settings (SYSTEM by default). */
    @Volatile var chosen: Lang = Lang.SYSTEM
        set(v) { field = v; current = resolve(v) }

    /** RU or EN — what [tr] uses now. */
    @Volatile var current: Lang = resolve(Lang.SYSTEM)
        private set

    private fun resolve(l: Lang): Lang = when (l) {
        Lang.SYSTEM -> if (Locale.getDefault().language in setOf("ru", "uk", "be")) Lang.RU else Lang.EN
        else -> l
    }

    fun fromCode(code: String?): Lang = Lang.entries.firstOrNull { it.code == code } ?: Lang.SYSTEM
}

/** The Russian [ru] text in the interface language. */
fun tr(ru: String): String = if (I18n.current == Lang.EN) EN[ru] ?: ru else ru

/** [ru] with %s placeholders, translated, then filled with [args]. */
fun tr(ru: String, vararg args: Any?): String = String.format(Locale.ROOT, tr(ru), *args)
