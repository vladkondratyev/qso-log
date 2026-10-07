package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pages built like www.qrz.com/db/CALL (October 2026), with made-up data. */
class QrzComTest {
    private fun page(nameBlock: String, details: String) = """
        <html><head><title>DL0DEMO - Callsign Lookup by QRZ Ham Radio</title></head><body>
        <div id="calldata"><!-- begin calldata -->
        <table id="jq" width="100%" cellpadding="0" cellspacing="0">
        <tr><td id="csdata" valign="top">
        <span class="csignm hamcall">DL0DEMO</span> <span class="ml4 cland">
            <span class="ptr" onclick="window.location='https://www.qrz.com/atlas?dxcc=230'"><img id="flg" src="https://static.qrz.com/static/flags-iso/flat/32/DE.png"  alt="Germany flag" title="DX Atlas for: Germany"/> <span style="position:relative;top:-8px;">Germany</span></span></span><br />
        $nameBlock
        <p class="mt05 f9" style="font-weight:normal"><b>QSL:</b> VIA BUREAU</p>
        <p class="m0 f8"><span class="green">Ham Member</span> <span class="ml1">Lookups: 42</span></p>
        	</td>
        	<td id="ppic" valign="top"><img src="https://example.org/pic.jpg" id="mypic" /></td>
            </tr>
        </table>
            </div><!-- end calldata -->
        <div id="detbox"><table id="dt">
        $details
        </table></div>
        </body></html>
    """.trimIndent()

    private val loggedInName = """<p class="m0" style="color: #666; font-weight: normal; font-size: 17px"><span style="color: black; font-weight: bold">Hans-Peter Demomann</span><span class="csgnl none">, DL0DEMO</span><br />Beispielweg 3 a<br/>12345 Demostadt<br/>Germany</p>"""

    private val details = """
        <tr><td class="dh">Lookups</td><td class="di">42 <span style="margin-left:1em;font-size:0.7em;">(50)</span></td></tr>
        <tr><td class="dh">Latitude</td><td class="di">51.831667 <span class="gm">(51&deg; 49' 54'' N)</span></td></tr>
        <tr><td class="dh">Longitude</td><td class="di">-9.949579 <span class="gm">(9&deg; 56' 58'' W)</span></td></tr>
        <tr><td class="dh">Grid Square</td><td class="di">io41XT</td></tr>
        <tr><td class="dh">ITU Zone</td><td class="di">28</td></tr>
        <tr><td class="dh">CQ Zone</td><td class="di">14</td></tr>
        <tr><td class="dh">QSL Info</td><td class="dw">VIA BUREAU</td></tr>
    """.trimIndent()

    @Test
    fun loggedInPage() {
        val html = page(loggedInName, details)
        assertFalse(QrzCom.needsLogin(html))
        val i = QrzCom.parse(html, "dl0demo")!!
        assertEquals("DL0DEMO", i.call)
        assertEquals("Hans-Peter Demomann", i.fullName)
        assertEquals("Demostadt", i.city)
        assertEquals("", i.region)
        assertEquals("Germany", i.country)
        assertEquals("IO41xt", i.locator)
        assertEquals(51.831667, i.lat)
        assertEquals(-9.949579, i.lon)
        assertEquals("14", i.cqZone)
        assertEquals("28", i.ituZone)
    }

    /** A US-style town line: "TOWN, ST 12345" gives the town and the state. */
    @Test
    fun townWithState() {
        val name = """<p class="m0" style="color: #666"><span style="color: black; font-weight: bold">DEMO RADIO CLUB</span><span class="csgnl none">, W0DEMO</span><br />1 MAIN ST<br/>DEMOVILLE, CT 06111<br/>Germany</p>"""
        val i = QrzCom.parse(page(name, ""), "W0DEMO")!!
        assertEquals("DEMO RADIO CLUB", i.name)
        assertEquals("DEMOVILLE", i.city)
        assertEquals("CT", i.region)
        assertEquals("", i.locator)
        assertNull(i.position)
    }

    @Test
    fun anonymousPage() {
        val name = """<p class="m0" style="color: #666; font-weight: normal; font-size: 17px"><p style="font-size:0.8em;">Login is required for additional detail.</p>
</p>"""
        val html = page(name, "")
        assertTrue(QrzCom.needsLogin(html))
        val i = QrzCom.parse(html, "DL0DEMO")!!
        assertEquals("Germany", i.country)
        assertEquals("", i.name)
        assertEquals("", i.city)
    }

    @Test
    fun unknownCallsign() {
        assertNull(QrzCom.parse("<html><body><p>Search for ZZ0ZZZ produced no results.</p></body></html>", "ZZ0ZZZ"))
    }
}
