package ru.r3xed.qsolog.data

/**
 * The user's own colour theme ("Своя" in the settings): every colour the app paints with, as ARGB. The first five
 * ([BASIC]) are what most people change; the rest are under "Дополнительно". [dark] tells the system bars and the
 * parts not listed here (scrims, the map's tiles) which way to go.
 */
data class CustomTheme(val dark: Boolean = false, val colors: Map<Role, Long> = LIGHT) {
    operator fun get(r: Role): Long = colors[r] ?: (if (dark) DARK else LIGHT).getValue(r)

    /**
     * [r] set to [argb]. A fill that has text of its own (the accent, groups, the "worked before" highlight) gets that
     * text in black or white, whichever reads better on it; the text can still be changed after, under "Дополнительно".
     */
    fun with(r: Role, argb: Long): CustomTheme {
        val c = argb or 0xFF000000
        val ink = when (r) {
            Role.ACCENT -> Role.ON_ACCENT
            Role.GROUP -> Role.GROUP_TEXT
            Role.HIGHLIGHT -> Role.HIGHLIGHT_TEXT
            else -> null
        }
        return copy(colors = colors + (r to c) + listOfNotNull(ink?.let { it to readableOn(c) }))
    }

    /** "background=FFFFFFFF;text=FF14202B;…" with "dark" first. */
    fun encode(): String = (listOf("dark=$dark") + Role.entries.map { "${it.key}=%08X".format(get(it)) }).joinToString(";")

    enum class Role(val key: String, val ru: String) {
        BACKGROUND("background", "Фон"), // no-tr
        TEXT("text", "Текст"), // no-tr
        ACCENT("accent", "Акцент: кнопки, выбранное"), // no-tr
        GROUP("group", "Фон групп и карточек"), // no-tr
        LINE("line", "Обводки и линии"), // no-tr
        MUTED("muted", "Второстепенный текст"), // no-tr
        FIELD("field", "Фон полей ввода"), // no-tr
        ON_ACCENT("onAccent", "Текст на акценте"), // no-tr
        GROUP_TEXT("groupText", "Текст в группах"), // no-tr
        HIGHLIGHT("highlight", "Фон «уже работали»"), // no-tr
        HIGHLIGHT_LINE("highlightLine", "Обводка «уже работали», повтор на диапазоне"), // no-tr
        HIGHLIGHT_TEXT("highlightText", "Текст «уже работали»"), // no-tr
        OK("ok", "Успех, новый диапазон"), // no-tr
        ERROR("error", "Ошибка, повтор"), // no-tr
        FOCUS("focus", "Контрастная обводка поля ввода"), // no-tr
    }

    companion object {
        val BASIC = listOf(Role.BACKGROUND, Role.TEXT, Role.ACCENT, Role.GROUP, Role.LINE)

        /** The app's light theme. */
        val LIGHT: Map<Role, Long> = mapOf(
            Role.BACKGROUND to 0xFFFFFFFF, Role.TEXT to 0xFF14202B, Role.ACCENT to 0xFF0A5C8A, Role.GROUP to 0xFFD3E8F5,
            Role.LINE to 0xFFDCE2E9, Role.MUTED to 0xFF4A5A6D, Role.FIELD to 0xFFF1F4F7, Role.ON_ACCENT to 0xFFFFFFFF,
            Role.GROUP_TEXT to 0xFF06324D, Role.HIGHLIGHT to 0xFFFFF1C9, Role.HIGHLIGHT_LINE to 0xFFE0A800,
            Role.HIGHLIGHT_TEXT to 0xFF4A3700, Role.OK to 0xFF1E7B45, Role.ERROR to 0xFFB3261E, Role.FOCUS to 0xFFFF6D00,
        )

        /** The app's dark theme. */
        val DARK: Map<Role, Long> = mapOf(
            Role.BACKGROUND to 0xFF10171E, Role.TEXT to 0xFFE8EEF4, Role.ACCENT to 0xFF6BBEEF, Role.GROUP to 0xFF12405E,
            Role.LINE to 0xFF26323D, Role.MUTED to 0xFFA6B5C4, Role.FIELD to 0xFF1C2630, Role.ON_ACCENT to 0xFF06121B,
            Role.GROUP_TEXT to 0xFFD3E8F5, Role.HIGHLIGHT to 0xFF3A2F0B, Role.HIGHLIGHT_LINE to 0xFFE0A800,
            Role.HIGHLIGHT_TEXT to 0xFFFFE08A, Role.OK to 0xFF6BD49A, Role.ERROR to 0xFFFF8A80, Role.FOCUS to 0xFFFFC400,
        )

        fun base(dark: Boolean) = CustomTheme(dark, if (dark) DARK else LIGHT)

        fun decode(s: String?): CustomTheme {
            if (s.isNullOrBlank()) return base(false)
            val m = s.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            val dark = m["dark"] == "true"
            val colors = Role.entries.associateWith { r -> m[r.key]?.toLongOrNull(16) ?: (if (dark) DARK else LIGHT).getValue(r) }
            return CustomTheme(dark, colors)
        }

        /** "#0A5C8A", "0a5c8a", "FF0A5C8A" → ARGB; null when it is not a colour. */
        fun parseHex(s: String): Long? {
            val h = s.trim().removePrefix("#")
            if (!h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            return when (h.length) {
                6 -> 0xFF000000 or h.toLong(16)
                8 -> h.toLong(16)
                else -> null
            }
        }

        fun hex(argb: Long) = "#%06X".format(argb and 0xFFFFFF)

        /** Black or near-white text for a fill [argb]: by its relative luminance (WCAG). */
        fun readableOn(argb: Long): Long {
            fun ch(shift: Int): Double {
                val v = ((argb shr shift) and 0xFF) / 255.0
                return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            }
            val l = 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
            return if (l > 0.179) 0xFF14202B else 0xFFFFFFFF
        }

        /** Ready colours for the picker: greys, then hues light to dark. */
        val SWATCHES: List<Long> = listOf(
            0xFFFFFFFF, 0xFFF1F4F7, 0xFFDCE2E9, 0xFFB9C4D0, 0xFF7A8A9A, 0xFF4A5A6D, 0xFF26323D, 0xFF14202B, 0xFF10171E, 0xFF000000,
            0xFFFFCDD2, 0xFFF8BBD0, 0xFFE1BEE7, 0xFFC5CAE9, 0xFFBBDEFB, 0xFFB2EBF2, 0xFFC8E6C9, 0xFFF0F4C3, 0xFFFFF1C9, 0xFFFFE0B2,
            0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1, 0xFF43A047, 0xFFC0CA33, 0xFFFFC400, 0xFFFF6D00,
            0xFFB3261E, 0xFF880E4F, 0xFF4A148C, 0xFF1A237E, 0xFF0A5C8A, 0xFF006064, 0xFF1E7B45, 0xFF827717, 0xFF4A3700, 0xFFBF360C,
        )
    }
}
