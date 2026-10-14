package ru.r3xed.qsolog.cat

import ru.r3xed.qsolog.tr

/**
 * CAT: the app asks the transceiver for its frequency, mode and S-meter, as N1MM, Log4OM, WSJT-X and FT8CN do —
 * a short command every few hundred milliseconds, the answer goes into the new QSO card.
 */
enum class CatProtocol {
    /** Icom CI-V and radios that speak it (Xiegu). Binary frames FE FE <to> <from> <cmd> … FD. */
    ICOM,
    /** Kenwood text commands "FA;" "MD;" "SM0;" — also Elecraft and FlexRadio. */
    KENWOOD,
    /** Yaesu's newer text CAT (FT-991, FT-891, FT-710, FTDX10, FTDX101, FT-950, FT-450): "FA;" "MD0;" "SM0;". */
    YAESU,
    /** Yaesu's 5-byte binary CAT of FT-817, FT-818, FT-857, FT-897. */
    YAESU_OLD,
    /** Hamlib rigctld (and programs that imitate it: wfview, SDR++, flrig's rigctld bridge) over the network. */
    RIGCTLD;

    val title: String
        get() = when (this) {
            ICOM -> tr("ICOM CI-V и совместимые (Xiegu)")
            KENWOOD -> tr("Kenwood, Elecraft, FlexRadio")
            YAESU -> tr("Yaesu — новый CAT (FT-991, FT-710, FTDX10…)")
            YAESU_OLD -> tr("Yaesu FT-817 / 818 / 857 / 897")
            RIGCTLD -> tr("Hamlib rigctld (по сети)")
        }
}

/** How the app reaches the radio. USB and Bluetooth are Android's, SERIAL is the computer's COM port. */
enum class CatLinkType { USB, BLUETOOTH, SERIAL, NETWORK }

/** A transceiver in the model list: its protocol and the factory CI-V address and speed. */
data class RigModel(val name: String, val protocol: CatProtocol, val civ: Int = 0, val baud: Int = 19200) {
    /** Elecraft's S-meter counts 0–21 bars, Kenwood's 0–30. */
    val elecraft get() = name.startsWith("Elecraft")
}

object Rigs {
    const val OTHER_ICOM = "ICOM — другая модель"
    const val OTHER_KENWOOD = "Kenwood — другая модель"
    const val OTHER_YAESU = "Yaesu — другая модель (новый CAT)"
    const val RIGCTLD = "Hamlib rigctld / wfview / flrig"

