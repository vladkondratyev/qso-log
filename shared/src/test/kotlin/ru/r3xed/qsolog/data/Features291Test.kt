package ru.r3xed.qsolog.data

import ru.r3xed.qsolog.SettingsIndex
import ru.r3xed.qsolog.SettingsPage
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Features291Test {
    private fun q(call: String, band: String = "20m", mode: String = "CW", adif: Map<String, String> = emptyMap(), rcvd: Boolean = false, sent: Boolean = false) =
        Qso(call = call, timeUtc = 1_700_000_000_000, band = band, mode = mode, adif = adif, qslRcvd = rcvd, qslSent = sent)

    @Test
    fun backups() {
        assertEquals("QSO-LOG-backup-2026-11-08-1530.adi", Backups.fileName(LocalDateTime.of(2026, 11, 8, 15, 30)))
        assertTrue(Backups.due(BackupPeriod.DAILY, 0))
        assertFalse(Backups.due(BackupPeriod.OFF, 0))
        val day = 86_400_000L
        assertFalse(Backups.due(BackupPeriod.WEEKLY, 10 * day, 12 * day))
        assertTrue(Backups.due(BackupPeriod.WEEKLY, 10 * day, 17 * day))
        val names = (1..12).map { "QSO-LOG-backup-2026-11-%02d-1200.adi".format(it) } + "other.adi"
        assertEquals(listOf("QSO-LOG-backup-2026-11-02-1200.adi", "QSO-LOG-backup-2026-11-01-1200.adi"), Backups.excess(names))
    }

    @Test
    fun filtersMatchAndRoundTrip() {
        val sent = q("DL1DEMO", adif = mapOf("LOTW_QSL_SENT" to "Y"))
        val fresh = q("OK1DEMO")
        val f = LogFilter(name = "LoTW 20m", unsent = OnlineLog.LOTW, band = "20m")
        assertFalse(f.matches(sent))
        assertTrue(f.matches(fresh))
        assertFalse(f.matches(q("OK1DEMO", band = "40m")))
        assertTrue(LogFilter(qsl = QslQueue.TO_SEND).matches(fresh))
        assertFalse(LogFilter(qsl = QslQueue.TO_SEND).matches(q("X", sent = true)))
        val list = listOf(f, LogFilter(name = "QSL", query = "DL&1", station = "UA3DEMO/P", qsl = QslQueue.RECEIVED, mode = "SSB"))
        assertEquals(list, LogFilters.decode(LogFilters.encode(list)))
    }

    @Test
    fun activationFields() {
        val a = Activation("id1", ActivationProgram.POTA, "ru-0001", 0)
        assertEquals(mapOf("MY_SIG" to "POTA", "MY_SIG_INFO" to "RU-0001", "MY_POTA_REF" to "RU-0001", Activation.FIELD to "id1"), a.fields())
        assertEquals("POTA RU-0001", a.title)
        assertEquals(a, Activation.decode(a.encode()))
        assertEquals("MY_CNTY", ActivationProgram.RDA.refField)
        assertNull(Activation.decode(""))
    }

    @Test
    fun qslLabels() {
        val h = QslLabels.html(listOf(q("DL1DEMO", adif = mapOf("QSL_VIA" to "dl0demo"))), "ua3demo")
        assertTrue("To Radio <b>DL1DEMO</b>" in h)
        assertTrue("via DL0DEMO" in h)
        assertTrue("UA3DEMO" in h)
    }

    @Test
    fun awards() {
        val list = listOf(
            q("DL1DEMO", rcvd = true), q("DL2DEMO", band = "40m"), q("R3XDEMO", adif = mapOf("CNTY" to "KG-15")),
            q("K1DEMO", adif = mapOf("STATE" to "MA", "CQZ" to "5")), q("JA1DEMO", adif = mapOf("LOTW_QSL_RCVD" to "Y")),
        )
        val dxcc = Awards.stat(Award.DXCC, list)
        assertTrue("Fed. Rep. of Germany" in dxcc.worked)
        assertTrue("Fed. Rep. of Germany" in dxcc.confirmed && "Japan" in dxcc.confirmed)
        assertEquals(setOf("20m", "40m"), dxcc.byBand.map { it.first }.toSet())
        val was = Awards.stat(Award.WAS, list)
        assertEquals(setOf("MA"), was.worked)
        assertEquals(49, was.missing.size)
        assertEquals(setOf("KG"), Awards.stat(Award.REGIONS, list).worked)
        assertEquals(setOf("KG-15"), Awards.stat(Award.RDA, list).worked)
        assertTrue("05" in Awards.stat(Award.WAZ, list).worked)
    }

    @Test
    fun clusterRegionAndAlerts() {
        val idx = DxLogIndex(listOf(q("R3XDEMO")))
        val spot = DxSpot(14025.0, "R1ZDEMO", "UA3DEMO", "", 0)
        assertEquals(DxMark.NEW_REGION, idx.mark(spot))
        assertTrue(DxAlerts.watched("VP8DEMO", listOf("VP8*")))
        assertFalse(DxAlerts.watched("VP9DEMO", listOf("VP8*")))
        val c = DxAlertConfig(enabled = true, newRegion = true, watch = listOf("3Y0J"))
        assertEquals(ru.r3xed.qsolog.tr("новая область"), DxAlerts.reason(spot, DxMark.NEW_REGION, c))
        assertNull(DxAlerts.reason(spot, DxMark.NEW_BAND, c))
        assertEquals(c.copy(quietMinutes = 30), DxAlertConfig.from(c.toMap()))
    }

    @Test
    fun sunAndSolar() {
        // June solstice noon UTC: the sun over ~23.4° N near Greenwich.
        val ms = java.time.Instant.parse("2026-06-21T12:00:00Z").toEpochMilli()
        val s = Sun.subsolar(ms)
        assertTrue(s.lat in 23.0..23.6, "lat ${s.lat}")
        assertTrue(kotlin.math.abs(s.lon) < 3, "lon ${s.lon}")
        assertFalse(Sun.isNight(LatLon(51.5, 0.0), ms))
        assertTrue(Sun.isNight(LatLon(-40.0, 180.0), ms))
        val d = SolarData.parse("<solardata><updated>08 Nov 2026 1200 GMT</updated><solarflux>142</solarflux><aindex>8</aindex><kindex>2</kindex><sunspots>98</sunspots></solardata>")!!
        assertEquals("SFI 142 · SN 98 · A 8 · K 2", d.short)
    }

    @Test
    fun lotwByCertificate() {
        val list = listOf(Qso(call = "A", timeUtc = 0, myCall = "UA3DEMO"), Qso(call = "B", timeUtc = 0, myCall = "UA3DEMO/P"), Qso(call = "C", timeUtc = 0), Qso(call = "D", timeUtc = 0, myCall = "R3DEMO"))
        val (m, skipped) = UploadCalls.byCertificate(list, listOf("UA3DEMO", "ua3demo/p"), "UA3DEMO/P")
        assertEquals(listOf("A"), m["UA3DEMO"]!!.map { it.call })
        assertEquals(listOf("B", "C"), m["UA3DEMO/P"]!!.map { it.call })
        assertEquals(1, skipped)
    }

    @Test
    fun settingsSearch() {
        assertEquals("Интерфейс", SettingsIndex.search("масштаб").single().block)
        assertEquals(SettingsPage.HARDWARE, SettingsIndex.search("omnirig").single().page)
        assertTrue(SettingsIndex.search("").isEmpty())
    }
}
