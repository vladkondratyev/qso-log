package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallbooksTest {
    @Test
    fun hamqthCallbookAnswer() {
        // As in HamQTH's API v2 guide, with a made-up station.
        val json = """{"callsign": "OK9DEMO", "nick": "Petr", "name": "Petr Demo", "qth": "Neratovice", "dxcc": 503,
            "country": "Czech Republic", "continent": "EU", "cq": 15, "itu": 28,
            "location": {"lat": 50.25, "lon": 14.51, "grid": "JO70GG"},
            "address": {"name": "Petr Demo", "street": ["Demo 1"], "city": "Neratovice", "zip": "27711", "country": "Czech Republic", "dxcc": 503},
            "region": {"district": "BME", "usState": null, "usCounty": null, "oblast": null, "dok": null}}"""
        val i = HamQthBook.parse(json, "ok9demo")!!
        assertEquals("OK9DEMO", i.call)
        assertEquals("Petr Demo", i.name)
        assertEquals("Neratovice", i.city)
        assertEquals("JO70gg", i.locator)
        assertEquals(50.25, i.lat)
        assertEquals("15", i.cqZone)
        assertEquals("BME", i.region)
    }

    @Test
    fun qrzcqAnswerAndErrors() {
        val xml = """<?xml version="1.0" encoding="utf-8" ?>
<QRZCQDatabase version="1.00" xmlns="http://qrzcq.com">
	<Callsign>
		<call>DL9DEMO</call><name>Hans Demo</name><qth>Buchholz, near Hamburg</qth><city>Buchholz</city>
		<country>Germany</country><state>NDS</state><locator>JO43WH</locator>
		<latitude>53.3199308</latitude><longitude>9.8597973</longitude><itu>28</itu><cq>14</cq>
	</Callsign>
	<Session><Key>abc</Key><Count>1</Count></Session>
</QRZCQDatabase>"""
        val r = QrzCq.parse(xml)
        assertNull(r.error)
        val i = QrzCq.toInfo(r.fields, "DL9DEMO")!!
        assertEquals("Buchholz", i.city)
        assertEquals("JO43wh", i.locator)
        assertEquals(9.8597973, i.lon)
        assertEquals("14", i.cqZone)
        val nf = QrzCq.parse("<QRZCQDatabase><Session><Error>Not found: N0CALL</Error></Session></QRZCQDatabase>")
        assertEquals("Not found: N0CALL", nf.error)
    }

    @Test
    fun aSecondCallbookFillsTheMissingQth() {
        val qrz = QrzInfo("R0DEMO", "Иван", "", "", "Russia", "", null, null, source = "qrzru")
        assertTrue(!qrz.complete)
        val hq = QrzInfo("R0DEMO", "Ivan", "", "Kaluga", "Russia", "KO84dm", null, null, cqZone = "16")
        val m = qrz.fillFrom(hq)
        assertEquals("Иван", m.name)
        assertEquals("Kaluga", m.city)
        assertEquals("KO84dm", m.locator)
        assertEquals("16", m.cqZone)
        assertEquals("qrzru", m.source)
        assertTrue(m.complete)
        // Coordinates of the first answer stay; the second's locator does not replace them.
        val withPos = qrz.copy(lat = 54.5, lon = 36.3).fillFrom(hq)
        assertEquals(54.5, withPos.lat)
        assertEquals("", withPos.locator)
    }
}
