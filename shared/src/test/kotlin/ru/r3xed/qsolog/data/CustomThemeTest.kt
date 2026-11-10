package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals

class CustomThemeTest {
    @Test
    fun roundTrip() {
        val t = CustomTheme.base(true).with(CustomTheme.Role.ACCENT, 0xFF43A047)
        assertEquals(t, CustomTheme.decode(t.encode()))
        assertEquals(0xFF43A047, CustomTheme.decode(t.encode())[CustomTheme.Role.ACCENT])
        assertEquals(CustomTheme.base(false), CustomTheme.decode(""))
        assertEquals(0xFF0A5C8A, CustomTheme.parseHex("#0a5c8a"))
        assertEquals(null, CustomTheme.parseHex("xyz"))
        assertEquals("#0A5C8A", CustomTheme.hex(0xFF0A5C8A))
        // A pale accent gets dark text, a deep one white.
        assertEquals(0xFF14202B, CustomTheme.base(false).with(CustomTheme.Role.ACCENT, 0xFFFFE0B2)[CustomTheme.Role.ON_ACCENT])
        assertEquals(0xFFFFFFFF, CustomTheme.base(false).with(CustomTheme.Role.ACCENT, 0xFF1A237E)[CustomTheme.Role.ON_ACCENT])
    }
}
