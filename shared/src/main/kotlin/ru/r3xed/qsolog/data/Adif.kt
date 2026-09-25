package ru.r3xed.qsolog.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * ADIF (.adi) export and import, https://adif.org.uk/ .
 *
 * Fields with a place in the contact card map onto [Qso]; every other field is kept verbatim in [Qso.adif],
 * so a log goes through import → export without losing data.
 * Russian logs (LogHX, UR5EQF) write Windows-1251; import detects UTF-8 vs Windows-1251, export uses Windows-1251 by default.
 */
object Adif {
    val WINDOWS_1251: Charset = Charset.forName("windows-1251")

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val TIME = DateTimeFormatter.ofPattern("HHmmss")

    /** Fields stored in [Qso] itself; everything else goes to [Qso.adif]. */
    private val CORE = setOf(
        "CALL", "QSO_DATE", "TIME_ON", "FREQ", "BAND", "MODE", "RST_SENT", "RST_RCVD",
        "NAME", "QTH", "COUNTRY", "GRIDSQUARE", "LAT", "LON", "DISTANCE", "TX_PWR",
        "QSL_SENT", "QSL_RCVD", "COMMENT", "STATION_CALLSIGN", "MY_GRIDSQUARE",
        "APP_QSOLOG_AUDIO",
    )

    /** Our mode names that ADIF writes as MODE + SUBMODE. */
    private val SUBMODE_PARENT = mapOf(
        "FT4" to "MFSK", "JS8" to "MFSK", "Q65" to "MFSK",
        "PSK31" to "PSK", "PSK63" to "PSK",
        "C4FM" to "DIGITALVOICE", "DMR" to "DIGITALVOICE", "DSTAR" to "DIGITALVOICE",
    )

    // ---------- export ----------

