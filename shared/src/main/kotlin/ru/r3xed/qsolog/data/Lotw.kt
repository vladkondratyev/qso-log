package ru.r3xed.qsolog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.tr
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.math.abs

class LotwException(message: String) : Exception(message)

/**
 * The callsign certificate of LoTW, from the .p12 file TQSL saves (Callsign Certificates → Save the callsign
 * certificate). [dxcc] is the DXCC entity it was issued for; [qsoFrom]..[qsoTo] — the contact dates it may sign.
 */
class LotwCert(
    val callsign: String,
    val dxcc: Int,
    val qsoFrom: LocalDate?,
    val qsoTo: LocalDate?,
    val notAfter: Instant,
    internal val cert: X509Certificate,
    internal val key: PrivateKey,
)

/**
 * LoTW without TQSL, as RUMlog and SDR-Control do it: the contacts are signed here with the callsign certificate and
 * sent to LoTW as a .tq8 file; confirmations come from LoTW's report with the website login.
 *
 * The signed file is TQSL's GABBI format (tqsllib, location.cpp): a tCERT record with the certificate, a tSTATION record
 * with the station's callsign, DXCC entity, locator and zones, and a tCONTACT per contact. A contact's signature is
 * SHA-1 with RSA over its SIGNDATA: the station's values in the order of the LoTW 2.0 signature specification
 * (CQZ, GRIDSQUARE, ITUZ), then BAND, CALL, FREQ, MODE, QSO_DATE ("2026-10-20"), QSO_TIME ("12:17:00Z"), upper case.
 */
object Lotw {
    const val RCVD = "LOTW_QSL_RCVD"
    const val RCVD_DATE = "LOTW_QSLRDATE"
    val RCVD_FIELDS = setOf(RCVD, RCVD_DATE)

    const val UPLOAD_URL = "https://lotw.arrl.org/lotw/upload"
    const val REPORT_URL = "https://lotw.arrl.org/lotwuser/lotwreport.adi"

    private const val OID_CALLSIGN = "1.3.6.1.4.1.12348.1.1"
    private const val OID_QSO_FROM = "1.3.6.1.4.1.12348.1.2"
    private const val OID_QSO_TO = "1.3.6.1.4.1.12348.1.3"
    private const val OID_DXCC = "1.3.6.1.4.1.12348.1.4"

    fun isReceived(adif: Map<String, String>) = adif[RCVD].equals("Y", ignoreCase = true)

    /** "18.10.2026", "" when the day is unknown; null when not confirmed in LoTW. */
    fun receivedAt(adif: Map<String, String>): String? {
        if (!isReceived(adif)) return null
        return adif[RCVD_DATE]?.let { d ->
            runCatching { LocalDate.parse(d, DateTimeFormatter.BASIC_ISO_DATE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrNull()
        }.orEmpty()
    }

    // ---------- the certificate ----------

    /** Reads the .p12 file: the certificate that has a key and a LoTW callsign. Throws [LotwException] with what is wrong. */
    fun loadP12(bytes: ByteArray, password: String): LotwCert {
        val ks = KeyStore.getInstance("PKCS12")
        try {
            ks.load(bytes.inputStream(), password.toCharArray())
        } catch (e: Exception) {
            val wrong = e.cause is javax.crypto.BadPaddingException || e.message.orEmpty().contains("password", ignoreCase = true) ||
                e is java.io.IOException && e.cause is java.security.UnrecoverableKeyException
            throw LotwException(if (wrong) tr("Неверный пароль файла .p12") else tr("Это не файл сертификата .p12: %s", e.message ?: e.javaClass.simpleName))
        }
        for (alias in ks.aliases().toList()) {
            if (!ks.isKeyEntry(alias)) continue
            val key = runCatching { ks.getKey(alias, password.toCharArray()) as? PrivateKey }.getOrNull() ?: continue
            val cert = ks.getCertificate(alias) as? X509Certificate ?: continue
            val call = callsign(cert) ?: continue
            val dxcc = extension(cert, OID_DXCC)?.trim()?.toIntOrNull() ?: 0
            return LotwCert(
                callsign = call.uppercase(), dxcc = dxcc,
                qsoFrom = extension(cert, OID_QSO_FROM)?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() },
                qsoTo = extension(cert, OID_QSO_TO)?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() },
                notAfter = cert.notAfter.toInstant(), cert = cert, key = key,
            )
        }
        throw LotwException(tr("В файле нет сертификата позывного LoTW с ключом. Сохраните в TQSL сертификат позывного (Save the callsign certificate), а не запрос"))
    }