    val ALL: List<RigModel> = listOf(
        RigModel("IC-705", CatProtocol.ICOM, 0xA4, 115200),
        RigModel("IC-7300", CatProtocol.ICOM, 0x94, 115200),
        RigModel("IC-7610", CatProtocol.ICOM, 0x98, 115200),
        RigModel("IC-9700", CatProtocol.ICOM, 0xA2, 115200),
        RigModel("IC-905", CatProtocol.ICOM, 0xAC, 115200),
        RigModel("IC-7100", CatProtocol.ICOM, 0x88, 19200),
        RigModel("IC-7851", CatProtocol.ICOM, 0x8E, 115200),
        RigModel("IC-7850", CatProtocol.ICOM, 0x8E, 115200),
        RigModel("IC-7800", CatProtocol.ICOM, 0x6A, 19200),
        RigModel("IC-7700", CatProtocol.ICOM, 0x74, 19200),
        RigModel("IC-7600", CatProtocol.ICOM, 0x7A, 19200),
        RigModel("IC-7410", CatProtocol.ICOM, 0x80, 19200),
        RigModel("IC-7200", CatProtocol.ICOM, 0x76, 19200),
        RigModel("IC-9100", CatProtocol.ICOM, 0x7C, 19200),
        RigModel("IC-7000", CatProtocol.ICOM, 0x70, 19200),
        RigModel("IC-756PRO III", CatProtocol.ICOM, 0x6E, 19200),
        RigModel("IC-756PRO II", CatProtocol.ICOM, 0x64, 19200),
        RigModel("IC-746PRO", CatProtocol.ICOM, 0x66, 9600),
        RigModel("IC-910H", CatProtocol.ICOM, 0x60, 9600),
        RigModel("IC-718", CatProtocol.ICOM, 0x5E, 9600),
        RigModel("IC-706MKIIG", CatProtocol.ICOM, 0x58, 9600),
        RigModel("IC-703", CatProtocol.ICOM, 0x68, 9600),
        RigModel("IC-R8600", CatProtocol.ICOM, 0x96, 115200),
        RigModel("Xiegu X6100", CatProtocol.ICOM, 0x70, 19200),
        RigModel("Xiegu X6200", CatProtocol.ICOM, 0x70, 19200),
        RigModel("Xiegu G90", CatProtocol.ICOM, 0x88, 19200),
        RigModel("Xiegu X5105", CatProtocol.ICOM, 0x70, 19200),
        RigModel("Xiegu X108G", CatProtocol.ICOM, 0x70, 19200),
        RigModel(OTHER_ICOM, CatProtocol.ICOM, 0x94, 19200),
        RigModel("TS-590S / SG", CatProtocol.KENWOOD, baud = 115200),
        RigModel("TS-890S", CatProtocol.KENWOOD, baud = 115200),
        RigModel("TS-990S", CatProtocol.KENWOOD, baud = 115200),
        RigModel("TS-480", CatProtocol.KENWOOD, baud = 57600),
        RigModel("TS-2000", CatProtocol.KENWOOD, baud = 57600),
        RigModel("Elecraft K3 / K3S", CatProtocol.KENWOOD, baud = 38400),
        RigModel("Elecraft K4", CatProtocol.KENWOOD, baud = 38400),
        RigModel("Elecraft KX2 / KX3", CatProtocol.KENWOOD, baud = 38400),
        RigModel("FlexRadio (SmartSDR CAT)", CatProtocol.KENWOOD, baud = 9600),
        RigModel(OTHER_KENWOOD, CatProtocol.KENWOOD, baud = 9600),
        RigModel("FT-991 / FT-991A", CatProtocol.YAESU, baud = 38400),
        RigModel("FT-891", CatProtocol.YAESU, baud = 38400),
        RigModel("FT-710", CatProtocol.YAESU, baud = 38400),
        RigModel("FTDX10", CatProtocol.YAESU, baud = 38400),
        RigModel("FTDX101D / MP", CatProtocol.YAESU, baud = 38400),
        RigModel("FTDX3000", CatProtocol.YAESU, baud = 38400),
        RigModel("FT-950 / FT-2000", CatProtocol.YAESU, baud = 38400),
        RigModel("FT-450D", CatProtocol.YAESU, baud = 38400),
        RigModel(OTHER_YAESU, CatProtocol.YAESU, baud = 38400),
        RigModel("FT-817 / FT-818", CatProtocol.YAESU_OLD, baud = 9600),
        RigModel("FT-857 / FT-897", CatProtocol.YAESU_OLD, baud = 9600),
        RigModel(RIGCTLD, CatProtocol.RIGCTLD),
    )

    fun byName(name: String): RigModel = ALL.firstOrNull { it.name == name } ?: ALL.first { it.name == "IC-7300" }

    /** CI-V addresses tried when the set one does not answer: the factory ones, the commonest first. */
    val CIV_PROBE: List<Int> = (listOf(0x94, 0xA4, 0x98, 0xA2, 0x88, 0x70) + ALL.filter { it.protocol == CatProtocol.ICOM }.map { it.civ }).distinct()

    val BAUDS = listOf(4800, 9600, 19200, 38400, 57600, 115200)

    fun title(m: RigModel): String = when (m.name) {
        OTHER_ICOM -> tr("ICOM — другая модель")
        OTHER_KENWOOD -> tr("Kenwood — другая модель")
        OTHER_YAESU -> tr("Yaesu — другая модель (новый CAT)")
        else -> m.name
    }
}