    fun export(list: List<Qso>, out: OutputStream, charset: Charset = WINDOWS_1251, program: String = "QSO-LOG", version: String = "") {
        val w = AdiWriter(out, charset)
        w.raw("ADIF export from $program\r\n")
        w.field("ADIF_VER", "3.1.4")
        w.field("PROGRAMID", program)
        if (version.isNotBlank()) w.field("PROGRAMVERSION", version)
        w.field("CREATED_TIMESTAMP", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd HHmmss")))
        w.raw("\r\n<EOH>\r\n\r\n")
        for (q in list.sortedBy { it.timeUtc }) {
            for ((k, v) in fields(q)) w.field(k, v)
            w.raw("<EOR>\r\n")
        }
        w.flush()
    }

    /** One contact as ordered ADIF fields. */
    fun fields(q: Qso): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        fun add(k: String, v: String?) { if (!v.isNullOrBlank()) out += k to v.trim() }
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(q.timeUtc), ZoneOffset.UTC)
        add("CALL", q.call)
        add("QSO_DATE", DATE.format(t))
        add("TIME_ON", TIME.format(t))
        add("FREQ", q.freqMhz)
        add("BAND", q.band.lowercase())
        val parent = SUBMODE_PARENT[q.mode.uppercase()]
        if (parent != null) {
            add("MODE", parent)
            if (!q.adif.containsKey("SUBMODE")) add("SUBMODE", q.mode)
        } else add("MODE", q.mode)
        add("RST_SENT", q.rstSent)
        add("RST_RCVD", q.rstRcvd)
        add("NAME", q.name)
        add("QTH", q.qth)
        add("COUNTRY", q.country)
        if (Geo.isLocator(q.locator)) add("GRIDSQUARE", q.locator)
        q.lat?.let { add("LAT", formatCoord(it, 'N', 'S')) }
        q.lon?.let { add("LON", formatCoord(it, 'E', 'W')) }
        q.distanceKm?.let { add("DISTANCE", Math.round(it).toString()) }
        add("TX_PWR", q.power)
        // Only "Y": writing "N" for every contact could overwrite QSL status in the log the file is imported into.
        if (q.qslSent) add("QSL_SENT", "Y")
        if (q.qslRcvd) add("QSL_RCVD", "Y")
        add("COMMENT", q.comment)
        add("STATION_CALLSIGN", q.myCall)
        if (Geo.isLocator(q.myLocator)) add("MY_GRIDSQUARE", q.myLocator)
        // The voice note stays on the phone; the name lets a re-import on the same phone link it back.
        add("APP_QSOLOG_AUDIO", q.audio)
        val written = out.map { it.first }.toSet()
        for ((k, v) in q.adif) if (k !in written) add(k, v)
        return out
    }

    // ---------- import ----------

    class ImportResult(val rows: List<Qso>, val skipped: Int, val charset: Charset)

    fun import(input: InputStream): ImportResult {
        val bytes = input.readBytes()
        val charset = detectCharset(bytes)
        val records = parseRecords(bytes, charset)
        var skipped = 0
        val rows = records.mapNotNull { r ->
            toQso(r) ?: run { skipped++; null }
        }
        return ImportResult(rows, skipped, charset)
    }

    fun toQso(r: Map<String, String>): Qso? {
        val call = r["CALL"]?.trim()?.uppercase().orEmpty()
        val date = r["QSO_DATE"]?.let { parseDate(it) }
        val time = r["TIME_ON"]?.let { parseTime(it) } ?: LocalTime.MIDNIGHT
        if (call.isEmpty() || date == null) return null
        val ts = LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli()

        val extra = linkedMapOf<String, String>()
        for ((k, v) in r) if (k !in CORE && v.isNotBlank()) extra[k] = v

        // MFSK + FT4 → FT4; SSB + USB → SSB with SUBMODE kept.
        var mode = r["MODE"]?.trim()?.uppercase().orEmpty()
        val sub = extra["SUBMODE"]?.trim()?.uppercase()
        if (sub != null && sub in SUBMODE_PARENT) {
            mode = sub
            extra.remove("SUBMODE")
        }

        val grid = r["GRIDSQUARE"]?.trim().orEmpty()
        val locator = if (Geo.isLocator(grid)) grid else ""
        if (grid.isNotEmpty() && locator.isEmpty()) extra["GRIDSQUARE"] = grid // not a valid locator: keep as is
        val myGrid = r["MY_GRIDSQUARE"]?.trim().orEmpty()
        val myLocator = if (Geo.isLocator(myGrid)) myGrid else ""
        if (myGrid.isNotEmpty() && myLocator.isEmpty()) extra["MY_GRIDSQUARE"] = myGrid

        val lat = r["LAT"]?.let { parseCoord(it) }
        val lon = r["LON"]?.let { parseCoord(it) }

        return Qso(
            call = call,
            timeUtc = ts,
            band = normalizeBand(r["BAND"]).ifEmpty { r["FREQ"]?.toDoubleOrNull()?.let { bandForFreq(it) }.orEmpty() },
            mode = mode,
            freqMhz = r["FREQ"]?.trim().orEmpty(),
            rstSent = r["RST_SENT"]?.trim().orEmpty(),
            rstRcvd = r["RST_RCVD"]?.trim().orEmpty(),
            name = r["NAME"]?.trim().orEmpty(),
            qth = r["QTH"]?.trim().orEmpty(),
            country = r["COUNTRY"]?.trim().orEmpty(),
            locator = locator,
            lat = if (lat != null && lon != null) lat else null,
            lon = if (lat != null && lon != null) lon else null,
            distanceKm = r["DISTANCE"]?.replace(',', '.')?.toDoubleOrNull(),
            power = r["TX_PWR"]?.trim().orEmpty(),
            qslSent = r["QSL_SENT"]?.trim()?.uppercase() == "Y",
            qslRcvd = r["QSL_RCVD"]?.trim()?.uppercase() == "Y",
            comment = r["COMMENT"]?.trim().orEmpty(),
            myCall = (r["STATION_CALLSIGN"] ?: r["OPERATOR"])?.trim()?.uppercase().orEmpty(),
            myLocator = myLocator,
            audio = r["APP_QSOLOG_AUDIO"]?.trim().orEmpty(),
            adif = extra,
        )
    }

    /** Records as field maps (upper-case names). Header fields before <EOH> are skipped. */
    fun parseRecords(bytes: ByteArray, charset: Charset): List<Map<String, String>> {
        val records = mutableListOf<Map<String, String>>()
        var current = linkedMapOf<String, String>()
        // Everything before <EOH> is the header; a file without <EOH> starts straight with the records.
        var i = indexOfTag(bytes, "EOH").coerceAtLeast(0)
        while (i < bytes.size) {
            if (bytes[i] != '<'.code.toByte()) { i++; continue }
            val close = bytes.indexOf('>'.code.toByte(), i + 1)
            if (close < 0) break
            val spec = String(bytes, i + 1, close - i - 1, Charsets.US_ASCII)
            val parts = spec.split(':')
            val name = parts[0].trim().uppercase()
            when {
                name == "EOH" -> { current = linkedMapOf(); i = close + 1 }
                name == "EOR" -> {
                    if (current.isNotEmpty()) records += current
                    current = linkedMapOf()
                    i = close + 1
                }
                parts.size >= 2 && parts[1].trim().toIntOrNull() != null -> {
                    val len = parts[1].trim().toInt()
                    val start = close + 1
                    var end = (start + len).coerceAtMost(bytes.size)
                    // Some programs count characters rather than bytes in UTF-8 files: extend to the next tag.
                    if (charset == Charsets.UTF_8) {
                        var j = end
                        while (j < bytes.size && bytes[j] != '<'.code.toByte()) j++
                        var k = j
                        while (k > end && bytes[k - 1].toInt().toChar().isWhitespace()) k--
                        end = k
                    }
                    current[name] = String(bytes, start, end - start, charset)
                    i = end
                }
                else -> i = close + 1
            }
        }
        if (current.isNotEmpty()) records += current
        return records
    }

    /** Position right after the given <TAG>, or -1. */
    private fun indexOfTag(bytes: ByteArray, tag: String): Int {
        val needle = "<$tag>"
        val text = String(bytes, Charsets.ISO_8859_1).uppercase(Locale.ROOT)
        val p = text.indexOf(needle)
        return if (p < 0) -1 else p + needle.length
    }

    private fun ByteArray.indexOf(b: Byte, from: Int): Int {
        for (i in from until size) if (this[i] == b) return i
        return -1
    }

    /** UTF-8 if the bytes are valid UTF-8 (pure ASCII included), otherwise Windows-1251. */
    fun detectCharset(bytes: ByteArray): Charset = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        Charsets.UTF_8
    } catch (e: CharacterCodingException) {
        WINDOWS_1251
    }

    // ---------- values ----------

    fun normalizeBand(b: String?): String {
        val v = b?.trim()?.lowercase().orEmpty()
        return BANDS.firstOrNull { it == v } ?: v
    }

    private fun parseDate(s: String): LocalDate? = try { LocalDate.parse(s.trim(), DATE) } catch (e: Exception) { null }

    private fun parseTime(s: String): LocalTime? {
        val v = s.trim()
        return try {
            when (v.length) {
                4 -> LocalTime.parse(v, DateTimeFormatter.ofPattern("HHmm"))
                6 -> LocalTime.parse(v, TIME)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** ADIF location: "N056 36.978" → 56.6163. */
    fun parseCoord(s: String): Double? {
        val m = Regex("""^\s*([NSEWnsew])\s*(\d{1,3})\s+(\d{1,2}(?:\.\d+)?)\s*$""").find(s) ?: return s.trim().toDoubleOrNull()
        val deg = m.groupValues[2].toDouble() + m.groupValues[3].toDouble() / 60
        return if (m.groupValues[1].uppercase() in setOf("S", "W")) -deg else deg
    }

    fun formatCoord(v: Double, pos: Char, neg: Char): String {
        val a = abs(v)
        val deg = a.toInt()
        val min = (a - deg) * 60
        return String.format(Locale.US, "%c%03d %06.3f", if (v < 0) neg else pos, deg, min)
    }

    /** ADIF date/time pair "20250108" + "160100" → "08.01.2025 16:01" style parts for display. */
    fun displayDate(adif: String?): String = adif?.let { parseDate(it) }?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).orEmpty()
    fun displayTime(adif: String?): String = adif?.let { parseTime(it) }?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty()
    fun adifDate(display: String): String? =
        try { LocalDate.parse(display.trim(), DateTimeFormatter.ofPattern("dd.MM.yyyy")).format(DATE) } catch (e: Exception) { null }
    fun adifTime(display: String): String? =
        try { LocalTime.parse(display.trim(), DateTimeFormatter.ofPattern("HH:mm")).format(TIME) } catch (e: Exception) { null }

    // ---------- storing extra fields ----------

    /** Extra fields as an ADIF fragment, "<STATE:2>MA <CNTY:5>MA-11", for a single DB/CSV column. Lengths in characters. */
    fun encodeFields(map: Map<String, String>): String =
        map.entries.filter { it.value.isNotEmpty() }.joinToString(" ") { (k, v) -> "<$k:${v.length}>$v" }

    fun decodeFields(s: String?): Map<String, String> {
        if (s.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        var i = 0
        while (true) {
            val open = s.indexOf('<', i)
            if (open < 0) break
            val close = s.indexOf('>', open)
            if (close < 0) break
            val parts = s.substring(open + 1, close).split(':')
            val len = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (len == null) { i = close + 1; continue }
            val end = (close + 1 + len).coerceAtMost(s.length)
            out[parts[0].trim().uppercase()] = s.substring(close + 1, end)
            i = end
        }
        return out
    }

    private class AdiWriter(private val out: OutputStream, private val charset: Charset) {
        private val buf = ByteArrayOutputStream()

        fun raw(s: String) {
            buf.write(s.toByteArray(charset))
        }

        /** Length is the byte count in the file's encoding, which is what readers of Windows-1251 and UTF-8 files expect. */
        fun field(name: String, value: String) {
            val bytes = value.toByteArray(charset)
            buf.write("<$name:${bytes.size}>".toByteArray(Charsets.US_ASCII))
            buf.write(bytes)
            buf.write(' '.code)
        }

        fun flush() {
            out.write(buf.toByteArray())
            out.flush()
        }
    }
}

/** Labels for extra ADIF fields shown in the contact card, grouped as they appear there. */
object AdifLabels {
    val THEM = linkedMapOf(
        "STATE" to "Область / регион",
        "CNTY" to "Район",
        "CONT" to "Континент",
        "CQZ" to "Зона CQ",
        "ITUZ" to "Зона ITU",
        "DXCC" to "Код DXCC",
        "QSL_VIA" to "QSL via",
        "EMAIL" to "E-mail",
    )
    val CONTACT = linkedMapOf(
        "SUBMODE" to "Подвид (SUBMODE)",
        "PROP_MODE" to "Вид прохождения",
        "NOTES" to "Заметки",
    )
    val MINE = linkedMapOf(
        "OPERATOR" to "Оператор",
        "MY_RIG" to "Трансивер",
        "MY_ANTENNA" to "Антенна",
        "MY_STATE" to "Моя область",
        "MY_CNTY" to "Мой район",
        "MY_CITY" to "Мой город",
    )
    val SPACE_WEATHER = linkedMapOf(
        "SFI" to "Индекс SFI",
        "A_INDEX" to "A-индекс",
        "K_INDEX" to "K-индекс",
    )
    /** End of the contact; filled from the start time unless a log brought its own. Not shown in the card. */
    val TIME_OFF = setOf("QSO_DATE_OFF", "TIME_OFF")

    /**
     * Fields that repeat the main ones (receive band/frequency = band/frequency, prefix = callsign).
     * Kept in the record and in exports, filled automatically, not shown in the card.
     */
    val DERIVED = setOf("BAND_RX", "FREQ_RX", "PFX")

    val KNOWN: Set<String> = THEM.keys + CONTACT.keys + MINE.keys + SPACE_WEATHER.keys + TIME_OFF + DERIVED
}
