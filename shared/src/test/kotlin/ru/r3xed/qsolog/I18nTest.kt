package ru.r3xed.qsolog

import java.io.File
import ru.r3xed.qsolog.data.Reference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    @AfterTest
    fun back() {
        I18n.chosen = Lang.SYSTEM
    }

    @Test
    fun switchesLanguage() {
        I18n.chosen = Lang.EN
        assertEquals("Save", tr("Сохранить"))
        assertEquals("QSO with R0DEMO saved", tr("Связь с %s записана", "R0DEMO"))
        // No translation: the Russian text is shown.
        assertEquals("Нет такой строки", tr("Нет такой строки"))
        I18n.chosen = Lang.RU
        assertEquals("Сохранить", tr("Сохранить"))
        assertEquals("Связь с R0DEMO записана", tr("Связь с %s записана", "R0DEMO"))
    }

    /** Every tr("…") in the Android and desktop code has an English text with the same placeholders. */
    @Test
    fun everyTextIsTranslated() {
        val root = File("..").canonicalFile
        val call = Regex("""\btr\("((?:[^"\\]|\\.)*)"""")
        val missing = mutableListOf<String>()
        listOf("app/src/main/java", "desktop/src/main/kotlin", "shared/src/main/kotlin").forEach { dir ->
            File(root, dir).walk().filter { it.extension == "kt" && it.name != "I18nEn.kt" }.forEach { f ->
                call.findAll(f.readText()).forEach { m ->
                    val key = m.groupValues[1].replace("\\\"", "\"").replace("\\$", "$").replace("\\\\", "\\")
                    if (!key.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' }) return@forEach
                    val en = EN[key]
                    if (en == null) missing += "${f.name}: $key"
                    else assertEquals(key.split("%s").size, en.split("%s").size, "placeholders: $key")
                }
            }
        }
        // Reference tables are shown through tr(value): the strings of those lists need translations too
        // (the Cyrillic Morse letters and the Russian spelling words are content and stay as they are).
        val shown = buildList {
            Reference.BANDS_RU.forEach { add(it.name); add(it.range); add(it.note) }
            Reference.BAND_PLAN.forEach { (band, segs) -> add(band); segs.forEach { add(it.use) } }
            Reference.ACTIVITY.forEach { add(it.first); add(it.second) }
            Reference.NCDXF.forEach { add(it.second) }
            listOf(Reference.Q_CODES, Reference.CW_ABBR, Reference.RST_R, Reference.RST_S, Reference.RST_T,
                Reference.SFI, Reference.A_INDEX, Reference.K_INDEX).forEach { l -> l.forEach { add(it.second) } }
            Reference.RU_ZONES.forEach { add(it.second) }
        }
        shown.filter { k -> k.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' } && EN[k] == null }.forEach { missing += "Reference: $it" }
        assertTrue(missing.isEmpty(), "No English text for:\n" + missing.distinct().joinToString("\n"))
    }
}