    /** The callsign of the subject: the AROcallsign attribute (TQSL's OID) of the name. */
    private fun callsign(cert: X509Certificate): String? {
        val name = cert.subjectX500Principal.getName(javax.security.auth.x500.X500Principal.RFC2253)
        val hex = Regex("(?:^|,)" + Regex.escape(OID_CALLSIGN) + "=#([0-9A-Fa-f]+)").find(name)?.groupValues?.get(1)
        if (hex != null) return derString(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        return Regex("(?:^|,)" + Regex.escape(OID_CALLSIGN) + "=([^,]+)").find(name)?.groupValues?.get(1)
    }

    /** A TQSL extension's value: the bytes inside the extension's OCTET STRING, as text ("54", "2026-01-01"). */
    private fun extension(cert: X509Certificate, oid: String): String? {
        val v = cert.getExtensionValue(oid) ?: return null
        // getExtensionValue gives the DER OCTET STRING: 04, length, then the value.
        return derString(v) ?: String(v, Charsets.US_ASCII)
    }

    /** The content of a DER string (OCTET, Printable, IA5, UTF8): tag, length, bytes. */
    private fun derString(b: ByteArray): String? {
        if (b.size < 2) return null
        var len = b[1].toInt() and 0xFF
        var at = 2
        if (len > 0x7F) {
            val n = len and 0x7F
            len = 0
            repeat(n) { len = (len shl 8) or (b[at++].toInt() and 0xFF) }
        }
        if (at + len > b.size) return null
        val inner = b.copyOfRange(at, at + len)
        // An extension may wrap its string once more (an OCTET STRING with a string inside).
        val tag = inner.firstOrNull()?.toInt()?.and(0xFF)
        if (tag in setOf(0x0C, 0x13, 0x16) && inner.size >= 2 && (inner[1].toInt() and 0xFF) == inner.size - 2) return String(inner, 2, inner.size - 2, Charsets.UTF_8)
        return String(inner, Charsets.UTF_8)
    }

    // ---------- signing ----------

    /** Where the station is, as TQSL's Station Location says: the locator (4 or 6 characters) and the zones. */
    data class Station(val grid: String, val cqZone: String = "", val ituZone: String = "")

    /** LoTW's name of a mode (TQSL's ADIF mode map): SSB, CW, FT8, FT4, PSK31, … — DATA for the rest of the digital ones. */
    fun mode(q: Qso): String {
        val m = q.mode.trim().uppercase()
        val sub = q.adif["SUBMODE"]?.trim()?.uppercase().orEmpty()
        return when {
            m in setOf("SSB", "USB", "LSB") -> "SSB"
            m == "MFSK" -> when (sub) { "FT4", "FST4", "Q65", "MFSK16", "MFSK8" -> sub; else -> "DATA" }
            m == "PSK" -> when (sub) { "PSK31", "BPSK31", "QPSK31" -> "PSK31"; "PSK63", "BPSK63", "QPSK63" -> "PSK63"; "PSK125", "BPSK125", "QPSK125" -> "PSK125"; else -> "DATA" }
            m in setOf("JS8", "DIGI", "FSQ", "VARA") -> "DATA"
            m.startsWith("JT65") -> "JT65"
            m.startsWith("OLIVIA") -> "OLIVIA"
            m in LOTW_MODES -> m
            else -> "DATA"
        }
    }

    private val LOTW_MODES = setOf(
        "AM", "ATV", "C4FM", "CW", "DIGITALVOICE", "DSTAR", "FM", "FST4", "FT4", "FT8", "HELL", "JT4", "JT65", "JT9", "MSK144",
        "MFSK16", "MFSK8", "MT63", "OLIVIA", "PACKET", "PSK31", "PSK63", "PSK125", "Q65", "RTTY", "SSTV", "THOR", "WSPR", "DOMINO",
    )

    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss'Z'")

    /** "<NAME:len>value" (with ":6" for base64), as tqsl_adifMakeField writes it. */
    private fun field(name: String, value: String, type: String = ""): String = "<$name:${value.toByteArray(Charsets.UTF_8).size}${if (type.isEmpty()) "" else ":$type"}>$value"

    /** OpenSSL's base64 of the signature: lines of 64 characters, each ending with a new line. */
    private fun base64Lines(b: ByteArray): String = Base64.getEncoder().encodeToString(b).chunked(64).joinToString("") { it + "\n" }

    /** The contacts the certificate cannot sign (the station's callsign is another one, the dates are outside its range). */
    fun unsignable(cert: LotwCert, list: List<Qso>): List<Qso> = list.filter { q ->
        val day = Instant.ofEpochMilli(q.timeUtc).atZone(ZoneOffset.UTC).toLocalDate()
        (q.myCall.isNotBlank() && !q.myCall.trim().equals(cert.callsign, ignoreCase = true)) ||
            (cert.qsoFrom != null && day < cert.qsoFrom) || (cert.qsoTo != null && day > cert.qsoTo) ||
            q.band.isBlank() || q.call.isBlank()
    }

    /** The GABBI text of [list] signed with [cert] for [station] (contacts it cannot sign are skipped: see [unsignable]). */
    fun signedLog(cert: LotwCert, station: Station, list: List<Qso>, program: String = "QSO-LOG"): String {
        val skip = unsignable(cert, list).map { it.id to it.timeUtc }.toSet()
        val grid = station.grid.trim().take(6).let { if (Geo.isLocator(it)) it.take(4).uppercase() + it.drop(4).lowercase() else "" }
        val cqz = station.cqZone.trim().toIntOrNull()?.toString().orEmpty()
        val ituz = station.ituZone.trim().toIntOrNull()?.toString().orEmpty()
        val sb = StringBuilder()
        val ident = "$program Lib: V2.7 Config: V11.30 AllowDupes: false"
        sb.append(field("TQSL_IDENT", ident)).append("\n\n")
        val pem = Base64.getEncoder().encodeToString(cert.cert.encoded).chunked(64).joinToString("") { it + "\n" }
        sb.append("<Rec_Type:5>tCERT\n").append(field("CERT_UID", "1")).append("\n").append(field("CERTIFICATE", pem)).append("<eor>\n\n")
        sb.append("<Rec_Type:8>tSTATION\n").append(field("STATION_UID", "1")).append("\n").append(field("CERT_UID", "1")).append("\n")
        sb.append(field("CALL", cert.callsign)).append("\n").append(field("DXCC", cert.dxcc.toString())).append("\n")
        if (grid.isNotEmpty()) sb.append(field("GRIDSQUARE", grid)).append("\n")
        if (ituz.isNotEmpty()) sb.append(field("ITUZ", ituz)).append("\n")
        if (cqz.isNotEmpty()) sb.append(field("CQZ", cqz)).append("\n")
        sb.append("<eor>\n\n")
        // The station's part of every SIGNDATA: the LoTW 2.0 specification's tSTATION fields that are set, in its order.
        val stationSign = cqz + grid + ituz
        val signer = Signature.getInstance("SHA1withRSA")
        for (q in list.sortedBy { it.timeUtc }) {
            if ((q.id to q.timeUtc) in skip) continue
            val t = Instant.ofEpochMilli(q.timeUtc).atZone(ZoneOffset.UTC)
            val band = q.band.trim().uppercase()
            val call = q.call.trim().uppercase()
            val freq = q.freqMhz.trim().replace(',', '.').takeIf { it.toDoubleOrNull() != null }.orEmpty()
            val mode = mode(q)
            val prop = q.adif["PROP_MODE"]?.trim()?.uppercase().orEmpty()
            val sat = q.adif["SAT_NAME"]?.trim()?.uppercase().orEmpty()
            val date = t.format(DAY)
            val time = t.format(TIME)
            // tCONTACT's order of the specification: BAND, BAND_RX, CALL, FREQ, FREQ_RX, MODE, PROP_MODE, QSO_DATE, QSO_TIME, SAT_NAME.
            val signData = (stationSign + band + call + freq + mode + prop + date + time + sat).uppercase()
            signer.initSign(cert.key)
            signer.update(signData.toByteArray(Charsets.UTF_8))
            val sig = base64Lines(signer.sign())
            sb.append("<Rec_Type:8>tCONTACT\n").append(field("STATION_UID", "1")).append("\n")
            sb.append(field("CALL", call)).append("\n").append(field("BAND", band)).append("\n").append(field("MODE", mode)).append("\n")
            if (freq.isNotEmpty()) sb.append(field("FREQ", freq)).append("\n")
            if (prop.isNotEmpty()) sb.append(field("PROP_MODE", prop)).append("\n")
            if (sat.isNotEmpty()) sb.append(field("SAT_NAME", sat)).append("\n")
            sb.append(field("QSO_DATE", date)).append("\n").append(field("QSO_TIME", time)).append("\n")
            sb.append(field("SIGN_LOTW_V2.0", sig, "6")).append(field("SIGNDATA", signData)).append("\n<eor>\n\n")
        }
        return sb.toString()
    }

    /** The .tq8 file: the signed log, gzipped. */
    fun tq8(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    // ---------- upload and confirmations ----------

    /** LoTW's answer page: "<!-- .UPL. accepted -->" and the message "<!-- .UPLMESSAGE. … -->". */
    fun parseUpload(html: String): Pair<Boolean, String> {
        val status = Regex("<!-- \\.UPL\\.\\s*([^-]+?)\\s*-->").find(html)?.groupValues?.get(1)?.trim().orEmpty()
        val message = Regex("<!-- \\.UPLMESSAGE\\.\\s*(.+?)\\s*-->", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.trim().orEmpty()
        return status.equals("accepted", ignoreCase = true) to message
    }

    /** Sends the signed contacts; throws [LotwException] when LoTW does not take the file. */
    suspend fun upload(cert: LotwCert, station: Station, list: List<Qso>, agent: String): Int = withContext(Dispatchers.IO) {
        if (Instant.now() > cert.notAfter) throw LotwException(tr("Сертификат LoTW для %s истёк: обновите его в TQSL и загрузите новый файл .p12", cert.callsign))
        val text = signedLog(cert, station, list)
        val n = Regex("<Rec_Type:8>tCONTACT").findAll(text).count()
        if (n == 0) return@withContext 0
        val boundary = "qsolog" + System.nanoTime()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Disposition: form-data; name=\"upfile\"; filename=\"qsolog.tq8\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            write(tq8(text))
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        val c = URL(UPLOAD_URL).openConnection() as HttpURLConnection
        val (code, page) = try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.setRequestProperty("User-Agent", agent)
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            code to ((if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty())
        } catch (e: Exception) {
            throw LotwException(OnlineLogs.netError("LoTW", e))
        } finally {
            c.disconnect()
        }
        if (code != 200) throw LotwException(tr("LoTW ответил %s, попробуйте позже", code))
        val (ok, message) = parseUpload(page)
        if (!ok) throw LotwException(tr("LoTW не принял файл: %s", message.ifBlank { tr("неожиданный ответ сервера") }))
        n
    }

    /**
     * An upload as the log's dialog reports it: the contacts the certificate can sign go, the others are left (and
     * said); a refusal of LoTW is the error.
     */
    suspend fun send(cert: LotwCert, station: Station, list: List<Qso>, agent: String): UploadResult {
        val skip = unsignable(cert, list).map { it.id to it.timeUtc }.toSet()
        val ok = list.filter { (it.id to it.timeUtc) !in skip }
        if (ok.isEmpty()) return UploadResult(emptyList(), 0, tr("Сертификатом %s нельзя подписать эти связи: позывной станции другой или даты вне срока сертификата", cert.callsign))
        return try {
            upload(cert, station, ok, agent)
            UploadResult(ok, 0, if (skip.isEmpty()) null else tr("не подписано связей: %s — позывной станции не %s или дата вне срока сертификата", skip.size, cert.callsign))
        } catch (e: LotwException) {
            UploadResult(emptyList(), 0, e.message)
        }
    }

    /** "R0DEMO · DXCC 54 · до 12.05.2029": the loaded certificate in the settings. */
    fun describe(c: LotwCert): String = tr(
        "Сертификат %s · DXCC %s · действует до %s", c.callsign, c.dxcc,
        c.notAfter.atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
    )

    /** The report's line after a download of the confirmations. */
    fun summary(m: Match): String = buildString {
        append(tr("LoTW: подтверждено связей: %s", m.confirmed.size))
        if (m.already > 0) append(tr(", отмечены раньше: %s", m.already))
        if (m.notFound > 0) append(tr(", нет в журнале: %s", m.notFound))
    }

    /**
     * The confirmations (QSL) of the account since [since] (all when null), as ADIF records. Throws [LotwException]
     * for a wrong login: LoTW then answers with a web page instead of ADIF.
     */
    suspend fun confirmations(user: String, password: String, since: LocalDate?, agent: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val url = "$REPORT_URL?login=${enc(user.trim())}&password=${enc(password)}&qso_query=1&qso_qsl=yes&qso_qsldetail=yes&qso_mydetail=yes" +
            "&qso_qslsince=" + (since ?: LocalDate.of(1990, 1, 1)).format(DAY)
        val (code, bytes) = try {
            OnlineLogs.get(url, agent)
        } catch (e: Exception) {
            throw LotwException(OnlineLogs.netError("LoTW", e))
        }
        if (code != 200) throw LotwException(tr("LoTW ответил %s, попробуйте позже", code))
        val text = bytes.toString(Charsets.UTF_8)
        if (!text.contains("<eoh>", ignoreCase = true)) {
            throw LotwException(
                if (text.contains("password", ignoreCase = true) || text.contains("login", ignoreCase = true)) tr("LoTW не принял логин или пароль сайта")
                else tr("LoTW: неожиданный ответ сервера"),
            )
        }
        Adif.parseRecords(bytes, Charsets.UTF_8)
    }

    /** SSB, USB and LSB are one mode for the match; LoTW's DATA matches any digital mode. */
    private fun modeKey(m: String) = m.uppercase().let { if (it in setOf("USB", "LSB", "PHONE")) "SSB" else it }

    /** What the report gave: the contacts newly confirmed (already marked), how many were confirmed before, the records not found. */
    data class Match(val confirmed: List<Qso>, val already: Int, val notFound: Int)

    /**
     * Matches LoTW's [records] with the [log]: same station (home callsign), band and mode, times within 30 minutes;
     * the nearest contact wins. A confirmed one gets LOTW_QSL_RCVD = Y and the day of the confirmation.
     */
    fun match(records: List<Map<String, String>>, log: List<Qso>, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): Match {
        val byCall = log.groupBy { Callsign.home(it.call) }
        val used = mutableSetOf<Long>()
        val confirmed = mutableListOf<Qso>()
        var already = 0
        var notFound = 0
        val day = today.format(DateTimeFormatter.BASIC_ISO_DATE)
        for (r in records) {
            if (!r["QSL_RCVD"].equals("Y", ignoreCase = true)) continue
            val rec = Adif.toQso(r) ?: continue
            val recMode = modeKey(rec.mode)
            val best = byCall[Callsign.home(rec.call)].orEmpty()
                .filter {
                    it.id !in used && it.band.equals(rec.band, ignoreCase = true) && abs(it.timeUtc - rec.timeUtc) <= Eqsl.WINDOW_MS &&
                        (modeKey(it.mode) == recMode || modeKey(mode(it)) == recMode || recMode == "DATA" && mode(it) == "DATA")
                }
                .minByOrNull { abs(it.timeUtc - rec.timeUtc) }
            when {
                best == null -> notFound++
                isReceived(best.adif) -> { used += best.id; already++ }
                else -> {
                    used += best.id
                    val at = r["QSLRDATE"]?.trim()?.takeIf { it.length == 8 && it.all(Char::isDigit) } ?: day
                    confirmed += best.copy(adif = best.adif + (RCVD to "Y") + (RCVD_DATE to at), updatedAt = System.currentTimeMillis())
                }
            }
        }
        return Match(confirmed, already, notFound)
    }
}
