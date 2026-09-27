package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnlineLogsTest {
    private val q = Qso(
        id = 7, call = "R0DEMO", timeUtc = 1_790_000_000_000, band = "20m", mode = "SSB", freqMhz = "14.195",
        rstSent = "59", rstRcvd = "57", name = "Иван", audio = "qso_1.m4a",
        adif = mapOf("QRZCOM_QSO_UPLOAD_STATUS" to "Y", "APP_QSOLOG_POS" to "REGION", "STATE" to "KG"),
    )

    @Test
    fun recordLeavesOurOwnFieldsHome() {
        val r = OnlineLogs.record(q)
        assertTrue(r.startsWith("<CALL:6>R0DEMO"))
        assertTrue(r.contains("<BAND:3>20m") && r.contains("<MODE:3>SSB") && r.contains("<STATE:2>KG"))
        // Lengths are in UTF-8 bytes: "Иван" is 8.
        assertTrue(r.contains("<NAME:8>Иван"))
        assertFalse(r.contains("APP_") || r.contains("QRZCOM_QSO_UPLOAD"))
        assertTrue(r.endsWith("<EOR>\n"))
        val f = OnlineLogs.file(listOf(q)) { "<APP_EQSL_QTH_NICKNAME:4>Home" }
        assertTrue(f.contains("<EOH>") && f.contains("<APP_EQSL_QTH_NICKNAME:4>Home<EOR>"))
    }

    @Test
    fun marks() {
        assertTrue(OnlineLog.QRZCOM.isSent(q))
        assertFalse(OnlineLog.EQSL.isSent(q))
        val m = OnlineLog.EQSL.mark(q)
        assertEquals("Y", m.adif["EQSL_QSL_SENT"])
        assertEquals(8, m.adif["EQSL_QSLSDATE"]!!.length)
        assertTrue(OnlineLog.LOTW.isSent(OnlineLog.LOTW.mark(q, "Q")))
    }

    /** Answers as the services gave them to made-up credentials (September 2026). */
    @Test
    fun answers() {
        val qrz = OnlineLogs.parseQrzCom("STATUS=AUTH&RESULT=AUTH&REASON=invalid api key 0000000000000000\n&EXTENDED=")
        assertEquals("AUTH", qrz["RESULT"])
        assertEquals("OK", OnlineLogs.parseQrzCom("RESULT=OK&LOGID=130877825&COUNT=1")["RESULT"])
        val (none, err) = OnlineLogs.parseEqsl("<HTML><BODY>  Error: No match on eQSL_User/eQSL_Pswd<BR></BODY></HTML>")
        assertNull(none)
        assertEquals("No match on eQSL_User/eQSL_Pswd", err)
        val (added, _) = OnlineLogs.parseEqsl("<BR>Result: 2 out of 3 records added<BR>Warning: Y=2026 ... Bad record: Duplicate<BR>")
        assertEquals(2, added)
    }
}
