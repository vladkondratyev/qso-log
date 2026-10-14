package ru.r3xed.qsolog.digi

import ru.r3xed.qsolog.data.Adif
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.bandForFreq
import ru.r3xed.qsolog.cat.CatFormat
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Contacts from digital-mode programs: WSJT-X, JTDX, MSHV (and others speaking the WSJT-X UDP protocol) send every
 * logged contact to a UDP port, 2237 by default. Also plain ADIF text in a datagram — WSJT-X's "secondary UDP server",
 * GridTracker, JS8Call — is taken.
 *
 * Protocol: WSJT-X source, Network/NetworkMessage.hpp. Big-endian Qt QDataStream: magic 0xADBCCBDA, schema, type,
 * the sender's id, then the fields of the type. utf8 strings are a quint32 byte count (0xFFFFFFFF — null) and bytes.
 */
object Wsjtx {
    const val MAGIC = 0xADBCCBDA.toInt()
    const val DEFAULT_PORT = 2237

    sealed interface Message {
        val id: String

        /** Type 0: sent every 15 s while the program runs. */
        data class Heartbeat(override val id: String, val version: String) : Message

        /** Type 5 ("QSO Logged") or 12 ("Logged ADIF"), or a plain ADIF datagram. */
        data class Logged(override val id: String, val qsos: List<Qso>) : Message

        /** Type 6: the program was closed. */
        data class Close(override val id: String) : Message

        data class Other(override val id: String, val type: Int) : Message
    }

    /** A datagram as a message, or null when it is neither the WSJT-X protocol nor ADIF. */
    fun parse(bytes: ByteArray, length: Int = bytes.size): Message? {
        val data = if (length == bytes.size) bytes else bytes.copyOf(length)
        if (data.size >= 4 && DataInputStream(ByteArrayInputStream(data)).readInt() == MAGIC) return parseBinary(data)
        return parseAdif(data, "ADIF")
    }

    private fun parseBinary(data: ByteArray): Message? = try {
        val r = Reader(data)
        r.int() // magic
        r.int() // schema
        val type = r.int()
        val id = r.utf8()
        when (type) {
            0 -> {
                r.int() // max schema
                Message.Heartbeat(id, r.utf8())
            }
            5 -> Message.Logged(id, listOfNotNull(qsoLogged(r)))
            6 -> Message.Close(id)
            12 -> parseAdif(r.utf8().toByteArray(Charsets.UTF_8), id)
            else -> Message.Other(id, type)
        }
    } catch (e: EOFException) {
        null
    }

    private fun parseAdif(data: ByteArray, id: String): Message.Logged? {
        val text = String(data, Charsets.UTF_8)
        if (!text.contains("<EOR>", ignoreCase = true) && !text.contains("<CALL:", ignoreCase = true)) return null
        val charset = Adif.detectCharset(data)
        val qsos = Adif.parseRecords(data, charset).mapNotNull { Adif.toQso(it) }
        return if (qsos.isEmpty()) null else Message.Logged(id, qsos)
    }

    /** Type 5: the fields in the order WSJT-X writes them. */
    private fun qsoLogged(r: Reader): Qso? {
        val off = r.dateTime()
        val call = r.utf8().trim().uppercase()
        val grid = r.utf8().trim()
        val freqHz = r.long()
        val mode = r.utf8().trim().uppercase()
        val sent = r.utf8().trim()
        val rcvd = r.utf8().trim()
        val power = r.utf8().trim()
        val comment = r.utf8().trim()
        val name = r.utf8().trim()
        val on = r.dateTime() ?: off
        val operator = r.utfOrEmpty().trim().uppercase()
        val myCall = r.utfOrEmpty().trim().uppercase()
        val myGrid = r.utfOrEmpty().trim()
        val stx = r.utfOrEmpty().trim()
        val srx = r.utfOrEmpty().trim()
        val prop = r.utfOrEmpty().trim()
        if (call.isEmpty() || on == null) return null
        // Through ADIF, so the mode, locators and the rest go the same way as in an imported log.
        val f = linkedMapOf<String, String>()
        fun put(k: String, v: String?) { if (!v.isNullOrBlank()) f[k] = v }
        put("CALL", call)
        put("QSO_DATE", on.toLocalDate().toString().replace("-", ""))
        put("TIME_ON", "%02d%02d%02d".format(on.hour, on.minute, on.second))
        off?.let {
            put("QSO_DATE_OFF", it.toLocalDate().toString().replace("-", ""))
            put("TIME_OFF", "%02d%02d%02d".format(it.hour, it.minute, it.second))
        }
        if (freqHz > 0) {
            val mhz = CatFormat.mhz(freqHz)
            put("FREQ", mhz)
            put("BAND", bandForFreq(mhz.toDouble()))
        }
        val parent = SUBMODES[mode]
        if (parent != null) { put("MODE", parent); put("SUBMODE", mode) } else put("MODE", mode)
        put("GRIDSQUARE", grid)
        put("RST_SENT", sent)
        put("RST_RCVD", rcvd)
        put("TX_PWR", power)
        put("COMMENT", comment)
        put("NAME", name)
        put("OPERATOR", operator)
        put("STATION_CALLSIGN", myCall)
        put("MY_GRIDSQUARE", myGrid)
        put("STX_STRING", stx)
        put("SRX_STRING", srx)
        put("PROP_MODE", prop)
        return Adif.toQso(f)
    }

    /** WSJT-X names the mode itself ("FT4"); ADIF writes these as MODE + SUBMODE. */
    private val SUBMODES = mapOf("FT4" to "MFSK", "JS8" to "MFSK", "Q65" to "MFSK", "FST4" to "MFSK", "FST4W" to "MFSK", "PSK31" to "PSK")

    private class Reader(data: ByteArray) {
        private val s = DataInputStream(ByteArrayInputStream(data))
        fun int() = s.readInt()
        fun long() = s.readLong()

        fun utf8(): String {
            val n = s.readInt()
            if (n == -1 || n == 0) return ""
            val b = ByteArray(n)
            s.readFully(b)
            return String(b, Charsets.UTF_8)
        }

        /** A field newer programs add at the end: older ones (and JTDX, MSHV) may stop before it. */
        fun utfOrEmpty(): String = try { utf8() } catch (e: EOFException) { "" }

        /** QDateTime: Julian day (qint64), ms since midnight (quint32), time spec (0 local, 1 UTC, 2 offset + qint32). */
        fun dateTime(): LocalDateTime? {
            val jd = s.readLong()
            val ms = s.readInt().toLong() and 0xFFFFFFFFL
            val spec = s.readUnsignedByte()
            val offsetS = if (spec == 2) s.readInt() else 0
            if (jd <= 0 || ms >= 86_400_000L) return null
            val t = LocalDateTime.of(LocalDate.ofEpochDay(jd - JD_EPOCH), LocalTime.ofNanoOfDay(ms * 1_000_000))
            return when (spec) {
                0 -> t.atZone(java.time.ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
                2 -> t.minusSeconds(offsetS.toLong())
                else -> t
            }
        }
    }

    /** The Julian day of 1970-01-01. */
    private const val JD_EPOCH = 2_440_588L
}
