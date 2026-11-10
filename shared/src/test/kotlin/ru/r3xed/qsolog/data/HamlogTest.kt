package ru.r3xed.qsolog.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** HAMLOG.online API v2 against a local stand-in server that answers as the documentation says. */
class HamlogTest {
    private var server: HttpServer? = null

    @AfterTest
    fun stop() {
        server?.stop(0)
        Hamlog.base = Hamlog.BASE
    }

    private val q = Qso(
        id = 11, call = "R0DEMO", timeUtc = 1_790_000_000_000, band = "20m", mode = "FT4", freqMhz = "14.080",
        rstSent = "-10", rstRcvd = "-12", name = "Иван", myCall = "UA3DEMO/P", myLocator = "KO85",
        adif = mapOf("IOTA" to "EU-001", "MY_RIG" to "IC-7300", "QRZCOM_QSO_UPLOAD_STATUS" to "Y", "STATION_CALLSIGN" to "UA3DEMO/P"),
    )

    @Test
    fun recordHasOnlyFieldsHamlogKeeps() {
        val r = Hamlog.record(q)
        assertTrue(r.startsWith("<CALL:6>R0DEMO"))
        // FT4 is a submode of MFSK; the band in capitals as in Hamlog's examples; lengths in UTF-8 bytes.
        assertTrue("<MODE:4>MFSK" in r && "<SUBMODE:3>FT4" in r && "<BAND:3>20M" in r && "<NAME:8>Иван" in r && "<IOTA:6>EU-001" in r)
        assertTrue("<MY_GRIDSQUARE:4>KO85" in r)
        // Not kept by Hamlog, or ours, or the callsign (it goes in the address).
        assertFalse("MY_RIG" in r || "QRZCOM" in r || "APP_" in r || "STATION_CALLSIGN" in r)
        assertTrue(r.endsWith("<EOR>\n"))
    }

    @Test
    fun linkRoundTrip() {
        val l = Hamlog.Link("acc", 1, "ref", 2, listOf("UA3DEMO", "UA3DEMO/P"))
        assertEquals(l, Hamlog.Link.decode(l.encode()))
        assertNull(Hamlog.Link.decode(""))
        assertNull(Hamlog.Link.decode("{\"a\":\"x\"}"))
        assertEquals(listOf(3L, 5L), Hamlog.parseQueue("3, 5,3,,x"))
    }

    @Test
    fun hamlogStaysOnTheDevice() {
        val c = OnlineConfig(hamlogOn = true, hamlogCalls = "UA3DEMO", hamlogAuto = true)
        assertEquals(c, OnlineConfig.from(c.publicMap() + c.secretMap()))
        assertNull(c.problem(OnlineLog.HAMLOG, ""))
        assertTrue(OnlineConfig().problem(OnlineLog.HAMLOG, "UA3DEMO") != null)
        // Single-use tokens on two devices would revoke each other: none of it goes through the settings sync.
        val out = SettingsSync.outgoing(c.publicMap() + mapOf("hamlog_link" to "x", "hamlog_queue" to "1"), credentials = true)
        assertTrue(out.keys.none { it.startsWith("hamlog_") })
        assertTrue(Hamlog.ID_FIELD in OnlineLog.FIELDS)
    }