/** CAT settings, the same on the phone and the computer. Stored as text keys (see [toMap]). */
data class CatConfig(
    val enabled: Boolean = false,
    val rig: String = "IC-7300",
    val civAddress: Int = 0x94,
    val baud: Int = 115200,
    val link: CatLinkType = CatLinkType.USB,
    /** USB device name (Android), Bluetooth address, or the computer's serial port. */
    val device: String = "",
    val host: String = "127.0.0.1",
    val port: Int = 4532,
    /** The new QSO card takes the radio's frequency and band, and its mode, while it is open. */
    val followFreq: Boolean = true,
    val followMode: Boolean = true,
    /** The S digit of the report sent follows the S-meter while the card is open. */
    val sMeterToRst: Boolean = false,
) {
    val model get() = Rigs.byName(rig)
    val protocol get() = model.protocol

    fun toMap(): Map<String, String> = mapOf(
        "cat_enabled" to enabled.toString(), "cat_rig" to rig, "cat_civ" to civAddress.toString(16),
        "cat_baud" to baud.toString(), "cat_link" to link.name, "cat_device" to device, "cat_host" to host,
        "cat_port" to port.toString(), "cat_follow_freq" to followFreq.toString(), "cat_follow_mode" to followMode.toString(),
        "cat_smeter_rst" to sMeterToRst.toString(),
    )

    companion object {
        fun fromMap(m: Map<String, String?>, defaultLink: CatLinkType): CatConfig {
            val d = CatConfig(link = defaultLink)
            return CatConfig(
                enabled = m["cat_enabled"]?.toBooleanStrictOrNull() ?: d.enabled,
                rig = m["cat_rig"]?.takeIf { n -> Rigs.ALL.any { it.name == n } } ?: d.rig,
                civAddress = m["cat_civ"]?.toIntOrNull(16)?.takeIf { it in 1..0xDF } ?: d.civAddress,
                baud = m["cat_baud"]?.toIntOrNull() ?: d.baud,
                link = m["cat_link"]?.let { v -> CatLinkType.entries.firstOrNull { it.name == v } } ?: d.link,
                device = m["cat_device"] ?: d.device,
                host = m["cat_host"]?.ifBlank { null } ?: d.host,
                port = m["cat_port"]?.toIntOrNull() ?: d.port,
                followFreq = m["cat_follow_freq"]?.toBooleanStrictOrNull() ?: d.followFreq,
                followMode = m["cat_follow_mode"]?.toBooleanStrictOrNull() ?: d.followMode,
                sMeterToRst = m["cat_smeter_rst"]?.toBooleanStrictOrNull() ?: d.sMeterToRst,
            )
        }

        val KEYS = CatConfig().toMap().keys
    }
}

/**
 * What the radio reported. [radioMode] is the radio's own name of the mode (USB, CW-R, DATA-U, …),
 * [sDb] the S-meter in dB over S0 (6 dB per S unit: S9 = 54, S9+20 = 74).
 */
data class RigState(
    val freqHz: Long? = null,
    val radioMode: String? = null,
    val sDb: Int? = null,
    val at: Long = System.currentTimeMillis(),
) {
    /** The frequency as the card keeps it: MHz, no trailing zeros ("14.074", "7.0125"). */
    val freqMhz: String? get() = freqHz?.let { CatFormat.mhz(it) }
}

sealed interface CatStatus {
    data object Off : CatStatus
    data class Connecting(val what: String) : CatStatus
    /** The link is open. [state] is null until the radio answers; [silent] — it has stopped answering. */
    data class Connected(val what: String, val state: RigState?, val silent: Boolean = false) : CatStatus
    data class Failed(val message: String) : CatStatus
}

