package ru.r3xed.qsolog.data

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DxClusterTest {
    private val now = LocalDate.of(2026, 10, 30).atTime(21, 5).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun liveSpotLine() {
        val s = DxCluster.parse("DX de ND4X:      18100.0  JA1DEMO      FT8 -12 dB                     2058Z", now)!!
        assertEquals(18100.0, s.khz)
        assertEquals("JA1DEMO", s.call)
        assertEquals("ND4X", s.spotter)
        assertEquals("FT8 -12 dB", s.comment)
        assertEquals(now - 7 * 60_000, s.timeUtc)
        assertEquals("17m", s.band)
        assertEquals("FT8", s.mode)
    }

    @Test
    fun rbnLineWithLocatorAfterTime() {
        val s = DxCluster.parse("DX de DL8DEMO-#:  14025.0  VK9DEMO      CW    24 dB  25 WPM  CQ      2101Z JO62", now)!!
        assertEquals("DL8DEMO", s.spotter)
        assertEquals("CW", s.mode)
        assertEquals(14_025_000L, s.hz)
    }

    @Test
    fun showDxLine() {
        val s = DxCluster.parse(" 21074.0  PY2DEMO     30-Oct-2026 2059Z  FT8                          <KD9DEMO>", now)!!
        assertEquals("PY2DEMO", s.call)
        assertEquals("KD9DEMO", s.spotter)
        assertEquals(now - 6 * 60_000, s.timeUtc)
    }

    @Test
    fun otherLinesAreNotSpots() {
        assertNull(DxCluster.parse("UA3DEMO de DB0SUE 30-Oct-2026 2101Z dxspider >", now))
        assertNull(DxCluster.parse("Hello UA3DEMO, this is DB0SUE", now))
        assertTrue(DxCluster.isLoginPrompt("* Welcome, DXer! *\r\nlogin: "))
        assertTrue(DxCluster.isLoginPrompt("Please enter your call: "))
    }

    @Test
    fun spotJustBeforeMidnightIsYesterday() {
        val s = DxCluster.parse("DX de ND4X:  14025.0  VK9DEMO  CW  2359Z", now)!!
        assertEquals(now - (21 * 60 + 5) * 60_000L - 60_000L, s.timeUtc)
    }

    @Test
    fun modeByFrequency() {
        assertEquals("CW", DxCluster.mode(7012.0, ""))
        assertEquals("FT8", DxCluster.mode(14_075.5, ""))
        assertEquals("DIGI", DxCluster.mode(14_090.0, ""))
        assertEquals("SSB", DxCluster.mode(14_195.0, "up 5"))
        assertEquals("SSB", DxCluster.mode(14_025.0, "LSB?"))
        assertEquals("CW", DxCluster.mode(10_120.0, ""))
    }

    @Test
    fun mergeReplacesTheSameStationAndDropsOld() {
        val a = DxSpot(14025.0, "VK9DEMO", "A", "", now - 60_000)
        val b = DxSpot(14025.4, "VK9DEMO", "B", "", now)
        val old = DxSpot(7012.0, "DL1DEMO", "C", "", now - 40 * 60_000)
        val list = DxCluster.merge(DxCluster.merge(listOf(old), a, now), b, now)
        assertEquals(listOf(b), list)
    }

    @Test
    fun marksAgainstTheLog() {
        val log = listOf(
            Qso(call = "DL1DEMO", timeUtc = now, band = "40m", mode = "CW"),
            Qso(call = "DL2DEMO", timeUtc = now, band = "20m", mode = "SSB"),
        )
        val ix = DxLogIndex(log)
        fun mark(call: String, khz: Double, comment: String = "") = ix.mark(DxSpot(khz, call, "X", comment, now))
        assertEquals(DxMark.WORKED, mark("DL1DEMO", 7012.0))
        assertEquals(DxMark.NEW_COUNTRY, mark("VK9DEMO", 14025.0))
        assertEquals(DxMark.NEW_BAND, mark("DL3DEMO", 21025.0))
        assertEquals(DxMark.NEW_MODE, mark("DL3DEMO", 14075.0))
        assertEquals(DxMark.NONE, mark("DL3DEMO", 14195.0))
    }

    @Test
    fun configRoundTrip() {
        val c = DxClusterConfig(server = "dxspider.co.uk:7300", login = "UA3DEMO", tune = false, keepMinutes = 60)
        assertEquals(c, DxClusterConfig.fromMap(c.toMap()))
        assertEquals("dxspider.co.uk", c.host)
        assertEquals(7300, c.port)
        assertEquals(DxClusterConfig(), DxClusterConfig.fromMap(emptyMap()))
    }

    @Test
    fun clientLogsInAsksForSpotsAndReadsThem() {
        val server = java.net.ServerSocket(0)
        val got = java.util.concurrent.LinkedBlockingQueue<String>()
        val node = Thread {
            server.accept().use { c ->
                val inp = c.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val out = c.getOutputStream()
                fun w(t: String) { out.write(t.toByteArray(Charsets.ISO_8859_1)); out.flush() }
                w("* Welcome, DXer! *\r\nlogin: ")
                got.put(inp.readLine())
                w("Hello UA3DEMO\r\nUA3DEMO de DB0DEMO 30-Oct-2026 2101Z dxspider >")
                got.put(inp.readLine())
                w(" 21074.0  PY2DEMO     30-Oct-2026 2059Z  FT8                          <KD9DEMO>\r\n")
                w("DX de ND4X:      18100.0  JA1DEMO      FT8 -12 dB                     2058Z\r\n")
                Thread.sleep(2000)
            }
        }.apply { isDaemon = true; start() }
        val spots = java.util.concurrent.LinkedBlockingQueue<DxSpot>()
        val statuses = java.util.concurrent.CopyOnWriteArrayList<DxStatus>()
        val client = DxClusterClient(DxClusterConfig(server = "127.0.0.1:${server.localPort}"), "UA3DEMO", { statuses += it }, { spots.put(it) })
        client.start()
        try {
            assertEquals("UA3DEMO", got.poll(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("sh/dx 30", got.poll(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("PY2DEMO", spots.poll(5, java.util.concurrent.TimeUnit.SECONDS)?.call)
            assertEquals("JA1DEMO", spots.poll(5, java.util.concurrent.TimeUnit.SECONDS)?.call)
            assertTrue(statuses.any { it is DxStatus.Connected })
        } finally {
            client.stop()
            server.close()
            node.interrupt()
        }
    }
}
