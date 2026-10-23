package ru.r3xed.qsolog.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    @Test
    fun qrzComXmlAnswer() {
        // As in QRZ.com's XML specification, with a made-up station.
        val xml = """<?xml version="1.0" ?><QRZDatabase version="1.34"><Callsign><call>W1DEMO</call><fname>John</fname>
            <name>Demo</name><addr2>Newington</addr2><state>CT</state><country>United States</country><lat>41.714775</lat>
            <lon>-72.727260</lon><grid>FN31pr</grid><cqzone>5</cqzone><ituzone>8</ituzone></Callsign>
            <Session><Key>2331uf894c4bd29f3923f3bacf02c532d7bd9</Key><Count>123</Count><SubExp>Wed Jan 1 12:34:03 2031</SubExp></Session></QRZDatabase>"""
        val r = QrzCq.parse(xml)
        val i = QrzComXml.toInfo(r.fields, "w1demo")!!
        assertEquals("W1DEMO", i.call)
        assertEquals("John Demo", i.fullName)
        assertEquals("Newington", i.city)
        assertEquals("FN31pr", i.locator)
        assertEquals(41.714775, i.lat)
        assertEquals("CT", i.region)
        assertEquals("5", i.cqZone)
        assertTrue(QrzComXml.subscribed(r.session["SubExp"].orEmpty()))
        assertFalse(QrzComXml.subscribed("non-subscriber"))
        // Without the subscription: the name and the country only.
        val free = QrzComXml.toInfo(QrzCq.parse("<QRZDatabase><Callsign><call>W1DEMO</call><fname>John</fname><country>United States</country></Callsign></QRZDatabase>").fields, "W1DEMO")!!
        assertEquals("", free.locator)
        assertEquals(null, free.lat)
        assertFalse(free.complete)
    }

    private fun info(call: String, city: String = "", locator: String = "", name: String = "") =
        QrzInfo(call = call, name = name, surname = "", city = city, country = "", locator = locator, lat = null, lon = null)

    @Test
    fun parallelAnswersGoInTheBooksOrder() = runBlocking {
        val slowFirst: suspend () -> QrzInfo? = { delay(300); info("R0DEMO", name = "Ivan") }
        val fastSecond: suspend () -> QrzInfo? = { info("R0DEMO", city = "Moscow", locator = "KO85ts", name = "John") }
        val started = System.currentTimeMillis()
        val i = lookupAll(listOf(SearchSource.QRZ_RU to slowFirst, SearchSource.QRZ_COM_XML to fastSecond, SearchSource.QRZ_COM to { delay(300); info("R0DEMO", city = "Other") }))!!
        // Asked at once: two slow books take one delay, not two.
        assertTrue(System.currentTimeMillis() - started < 550)
        assertEquals("Ivan", i.name) // the first book's, though it answered last
        assertEquals("Moscow", i.city) // filled in by the next one
        assertEquals("qrzru", i.source)
    }

    @Test
    fun failedAndUnknownBooks() = runBlocking {
        val broken: suspend () -> QrzInfo? = { throw QrzException(0, "down") }
        assertEquals("Kyiv", lookupAll(listOf(SearchSource.QRZ_RU to broken, SearchSource.HAMQTH_BOOK to { info("UR0DEMO", city = "Kyiv") }))!!.city)
        assertNull(lookupAll(listOf(SearchSource.QRZ_RU to broken, SearchSource.QRZCQ to { null })))
        assertEquals("down", assertFailsWith<QrzException> { lookupAll(listOf(SearchSource.QRZ_RU to broken)) }.message)
    }
}
