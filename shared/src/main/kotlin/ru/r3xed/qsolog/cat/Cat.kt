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
    RIGCTLD,
    /** DXLab Commander (Windows), which holds the radio itself and takes directives over TCP. */
    COMMANDER,
    /** OmniRig (Windows): a COM server holding the radio; QSO-LOG asks it through PowerShell. */
    OMNIRIG;

    /** Reached over the network only, never a port of its own. */
    val network: Boolean get() = this == RIGCTLD || this == COMMANDER

    /** Neither a port nor the network: another program of this computer holds the radio. */
    val local: Boolean get() = this == OMNIRIG

    /** Its usual TCP port. */
    val defaultPort: Int get() = if (this == COMMANDER) CommanderDriver.DEFAULT_PORT else 4532

    val title: String
        get() = when (this) {
            ICOM -> tr("ICOM CI-V и совместимые (Xiegu)")
            KENWOOD -> tr("Kenwood, Elecraft, FlexRadio")
            YAESU -> tr("Yaesu — новый CAT (FT-991, FT-710, FTDX10…)")
            YAESU_OLD -> tr("Yaesu FT-817 / 818 / 857 / 897")
            RIGCTLD -> tr("Hamlib rigctld (по сети)")
            COMMANDER -> tr("DXLab Commander (по сети, Windows)")
            OMNIRIG -> tr("OmniRig (Windows)")
        }
}

/** How the app reaches the radio. USB and Bluetooth are Android's, SERIAL is the computer's COM port. */
enum class CatLinkType { USB, BLUETOOTH, SERIAL, NETWORK }

/**
 * A transceiver in the model list: its protocol and the factory CI-V address and speed. [rts] — the radio sends only
 * while the computer holds RTS on (RTS/CTS flow control of Kenwood TS-590, TS-890, TS-990).
 */
data class RigModel(val name: String, val protocol: CatProtocol, val civ: Int = 0, val baud: Int = 19200, val rts: Boolean = false) {
    /** Elecraft's S-meter counts 0–21 bars, Kenwood's 0–30. */
    val elecraft get() = name.startsWith("Elecraft")
}

object Rigs {
    const val OTHER_ICOM = "ICOM — другая модель"
    const val OTHER_KENWOOD = "Kenwood — другая модель"
    const val OTHER_YAESU = "Yaesu — другая модель (новый CAT)"
    const val RIGCTLD = "Hamlib rigctld / wfview / flrig"
    const val COMMANDER = "DXLab Commander"
    const val OMNIRIG = "OmniRig"

