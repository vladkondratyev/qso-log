package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PropagationTest {
    @Test
    fun hamqslBandsAndVhf() {
        val xml = """<calculatedconditions><band name="80m-40m" time="day">Poor</band><band name="30m-20m" time="day">Good</band>
            <band name="80m-40m" time="night">Fair</band><band name="30m-20m" time="night">Poor</band></calculatedconditions>
            <phenomenon name="E-Skip" location="europe_6m">Band Closed</phenomenon>"""
        assertEquals(listOf(BandCondition("80m-40m", "Poor", "Fair"), BandCondition("30m-20m", "Good", "Poor")), Propagation.parseBands(xml))
        assertEquals(listOf("E-Skip · europe 6m" to "Band Closed"), Propagation.parseVhf(xml))
    }

    @Test
    fun noaaJson() {
        val kp = Propagation.parseKp("""[{"time_tag":"2026-10-31T18:00:00","kp":6.00,"observed":"observed","noaa_scale":"G2"},{"time_tag":"2026-11-09T00:00:00","kp":2.33,"observed":"predicted","noaa_scale":null}]""")
        assertEquals(2, kp.size); assertEquals("G2", kp[0].scale); assertTrue(kp[0].observed); assertEquals(2.33, kp[1].kp)
        val flux = Propagation.parseFlux("""[{"time_tag":"2026-11-07T20:00:00","flux":127.0,"reporting_schedule":"Noon"},{"time_tag":"2026-11-07T17:00:00","flux":129.0,"reporting_schedule":"Morning"},{"time_tag":"2026-11-06T20:00:00","flux":120.0,"reporting_schedule":"Noon"}]""")
        assertEquals(listOf(120.0, 127.0), flux.map { it.second })
        val fl = Propagation.parseFlares("""[{"max_time":"2026-11-01T12:36:00Z","max_class":"C1.1"},{"max_time":"2026-11-02T10:00:00Z","max_class":"M2.4"}]""")
        assertEquals("M2.4", fl.first().cls); assertTrue(fl.first().strong)
    }

    @Test
    fun drapAndAurora() {
        val drap = """# DRAP
      -178 -174 -170 -166 -162 -158 -154 -150 -146 -142 -138 -134
------------------------------------------------------------
 89 |  0.0  0.0  0.0  0.0  0.0  0.0  0.0  0.0  0.0  0.0  0.0  0.0
 55 |  0.0  5.5  0.0  0.0  0.0  0.0  0.0  0.0  0.0  0.0  0.0  0.0"""
        assertEquals(listOf(GridCell(55.0, -174.0, 5.5)), Propagation.parseDrap(drap))
        val au = Propagation.parseAurora("""{"coordinates":[[37,67,40],[38,66,12],[200,-70,5],[200,-71,30]]}""")
        assertEquals(2, au.size)
        assertTrue(au.any { it.value == 40.0 && it.lat > 60 })
        assertTrue(au.any { it.lon < 0 && it.lat < -60 })
    }

    @Test
    fun ionosondesAndReceptions() {
        val now = java.time.Instant.parse("2026-11-08T00:30:00Z").toEpochMilli()
        val io = Propagation.parseIonosondes("""[{"fof2":8.6,"cs":100,"mufd":28.8,"time":"2026-11-08T00:10:05","station":{"name":"Demo","latitude":"30.4","longitude":"262.3"}},
            {"fof2":5.0,"cs":90,"mufd":15.0,"time":"2026-03-19T22:10:05","station":{"name":"Old","latitude":"10","longitude":"10"}}]""", now)
        assertEquals(1, io.size); assertEquals(-97.7, io[0].point.lon, 0.01); assertEquals(28.8, io[0].mufMhz)
        val r = Propagation.parseReceptions("""<receptionReports><receptionReport receiverCallsign="DL1DEMO" receiverLocator="JO62qm" senderCallsign="UA3DEMO" senderLocator="KO85ts" frequency="14075557" flowStartSeconds="1794091468" mode="FT8" sNR="-11" /></receptionReports>""")
        assertEquals("20m", r.single().band); assertEquals(-11, r.single().snr)
        val qth = Geo.locatorToLatLon("KO85ts")!!
        val heard = Propagation.heardMeByBand(r, qth)
        assertEquals("20m", heard.single().first); assertTrue(heard.single().third > 1500)
        // Heard in the field: a sender to the west of Moscow lands in the west sector.
        val act = Propagation.activity(listOf(r.single().copy(senderLocator = "JO62qm", sender = "DL2DEMO")), qth)
        assertEquals(1, act.single().second[6])
    }

    @Test
    fun atQthAndBeacons() {
        val qth = Geo.locatorToLatLon("KO85ts")!!
        val noon = java.time.Instant.parse("2026-06-21T10:00:00Z").toEpochMilli()
        val d = PropagationData(
            bands = listOf(BandCondition("30m-20m", "Good", "Poor")),
            ionosondes = listOf(Ionosonde("Near", LatLon(55.5, 37.3), 18.0, 6.0, 90, noon), Ionosonde("Far", LatLon(-30.0, 20.0), 30.0, 9.0, 90, noon)),
            drap = listOf(GridCell(55.0, 38.0, 4.0)), aurora = listOf(GridCell(61.0, 37.5, 25.0)),
        )
        val a = Propagation.atQth(d, qth, noon)
        assertTrue(!a.night); assertEquals("Near", a.ionosonde?.name); assertEquals(4.0, a.absorptionMhz)
        assertEquals(25, a.auroraPct); assertEquals(listOf("30m-20m" to "Good"), a.bandsNow)
        assertTrue((a.changeInMin ?: 0) > 0)
        // 00:00:00 UTC: 4U1UN on 14.100, YV5B on 18.110 (one slot behind); 00:00:15 — VE8AT on 14.100.
        val b = Beacons.now(java.time.Instant.parse("2026-11-08T00:00:00Z").toEpochMilli())
        assertEquals("4U1UN", b[0].second.call); assertEquals("YV5B", b[1].second.call); assertEquals(10, b[0].third)
        assertEquals("VE8AT", Beacons.now(java.time.Instant.parse("2026-11-08T00:00:15Z").toEpochMilli())[0].second.call)
    }
}
