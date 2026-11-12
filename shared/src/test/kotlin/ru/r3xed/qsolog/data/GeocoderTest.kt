package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals

class GeocoderTest {
    @Test
    fun parsesPlaces() {
        val json = """[{"place_id":1,"lat":"54.5136","lon":"36.2612","name":"Калуга","display_name":"Калуга, городской округ Калуга, Калужская область, Россия","address":{"city":"Калуга","country":"Россия"}},
            {"lat":"55.1","lon":"37.2","name":"","display_name":"Демино, Московская область, Россия","address":{"village":"Демино"}}]"""
        val p = Geocoder.parse(json)
        assertEquals(2, p.size)
        assertEquals("Калуга", p[0].name)
        assertEquals("KO84dm", p[0].locator)
        assertEquals("Демино", p[1].name)
    }

    @Test
    fun scaleSteps() {
        assertEquals(UiScale.S90, UiScale.smaller(UiScale.S100))
        assertEquals(UiScale.S50, UiScale.smaller(UiScale.S50))
        assertEquals(UiScale.S150, UiScale.bigger(UiScale.S150))
        assertEquals(UiScale.S110, UiScale.bigger(UiScale.S100))
        assertEquals(UiScale.S90, UiScale.of(85))
        assertEquals(UiScale.S50, UiScale.of(50))
        assertEquals(UiScale.S100, UiScale.of(100))
        assertEquals(0.65f, UiScale.S50.text, 0.001f)
        assertEquals(1.3f, UiScale.S130.text, 0.001f)
    }

    @Test
    fun murmanskR1Y() {
        assertEquals("MU", RussianRegions.of("R1YDEMO")?.rda)
    }
}