    val ALL: List<RigModel> = listOf(
        RigModel("IC-705", CatProtocol.ICOM, 0xA4, 115200),
        RigModel("IC-7760", CatProtocol.ICOM, 0xB2, 115200),
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
        RigModel("TS-590S / SG", CatProtocol.KENWOOD, baud = 115200, rts = true),
        RigModel("TS-890S", CatProtocol.KENWOOD, baud = 115200, rts = true),
        RigModel("TS-990S", CatProtocol.KENWOOD, baud = 115200, rts = true),
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
        RigModel(COMMANDER, CatProtocol.COMMANDER),
        RigModel(OMNIRIG, CatProtocol.OMNIRIG),
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
    /** Band, mode and frequency changed in the new QSO card are sent to the radio too. */
    val control: Boolean = true,
    /** QSO-LOG shares the radio with JTDX, WSJT-X and others as a Hamlib rigctld server (see [RigServer]). */
    val serve: Boolean = false,
    val servePort: Int = RigServer.DEFAULT_PORT,
    /** The server takes programs on other computers of the network too, not only on this one. */
    val serveLan: Boolean = false,
    /**
     * An Icom on the network is reached by Icom's own protocol (RS-BA1, wfview: UDP 50001 with a login), not by plain
     * TCP to a serial server (ser2net, a Wi-Fi CI-V adapter).
     */
    val icomLan: Boolean = true,
    /** The radio's "Network User" — its name and password from the radio's menu (Network → Network User1). */
    val lanUser: String = "",
    val lanPassword: String = "",
    /** RTS of the serial port held on; null — as the model wants (see [RigModel.rts]). */
    val rts: Boolean? = null,
    /** OmniRig's radio: 1 or 2. */
    val omniRig: Int = 1,
) {
    val model get() = Rigs.byName(rig)
    val protocol get() = model.protocol

    /** RTS on: the radio's flow control wants it, or the user said so. */
    val rtsOn: Boolean get() = rts ?: model.rts

    /** The radio is an Icom reached by Icom's network protocol (see [icomLan]). */
    val viaIcomLan: Boolean get() = protocol == CatProtocol.ICOM && link == CatLinkType.NETWORK && icomLan

    /** The link is a serial port (USB, a COM port), where the speed matters. */
    val serial: Boolean get() = !protocol.network && !protocol.local && (link == CatLinkType.USB || link == CatLinkType.SERIAL)

    fun toMap(): Map<String, String> = mapOf(
        "cat_enabled" to enabled.toString(), "cat_rig" to rig, "cat_civ" to civAddress.toString(16),
        "cat_baud" to baud.toString(), "cat_link" to link.name, "cat_device" to device, "cat_host" to host,
        "cat_port" to port.toString(), "cat_follow_freq" to followFreq.toString(), "cat_follow_mode" to followMode.toString(),
        "cat_smeter_rst" to sMeterToRst.toString(), "cat_control" to control.toString(),
        "cat_serve" to serve.toString(), "cat_serve_port" to servePort.toString(), "cat_serve_lan" to serveLan.toString(),
        "cat_icom_lan" to icomLan.toString(), "cat_lan_user" to lanUser, LAN_PASSWORD to lanPassword,
        "cat_rts" to (rts?.toString() ?: "auto"), "cat_omnirig" to omniRig.toString(),
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
                control = m["cat_control"]?.toBooleanStrictOrNull() ?: d.control,
                serve = m["cat_serve"]?.toBooleanStrictOrNull() ?: d.serve,
                servePort = m["cat_serve_port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: d.servePort,
                serveLan = m["cat_serve_lan"]?.toBooleanStrictOrNull() ?: d.serveLan,
                // Before 2.7 an Icom on the network was reached by TCP only: kept for a serial server's port, not Icom's.
                icomLan = m["cat_icom_lan"]?.toBooleanStrictOrNull() ?: (m["cat_port"]?.toIntOrNull()?.let { it in 50001..50003 } ?: d.icomLan),
                lanUser = m["cat_lan_user"] ?: d.lanUser,
                lanPassword = m[LAN_PASSWORD] ?: d.lanPassword,
                rts = m["cat_rts"]?.toBooleanStrictOrNull(),
                omniRig = m["cat_omnirig"]?.toIntOrNull()?.takeIf { it == 1 || it == 2 } ?: d.omniRig,
            )
        }

        /** The one secret key: kept where the app keeps passwords, not in the plain settings. */
        const val LAN_PASSWORD = "cat_lan_password"

        /** Icom's network control port (Network → Control Port (UDP)). */
        const val ICOM_LAN_PORT = 50001

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
    /** On transmit; null — the radio was not asked (or cannot say). */
    val tx: Boolean? = null,
    /** On transmit: output power, % of the radio's full power. */
    val powerPct: Int? = null,
    /** On transmit: SWR, 1.0 and up. */
    val swr: Double? = null,
    /** On transmit: ALC, % of its scale (the radio's ALC zone is the first part). */
    val alcPct: Int? = null,
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

    /** "1.3", "∞" above 9.9. */
    fun swr(v: Double): String = if (v >= 9.95) "∞" else "%.1f".format(java.util.Locale.ROOT, v)

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

/** What the app can switch the radio to. DATA — USB with the data switch (USB-D, DATA-U, PKTUSB, DIG). */
enum class RadioMode { LSB, USB, CW, AM, FM, RTTY, DATA }

/** The radio's mode → the log's, and the log's → the radio's. */
object CatModes {
    private val DIGITAL = setOf("FT8", "FT4", "PSK31", "JT65", "JS8", "Q65", "OLIVIA", "SSTV", "DIGI", "RTTY")

    /**
     * The radio mode for the log's [mode] at [hz]: SSB is LSB below 10 MHz (except 60 m) and USB above, digital modes are
     * USB with data on. Null — the radio has nothing for it (DMR, C4FM, D-STAR).
     */
    fun toRadio(mode: String, hz: Long?): RadioMode? = when (mode.uppercase()) {
        "SSB" -> if (hz != null && hz < 10_000_000 && hz !in 5_250_000..5_450_000) RadioMode.LSB else RadioMode.USB
        "CW" -> RadioMode.CW
        "AM" -> RadioMode.AM
        "FM" -> RadioMode.FM
        "RTTY" -> RadioMode.RTTY
        "SSTV" -> if (hz != null && hz < 10_000_000) RadioMode.LSB else RadioMode.USB
        "FT8", "FT4", "PSK31", "JT65", "JS8", "Q65", "OLIVIA", "DIGI" -> RadioMode.DATA
        else -> null
    }

    /**
     * The log's mode for [radio], or null for one the log has no name for. Data modes (USB-D, PKTUSB, DATA-U) keep
     * a digital mode the card already has — FT8, FT4, PSK31, a radio cannot tell them apart — and else are FT8.
     */
    fun toLog(radio: String, current: String): String? {
        val r = radio.uppercase()
        return when {
            // WSJT-X and JTDX name the mode itself.
            r in DIGITAL && r != "DIGI" -> r
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

/** The server's line in the CAT settings. */
fun RigServerStatus.describe(): String = when (this) {
    RigServerStatus.Off -> tr("выключено")
    is RigServerStatus.Listening -> if (clients == 0) tr("порт %s · жду программ", port) else tr("порт %s · подключено программ: %s", port, clients)
    is RigServerStatus.Failed -> message
}

/** The radio's line in the QSO card: "14 074.000 · USB-D · S7", on transmit "14 074.000 · CW · TX 80% · SWR 1.3". */
fun RigState.short(): String = listOfNotNull(
    freqHz?.let { CatFormat.khz(it) }, radioMode,
    if (tx == true) "TX" + (powerPct?.let { " $it%" } ?: "") else sDb?.let { CatFormat.sMeter(it) },
    swr?.takeIf { tx == true }?.let { "SWR " + CatFormat.swr(it) },
).joinToString(" · ")

/**
 * Where the radio goes when a band is picked and the app does not yet know where the user last was on it: the CW,
 * phone and FT8/FT4 calling frequencies (IARU Region 1).
 */
object BandPlan {
    private class Band(val cw: Long, val phone: Long, val ft8: Long, val ft4: Long? = null, val fm: Long? = null)

    private val BANDS = mapOf(
        "2200m" to Band(135_700, 137_400, 136_130),
        "630m" to Band(472_500, 475_000, 474_200),
        "160m" to Band(1_830_000, 1_850_000, 1_840_000),
        "80m" to Band(3_530_000, 3_700_000, 3_573_000, 3_575_000),
        "60m" to Band(5_352_000, 5_360_000, 5_357_000),
        "40m" to Band(7_020_000, 7_100_000, 7_074_000, 7_047_500),
        "30m" to Band(10_115_000, 10_130_000, 10_136_000, 10_140_000),
        "20m" to Band(14_020_000, 14_200_000, 14_074_000, 14_080_000),
        "17m" to Band(18_080_000, 18_130_000, 18_100_000, 18_104_000),
        "15m" to Band(21_020_000, 21_250_000, 21_074_000, 21_140_000),
        "12m" to Band(24_900_000, 24_950_000, 24_915_000, 24_919_000),
        "10m" to Band(28_020_000, 28_500_000, 28_074_000, 28_180_000, 29_600_000),
        "6m" to Band(50_090_000, 50_150_000, 50_313_000, 50_318_000, 51_510_000),
        "4m" to Band(70_090_000, 70_200_000, 70_154_000),
        "2m" to Band(144_050_000, 144_300_000, 144_174_000, 144_170_000, 145_500_000),
        "70cm" to Band(432_050_000, 432_200_000, 432_174_000, null, 433_500_000),
        "23cm" to Band(1_296_050_000, 1_296_200_000, 1_296_174_000),
    )

    val bands: Set<String> get() = BANDS.keys

    /** The frequency for [band] in the log's [mode], Hz; null for a band the plan does not know. */
    fun freq(band: String, mode: String): Long? {
        val b = BANDS[band] ?: return null
        return when (mode.uppercase()) {
            "CW" -> b.cw
            "FT8", "JT65", "JS8", "Q65" -> b.ft8
            "FT4" -> b.ft4 ?: b.ft8
            "PSK31", "OLIVIA", "DIGI", "RTTY" -> b.ft8
            "FM" -> b.fm ?: b.phone
            else -> b.phone
        }
    }
}

/** Something the app asks the radio to do (see [CatSession.send]). */
sealed interface CatCommand {
    data class Freq(val hz: Long) : CatCommand
    data class Mode(val mode: RadioMode) : CatCommand
    /** Transmit on or off: for programs that key the radio through QSO-LOG (JTDX, WSJT-X via the rigctld server). */
    data class Ptt(val on: Boolean) : CatCommand
}