object CatFormat {
    fun mhz(hz: Long): String = java.math.BigDecimal(hz).movePointLeft(6).stripTrailingZeros().toPlainString().let {
        if (it.contains('.')) it else "$it.0"
    }

    /** "14 074.500" — kHz with the hertz, as radios show it. */
    fun khz(hz: Long): String {
        val k = hz / 1000
        val h = (hz % 1000).toInt()
        val ks = k.toString().reversed().chunked(3).joinToString(" ").reversed()
        return "%s.%03d".format(java.util.Locale.ROOT, ks, h)
    }

    /** "S7", "S9+20". */
    fun sMeter(db: Int): String {
        if (db < 54) return "S" + ((db + 3) / 6).coerceIn(0, 9)
        // Over S9 in tens, as the radio's scale is marked.
        val over = (db - 54 + 5) / 10 * 10
        return if (over == 0) "S9" else "S9+$over"
    }

    /** The S digit of a report: 1–9. */
    fun sDigit(db: Int): Int = ((db + 3) / 6).coerceIn(1, 9)

    /** [rst] ("59", "599") with its S digit from the S-meter; other reports (dB of FT8, contest codes) as they are. */
    fun rstWithS(rst: String, db: Int): String =
        if ((rst.length == 2 || rst.length == 3) && rst.all { it.isDigit() }) rst.substring(0, 1) + sDigit(db) + rst.substring(2) else rst

    /** A value of a radio's S-meter scale in dB over S0: [s9] is the reading at S9, [max] at S9+60. */
    fun scale(v: Int, s9: Int, max: Int): Int = if (v <= s9) v * 54 / s9 else 54 + (v - s9) * 60 / (max - s9)
}

/** The radio's mode → the log's. */
object CatModes {
    private val DIGITAL = setOf("FT8", "FT4", "PSK31", "JT65", "JS8", "Q65", "OLIVIA", "SSTV", "DIGI", "RTTY")

    /**
     * The log's mode for [radio], or null for one the log has no name for. Data modes (USB-D, PKTUSB, DATA-U) keep
     * a digital mode the card already has — FT8, FT4, PSK31, a radio cannot tell them apart — and else are FT8.
     */
    fun toLog(radio: String, current: String): String? {
        val r = radio.uppercase()
        return when {
            r == "LSB" || r == "USB" || r == "SSB" -> "SSB"
            r.startsWith("CW") -> "CW"
            r.startsWith("RTTY") || r.startsWith("FSK") -> "RTTY"
            r == "PSK" || r == "PSK-R" -> "PSK31"
            r.startsWith("DATA") || r.startsWith("PKT") || r.endsWith("-D") || r == "DIG" || r == "DD" ->
                if (current in DIGITAL && current != "RTTY") current else "FT8"
            r.startsWith("AM") || r == "SAM" || r == "DSB" -> "AM"
            r.startsWith("FM") || r == "WFM" || r == "NFM" -> "FM"
            r == "DV" -> "DSTAR"
            else -> null
        }
    }
}

/** The status line of the CAT settings and the QSO card. */
fun CatStatus.describe(): String = when (this) {
    CatStatus.Off -> tr("выключено")
    is CatStatus.Connecting -> tr("подключаюсь: %s…", what)
    is CatStatus.Connected -> when {
        silent -> tr("%s не отвечает: проверьте, включён ли трансивер, скорость порта и адрес CI-V", what)
        state == null -> tr("соединение есть, жду ответа: %s", what)
        else -> listOfNotNull(what, state.freqHz?.let { CatFormat.khz(it) + " " + tr("кГц") }, state.radioMode, state.sDb?.let { CatFormat.sMeter(it) }).joinToString(" · ")
    }
    is CatStatus.Failed -> message
}

/** The radio's line in the QSO card: "14 074.000 · USB-D · S7". */
fun RigState.short(): String = listOfNotNull(freqHz?.let { CatFormat.khz(it) }, radioMode, sDb?.let { CatFormat.sMeter(it) }).joinToString(" · ")
