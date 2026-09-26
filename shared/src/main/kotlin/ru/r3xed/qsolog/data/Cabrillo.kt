package ru.r3xed.qsolog.data

import java.io.InputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Contest reports: ЕРМАК (https://ermak.srr.ru/, the Russian contest format) and Cabrillo 3.0 it is based on.
 * Same keywords and the same fixed-column QSO line; ЕРМАК is UTF-8 with Cyrillic allowed in the header,
 * has its own mode codes (PS = PSK31, OL = OLIVIA, TV = SSTV, …), Russian CONTEST codes and RDA in LOCATION.
 * Cabrillo is ASCII and knows only CW, PH, FM, RY, DG.
 */
object Cabrillo {
    enum class Format(val title: String, val extension: String) {
        ERMAK("ЕРМАК", "txt"),
        CABRILLO("Cabrillo 3.0", "cbr"),
    }

    /** What goes into the header; the rest comes from the records. */
    data class Header(
        val format: Format,
        /** CONTEST code from the contest rules (RDXC, R3X-CHAMP, CQ-WW-CW, …). */
        val contest: String,
        val callsign: String,
        /** SINGLE-OP, MULTI-OP, MULTI-OP-2, CHECKLOG. */
        val categoryOperator: String = "SINGLE-OP",
        /** RDA for ЕРМАК (e.g. "KG03"), section/state for Cabrillo; empty to leave the line out. */
        val location: String = "",
        val operators: String = "",
        val createdBy: String = "QSO-LOG",
    )

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm")

    /** QSO-LOG mode → code in the QSO line. Modes without a code of their own go as digital (DG). */
    fun modeCode(mode: String, format: Format): String = when (mode.uppercase()) {
        "CW" -> "CW"
        "SSB", "AM" -> "PH"
        "FM" -> "FM"
        "RTTY" -> "RY"
        else -> if (format == Format.CABRILLO) "DG" else when (mode.uppercase()) {
            "PSK31" -> "PS"
            "OLIVIA" -> "OL"
            "SSTV" -> "TV"
            else -> "DG"
        }
    }

    private fun modeFromCode(code: String): String = when (code.uppercase()) {
        "CW" -> "CW"
        "PH" -> "SSB"
        "FM" -> "FM"
        "RY", "RM" -> "RTTY"
        "PS", "PM", "PO" -> "PSK31"
        "OL" -> "OLIVIA"
        "TV" -> "SSTV"
        else -> "DIGI"
    }

    /** Frequency column: kHz below 30 MHz, band designator above (50, 144, 432, 1.2G). */
    fun freqField(q: Qso): String {
        val mhz = q.freqMhz.replace(',', '.').toDoubleOrNull()
        val band = q.band.lowercase()
        val vhf = mapOf("6m" to "50", "4m" to "70", "2m" to "144", "70cm" to "432", "23cm" to "1.2G")
        vhf[band]?.let { return it }
        if (mhz != null && mhz >= 30) return when {
            mhz < 60 -> "50"
            mhz < 100 -> "70"
            mhz < 200 -> "144"
            mhz < 500 -> "432"
            else -> "1.2G"
        }
        if (mhz != null && mhz > 0) return Math.round(mhz * 1000).toString()
        return mapOf(
            "2200m" to "136", "630m" to "472", "160m" to "1800", "80m" to "3500", "60m" to "5351", "40m" to "7000",
            "30m" to "10100", "20m" to "14000", "17m" to "18068", "15m" to "21000", "12m" to "24890", "10m" to "28000",
        )[band] ?: "0"
    }

    private fun categoryBand(list: List<Qso>): String {
        val bands = list.map { it.band.uppercase() }.filter { it.isNotBlank() }.distinct()
        return if (bands.size == 1) bands[0] else "ALL"
    }

    private fun categoryMode(list: List<Qso>, format: Format): String {
        val codes = list.map { modeCode(it.mode, format) }.distinct()
        return when {
            codes.size != 1 -> "MIXED"
            codes[0] == "CW" -> "CW"
            codes[0] == "PH" -> "SSB"
            codes[0] == "FM" -> "FM"
            else -> if (format == Format.CABRILLO && codes[0] == "RY") "RTTY" else "DIGI"
        }
    }

    /** Cabrillo is ASCII: Cyrillic in the header is transliterated, anything else non-ASCII dropped. */
    private fun ascii(s: String): String {
        val map = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z",
            'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
            'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
            'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
        )
        return buildString {
            for (c in s) {
                val t = map[c.lowercaseChar()]
                when {
                    t != null -> append(if (c.isUpperCase()) t.replaceFirstChar { it.uppercase() } else t)
                    c.code < 128 -> append(c)
                }
            }
        }
    }

    /** Sent and received exchange of a record: the contest number fields if the log has them. */
    private fun sentExch(q: Qso, serial: Int): String =
        q.adif["STX_STRING"]?.ifBlank { null } ?: q.adif["STX"]?.ifBlank { null } ?: "%03d".format(serial)

    private fun rcvdExch(q: Qso): String = q.adif["SRX_STRING"]?.ifBlank { null } ?: q.adif["SRX"]?.ifBlank { null } ?: ""

    private fun col(s: String, width: Int) = s.padEnd(width)

    /** The whole report as text; records are put in chronological order, as both formats require. */
    fun export(list: List<Qso>, h: Header): String {
        val clean: (String) -> String = { if (h.format == Format.CABRILLO) ascii(it) else it }
        val sorted = list.sortedBy { it.timeUtc }
        val sb = StringBuilder()
        fun line(key: String, value: String) { if (value.isNotBlank()) sb.append(key).append(": ").append(clean(value.trim())).append("\r\n") }
        sb.append("START-OF-LOG: 3.0\r\n")
        line("CONTEST", h.contest.uppercase().ifBlank { "NONE" })
        line("CALLSIGN", h.callsign.uppercase())
        line("CATEGORY-OPERATOR", h.categoryOperator)
        line("CATEGORY-BAND", categoryBand(sorted))
        line("CATEGORY-MODE", categoryMode(sorted, h.format))
        line("LOCATION", h.location)
        line("OPERATORS", h.operators)
        line("CREATED-BY", h.createdBy)
        sorted.forEachIndexed { i, q ->
            val t = LocalDateTime.ofEpochSecond(q.timeUtc / 1000, 0, ZoneOffset.UTC)
            val my = (q.myCall.ifBlank { h.callsign }).uppercase()
            sb.append("QSO: ")
                .append(freqField(q).padStart(5)).append(' ')
                .append(col(modeCode(q.mode, h.format), 2)).append(' ')
                .append(DATE.format(t)).append(' ')
                .append(TIME.format(t)).append(' ')
                .append(col(my, 13)).append(' ')
                .append(col(q.rstSent.ifBlank { "59" }, 3)).append(' ')
                .append(col(sentExch(q, i + 1), 6)).append(' ')
                .append(col(q.call.uppercase(), 13)).append(' ')
                .append(col(q.rstRcvd.ifBlank { "59" }, 3)).append(' ')
                .append(rcvdExch(q))
            // Trailing spaces from the padding mean nothing; keep lines clean.
            while (sb.endsWith(" ")) sb.setLength(sb.length - 1)
            sb.append("\r\n")
        }
        sb.append("END-OF-LOG:\r\n")
        return sb.toString()
    }

    fun charset(format: Format): Charset = if (format == Format.ERMAK) Charsets.UTF_8 else Charsets.US_ASCII

    data class ImportResult(val rows: List<Qso>, val skipped: Int, val contest: String, val callsign: String)

    private val CALL = Regex("^(?=.*\\d)(?=.*[A-Z])[A-Z0-9/]{3,}$")
    private val GRID = Regex("^[A-R]{2}\\d{2}([A-X]{2})?$")
    private val RST = Regex("^-?\\d{2,3}$")

    /** Reads a ЕРМАК or Cabrillo report (UTF-8, or Windows-1251 if it is not valid UTF-8). */
    fun import(input: InputStream): ImportResult {
        val bytes = input.readBytes()
        val text = try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            String(bytes, Charset.forName("windows-1251"))
        }.removePrefix("﻿")
        var contest = ""
        var callsign = ""
        val rows = mutableListOf<Qso>()
        var skipped = 0
        for (raw in text.lines()) {
            val lineText = raw.trim()
            val key = lineText.substringBefore(':').uppercase()
            when (key) {
                "CONTEST" -> contest = lineText.substringAfter(':').trim()
                "CALLSIGN" -> callsign = lineText.substringAfter(':').trim().uppercase()
                "QSO", "X-QSO" -> {
                    if (key == "X-QSO") continue // lines the author marked as not claimed
                    val q = parseQso(lineText.substringAfter(':').trim(), contest, callsign)
                    if (q == null) skipped++ else rows += q
                }
            }
        }
        return ImportResult(rows, skipped, contest, callsign)
    }

    /**
     * "freq mo date time mycall <sent exchange…> call <received exchange…> [transmitter]".
     * The number of exchange fields depends on the contest, so the other station's call is found as the
     * callsign-looking token closest to the middle of what follows my call.
     */
    private fun parseQso(body: String, contest: String, callsign: String): Qso? {
        val t = body.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.uppercase() }
        if (t.size < 6) return null
        val freqTok = t[0]
        val mode = modeFromCode(t[1])
        val date = runCatching { LocalDate.parse(t[2], DATE) }.getOrNull() ?: return null
        val time = runCatching { LocalTime.parse(t[3].padStart(4, '0'), TIME) }.getOrNull() ?: return null
        val my = t[4]
        val rest = t.drop(5)
        val candidates = rest.indices.filter { it >= 1 && CALL.matches(rest[it]) && !GRID.matches(rest[it]) && !RST.matches(rest[it]) }
        val mid = (rest.size - 1) / 2.0
        val ci = candidates.minByOrNull { kotlin.math.abs(it - mid) } ?: return null
        val sent = rest.subList(0, ci)
        var rcvd = rest.subList(ci + 1, rest.size)
        // A trailing single 0/1 after a symmetric exchange is the transmitter number (MULTI-TWO).
        if (rcvd.size == sent.size + 1 && rcvd.last() in setOf("0", "1")) rcvd = rcvd.dropLast(1)
        fun split(x: List<String>): Pair<String, String> =
            if (x.isNotEmpty() && RST.matches(x[0]) && x.size > 1) x[0] to x.drop(1).joinToString(" ")
            else "" to x.joinToString(" ")
        val (rstS, exS) = split(sent)
        val (rstR, exR) = split(rcvd)
        val khz = freqTok.toDoubleOrNull()
        val vhf = mapOf("50" to "6m", "70" to "4m", "144" to "2m", "432" to "70cm", "1.2G" to "23cm")
        val band: String
        val freqMhz: String
        if (vhf.containsKey(freqTok)) {
            band = vhf.getValue(freqTok); freqMhz = ""
        } else {
            val mhz = (khz ?: 0.0) / 1000
            band = bandForFreq(mhz) ?: ""
            // A band edge (1800, 3500, …) stands for the band, not a real frequency.
            val edges = setOf("136", "472", "1800", "3500", "5351", "7000", "10100", "14000", "18068", "21000", "24890", "28000")
            freqMhz = if (khz == null || freqTok in edges) "" else "%.3f".format(java.util.Locale.ROOT, mhz)
        }
        val extra = buildMap {
            if (exS.isNotBlank()) put("STX_STRING", exS)
            if (exR.isNotBlank()) put("SRX_STRING", exR)
            if (contest.isNotBlank() && !contest.equals("NONE", true)) put("CONTEST_ID", contest)
        }
        return Qso(
            call = rest[ci],
            timeUtc = LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli(),
            band = band, mode = mode, freqMhz = freqMhz,
            rstSent = rstS, rstRcvd = rstR,
            myCall = my.ifBlank { callsign },
            adif = extra,
        )
    }
}