    private fun serve(handler: (method: String, path: String, query: String, auth: String?, body: String) -> Triple<Int, String, Map<String, String>>) {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val (code, text, headers) = handler(ex.requestMethod, ex.requestURI.path, ex.requestURI.rawQuery.orEmpty(), ex.requestHeaders.getFirst("Authorization"), body)
            headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
            val bytes = text.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong().coerceAtLeast(1).let { if (bytes.isEmpty()) -1 else it })
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        s.start()
        server = s
        Hamlog.base = "http://127.0.0.1:${s.address.port}/api/v2"
    }

    private fun form(body: String) = body.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    @Test
    fun deviceFlowGivesTokensAndCallsigns() = runBlocking {
        var polls = 0
        serve { method, path, _, auth, body ->
            when ("$method $path") {
                "POST /api/v2/oauth/authorization" -> {
                    val f = form(body)
                    assertEquals("QSO-LOG", f["application_name"])
                    assertEquals("qsos:read qsos:write", f["scope"])
                    Triple(200, """{"device_code":"DEV","user_code":"ABCD-EFGH-JKLM","verification_uri":"https://h/activate","verification_uri_complete":"https://h/activate?user_code=ABCD-EFGH-JKLM","expires_in":600,"interval":5}""", emptyMap())
                }
                "POST /api/v2/oauth/token" -> {
                    assertEquals("DEV", form(body)["device_code"])
                    polls++
                    when (polls) {
                        1 -> Triple(400, """{"error":"authorization_pending"}""", emptyMap())
                        2 -> Triple(400, """{"error":"slow_down"}""", mapOf("Retry-After" to "15"))
                        else -> Triple(200, """{"access_token":"A1","token_type":"Bearer","expires_in":3600,"refresh_token":"R1","refresh_expires_in":7776000,"scope":"qsos:read qsos:write"}""", emptyMap())
                    }
                }
                "GET /api/v2/connection" -> {
                    assertEquals("Bearer A1", auth)
                    Triple(200, """{"application":"QSO-LOG","station_callsigns":["UA3DEMO"],"permissions":["qsos:read","qsos:write"]}""", emptyMap())
                }
                else -> Triple(404, "{}", emptyMap())
            }
        }
        val d = Hamlog.start("test", "test")
        assertEquals("ABCD-EFGH-JKLM", d.userCode)
        assertTrue(d.uriComplete.endsWith("ABCD-EFGH-JKLM"))
        assertEquals(Hamlog.Poll.Wait(5), Hamlog.poll(d, 5, "test"))
        assertEquals(Hamlog.Poll.Wait(15), Hamlog.poll(d, 5, "test"))
        val done = Hamlog.poll(d, 15, "test") as Hamlog.Poll.Done
        assertEquals("R1", done.link.refresh)
        assertEquals(listOf("UA3DEMO"), done.link.calls)
    }

    @Test
    fun deniedAndExpiredStopTheWaiting() = runBlocking {
        var answer = "access_denied"
        serve { _, _, _, _, _ -> Triple(400, """{"error":"$answer"}""", emptyMap()) }
        val d = Hamlog.DeviceCode("DEV", "X", "u", "u", Long.MAX_VALUE, 5)
        assertTrue(Hamlog.poll(d, 5, "t") is Hamlog.Poll.Failed)
        answer = "expired_token"
        assertTrue(Hamlog.poll(d, 5, "t") is Hamlog.Poll.Failed)
    }

    @Test
    fun refreshIsSavedBeforeUseAndUploadReportsEachRecord() = runBlocking {
        var stored = Hamlog.Link("OLD", 0, "R1", Long.MAX_VALUE, listOf("UA3DEMO")).encode()
        val usedRefresh = mutableListOf<String>()
        val posted = mutableListOf<String>()
        serve { method, path, query, auth, body ->
            when ("$method $path") {
                "POST /api/v2/oauth/token" -> {
                    val f = form(body)
                    assertEquals("refresh_token", f["grant_type"])
                    usedRefresh += f["refresh_token"]!!
                    Triple(200, """{"access_token":"A2","expires_in":3600,"refresh_token":"R2","refresh_expires_in":7776000}""", emptyMap())
                }
                "POST /api/v2/qsos" -> {
                    assertEquals("Bearer A2", auth)
                    assertEquals("station_callsign=UA3DEMO%2FP", query)
                    posted += body
                    Triple(
                        200,
                        """{"station_callsign":"UA3DEMO/P","saved":1,"rejected":1,"failed":1,"results":[""" +
                            """{"record":1,"qso_id":12345,"log_callsign":"UA3DEMO","status":"saved","synchronization":"complete"},""" +
                            """{"record":2,"status":"rejected","http_status":422,"message":"Invalid band"},""" +
                            """{"record":3,"status":"failed","http_status":503,"retryable":true}]}""",
                        emptyMap(),
                    )
                }
                else -> Triple(404, "{}", emptyMap())
            }
        }
        val session = Hamlog.Session({ stored }, { stored = it?.encode().orEmpty() }, "test")
        val list = listOf(q, q.copy(id = 12, call = "R0BAD", timeUtc = q.timeUtc + 60_000), q.copy(id = 13, call = "R0LATE", timeUtc = q.timeUtc + 120_000))
        val r = session.upload(list, "UA3DEMO")
        // The old access token had ended: one refresh, and the new pair is stored.
        assertEquals(listOf("R1"), usedRefresh)
        assertEquals("R2", Hamlog.Link.decode(stored)!!.refresh)
        assertEquals(listOf("UA3DEMO"), Hamlog.Link.decode(stored)!!.calls)
        assertTrue(posted.single().startsWith("{\"adif\":\"<CALL:6>R0DEMO"))
        assertEquals(listOf(11L), r.sent.map { it.id })
        assertEquals("12345", r.sent[0].adif[Hamlog.ID_FIELD])
        // The refused one leaves the queue, the one with a passing error waits.
        assertEquals(listOf(12L), r.rejected.map { it.id })
        assertTrue(r.error!!.contains("Invalid band"))
        // The next upload uses the stored access token: no second refresh.
        session.upload(listOf(q), "UA3DEMO")
        assertEquals(1, usedRefresh.size)
    }

    @Test
    fun refusedRefreshDisconnects() = runBlocking {
        var stored = Hamlog.Link("OLD", 0, "R1", Long.MAX_VALUE).encode()
        serve { _, _, _, _, _ -> Triple(400, """{"error":"invalid_grant"}""", emptyMap()) }
        val session = Hamlog.Session({ stored }, { stored = it?.encode().orEmpty() }, "test")
        val r = session.upload(listOf(q), "UA3DEMO")
        assertTrue(r.sent.isEmpty() && r.error != null)
        assertEquals("", stored)
    }

    @Test
    fun queueMarksSentAndKeepsWaiting() = runBlocking {
        val stored = Hamlog.Link("A", Long.MAX_VALUE, "R", Long.MAX_VALUE).encode()
        serve { _, _, _, _, _ ->
            Triple(200, """{"results":[{"record":1,"qso_id":5,"status":"saved"},{"record":2,"status":"failed","retryable":true}]}""", emptyMap())
        }
        val db = mutableMapOf(11L to q, 12L to q.copy(id = 12, call = "R0LATE", timeUtc = q.timeUtc + 60_000), 14L to OnlineLog.HAMLOG.mark(q.copy(id = 14)))
        val session = Hamlog.Session({ stored }, {}, "test")
        val r = Hamlog.sendQueue(session, listOf(11, 12, 14, 99), { db[it] }, { db[it.id] = it }, "UA3DEMO")
        assertEquals(1, r.sent)
        assertEquals(listOf(12L), r.left)
        assertTrue(OnlineLog.HAMLOG.isSent(db[11]!!))
        assertEquals("5", db[11]!!.adif[Hamlog.ID_FIELD])
        assertFalse(OnlineLog.HAMLOG.isSent(db[12]!!))
    }
}
