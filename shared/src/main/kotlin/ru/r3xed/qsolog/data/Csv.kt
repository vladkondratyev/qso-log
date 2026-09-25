package ru.r3xed.qsolog.data

import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * CSV export and import of the whole log.
 * Semicolon-separated, UTF-8 with BOM, so Excel in Russian locale opens it with correct columns and Cyrillic.
 * Import accepts both ';' and ',' separators and recognises columns by header name, in any order.
 */
object Csv {
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val STAMP = DateTimeFormatter.ISO_INSTANT

    private val HEADER = listOf(
        "call", "date_utc", "time_utc", "band", "mode", "freq_mhz", "rst_sent", "rst_rcvd",
        "name", "qth", "country", "locator", "lat", "lon", "distance_km", "bearing",
        "power", "qsl_sent", "qsl_rcvd", "comment", "my_call", "my_locator", "created_utc", "updated_utc",
        "adif_extra",
    )

    fun export(list: List<Qso>, out: OutputStream) {
        val w = out.bufferedWriter(Charsets.UTF_8)
        w.write("﻿")
        w.write(HEADER.joinToString(";")); w.write("\r\n")
        for (q in list.sortedBy { it.timeUtc }) {
            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(q.timeUtc), ZoneOffset.UTC)
            val row = listOf(
                q.call, DATE.format(t), TIME.format(t), q.band, q.mode, q.freqMhz, q.rstSent, q.rstRcvd,
                q.name, q.qth, q.country, q.locator, q.lat.fmt(6), q.lon.fmt(6), q.distanceKm.fmt(1), q.bearing.fmt(0),
                q.power, yn(q.qslSent), yn(q.qslRcvd), q.comment, q.myCall, q.myLocator,
                STAMP.format(Instant.ofEpochMilli(q.createdAt)), STAMP.format(Instant.ofEpochMilli(q.updatedAt)),
                Adif.encodeFields(q.adif),
            )
            w.write(row.joinToString(";") { quote(it) }); w.write("\r\n")
        }
        w.flush()
    }

    class ImportResult(val rows: List<Qso>, val skipped: Int)

    fun import(input: InputStream): ImportResult {
        val text = input.bufferedReader(Charsets.UTF_8).readText().removePrefix("﻿")
        val firstLine = text.lineSequence().firstOrNull() ?: return ImportResult(emptyList(), 0)
        val sep = if (firstLine.count { it == ';' } >= firstLine.count { it == ',' }) ';' else ','
        val records = parse(text, sep)
        if (records.isEmpty()) return ImportResult(emptyList(), 0)
        val idx = records[0].mapIndexed { i, h -> h.trim().lowercase() to i }.toMap()
        fun List<String>.col(name: String) = idx[name]?.let { getOrNull(it) }?.trim().orEmpty()

        var skipped = 0
        val rows = records.drop(1).filter { r -> r.any { it.isNotBlank() } }.mapNotNull { r ->
            val call = r.col("call").uppercase()
            val time = parseTime(r.col("date_utc"), r.col("time_utc"))
            if (call.isEmpty() || time == null) {
                skipped++; return@mapNotNull null
            }
            Qso(
                call = call, timeUtc = time,
                band = r.col("band"), mode = r.col("mode").uppercase(), freqMhz = r.col("freq_mhz"),
                rstSent = r.col("rst_sent"), rstRcvd = r.col("rst_rcvd"),
                name = r.col("name"), qth = r.col("qth"), country = r.col("country"), locator = r.col("locator"),
                lat = r.col("lat").num(), lon = r.col("lon").num(),
                distanceKm = r.col("distance_km").num(), bearing = r.col("bearing").num(),
                power = r.col("power"),
                qslSent = r.col("qsl_sent").isYes(), qslRcvd = r.col("qsl_rcvd").isYes(),
                comment = r.col("comment"), myCall = r.col("my_call"), myLocator = r.col("my_locator"),
                createdAt = r.col("created_utc").instant() ?: System.currentTimeMillis(),
                updatedAt = r.col("updated_utc").instant() ?: System.currentTimeMillis(),
                adif = Adif.decodeFields(r.col("adif_extra")),
            )
        }
        return ImportResult(rows, skipped)
    }

    private fun parseTime(date: String, time: String): Long? {
        val d = date.trim()
        val t = time.trim().ifEmpty { "00:00" }
        val datePatterns = listOf("yyyy-MM-dd", "dd.MM.yyyy", "yyyyMMdd")
        val timePatterns = listOf("HH:mm:ss", "HH:mm", "HHmmss", "HHmm")
        for (dp in datePatterns) for (tp in timePatterns) {
            try {
                val ldt = LocalDateTime.parse("$d $t", DateTimeFormatter.ofPattern("$dp $tp"))
                return ldt.toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** RFC 4180 style parser: handles quoted fields with separators, quotes and line breaks. */
    private fun parse(text: String, sep: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                sep -> { row.add(field.toString()); field.clear() }
                '\r' -> Unit
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }

    private fun quote(s: String) =
        if (s.any { it == ';' || it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun Double?.fmt(digits: Int) = this?.let { String.format(java.util.Locale.US, "%.${digits}f", it) } ?: ""
    private fun String.num() = replace(',', '.').toDoubleOrNull()
    private fun yn(b: Boolean) = if (b) "Y" else "N"
    private fun String.isYes() = trim().uppercase() in setOf("Y", "YES", "1", "TRUE", "ДА", "Д")
    private fun String.instant() = try { Instant.parse(this).toEpochMilli() } catch (_: Exception) { null }
}
