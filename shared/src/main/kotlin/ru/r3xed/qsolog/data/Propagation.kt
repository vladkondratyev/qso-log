package ru.r3xed.qsolog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.tr
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.zip.GZIPInputStream

/*
 * "Прохождение": band conditions from free services, all seen from the station's QTH.
 *  - hamqsl.com (N0NBH): solar data and the band conditions by day and night — credited as asked.
 *  - NOAA SWPC (public domain): the Kp forecast, the 10.7 cm flux, X-ray flares, D-RAP absorption, the aurora (OVATION).
 *  - prop.kc2g.com: MUF(3000) and foF2 of the world's ionosondes (GIRO data).
 *  - PSK Reporter: who hears one's callsign, and what is heard in one's grid field — asked no more than every 5 minutes.
 *  - NCDXF/IARU beacons: their fixed schedule, no network.
 */

/** N0NBH's verdict for a group of bands, by day and by night. */
data class BandCondition(val bands: String, val day: String, val night: String)

/** An ionosonde of KC2G: MUF for a 3000 km hop, foF2, the confidence (0–100) and when it was measured. */
data class Ionosonde(val name: String, val point: LatLon, val mufMhz: Double, val fof2: Double?, val confidence: Int, val time: Long)

/** A cell of a world grid (D-RAP: the highest frequency, MHz, losing 1 dB; aurora: the probability, %). */
data class GridCell(val lat: Double, val lon: Double, val value: Double)

/** A Kp value of 3 hours: observed or predicted, with the NOAA storm level (G1–G5) if any. */
data class KpPoint(val time: Long, val kp: Double, val observed: Boolean, val scale: String?)

/** An X-ray flare: its peak time and class ("M1.2"). */
data class Flare(val time: Long, val cls: String) {
    /** M and X flares black out HF on the day side for tens of minutes. */
    val strong get() = cls.startsWith("M") || cls.startsWith("X")
}

/** A PSK Reporter reception: who sent, who heard, where, on what, how loud. */
data class Reception(
    val sender: String, val senderLocator: String, val receiver: String, val receiverLocator: String,
    val hz: Long, val mode: String, val snr: Int?, val time: Long,
) {
    val band: String? get() = bandForFreq(hz / 1_000_000.0)
}

/** Everything the screen and the map layers show; each part may be missing (the service did not answer). */
data class PropagationData(
    val solar: SolarData? = null,
    val bands: List<BandCondition> = emptyList(),
    val vhf: List<Pair<String, String>> = emptyList(),
    val kp: List<KpPoint> = emptyList(),
    val flux: List<Pair<Long, Double>> = emptyList(),
    val flares: List<Flare> = emptyList(),
    val ionosondes: List<Ionosonde> = emptyList(),
    val drap: List<GridCell> = emptyList(),
    val aurora: List<GridCell> = emptyList(),
    /** Who heard one's callsign (last hour). */
    val heardMe: List<Reception> = emptyList(),
    /** What receivers in one's grid field heard (last 15 minutes): the bands open from here. */
    val heardHere: List<Reception> = emptyList(),
    val updated: Map<String, Long> = emptyMap(),
    val problems: Map<String, String> = emptyMap(),
)

object Propagation {
    const val HAMQSL = "https://www.hamqsl.com/solarxml.php"
    const val KP = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json"
    const val FLUX = "https://services.swpc.noaa.gov/json/f107_cm_flux.json"
    const val FLARES = "https://services.swpc.noaa.gov/json/goes/primary/xray-flares-7-day.json"
    const val DRAP = "https://services.swpc.noaa.gov/text/drap_global_frequencies.txt"
    const val AURORA = "https://services.swpc.noaa.gov/json/ovation_aurora_latest.json"
    const val KC2G = "https://prop.kc2g.com/api/stations.json"
    const val PSK = "https://retrieve.pskreporter.info/query"

    /** How often each part may be asked again, ms. PSK Reporter asks for no more than once every 5 minutes. */
    val PERIOD = mapOf(
        "hamqsl" to 30 * 60_000L, "kp" to 30 * 60_000L, "flux" to 3 * 3_600_000L, "flares" to 15 * 60_000L,
        "drap" to 15 * 60_000L, "aurora" to 30 * 60_000L, "kc2g" to 15 * 60_000L, "psk" to 5 * 60_000L,
    )

    // ---------- parsing ----------

    fun parseBands(xml: String): List<BandCondition> {
        val re = Regex("<band name=\"([^\"]+)\" time=\"(day|night)\">\\s*([^<]*?)\\s*</band>")
        val m = re.findAll(xml).map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()
        return m.map { it.first }.distinct().map { b ->
            BandCondition(b, m.firstOrNull { it.first == b && it.second == "day" }?.third.orEmpty(), m.firstOrNull { it.first == b && it.second == "night" }?.third.orEmpty())
        }
    }

    fun parseVhf(xml: String): List<Pair<String, String>> =
        Regex("<phenomenon name=\"([^\"]+)\" location=\"([^\"]+)\">\\s*([^<]*?)\\s*</phenomenon>").findAll(xml)
            .map { "${it.groupValues[1]} · ${it.groupValues[2].replace('_', ' ')}" to it.groupValues[3] }.toList()

    /** N0NBH's VHF phenomenon in plain words: "Аврора, северное полушарие" → "закрыто" / "открыто: …". */
    fun vhfText(what: String, value: String): Pair<String, String> {
        val w = what.lowercase()
        val title = when {
            "aurora" in w -> tr("Аврора")
            "europe 6m" in w -> tr("Es на 6 м, Европа")
            "europe 4m" in w -> tr("Es на 4 м, Европа")
            "europe" in w -> tr("Es на 2 м, Европа")
            "north america" in w -> tr("Es на 2 м, Сев. Америка")
            else -> what
        }
        val v = if (value.contains("closed", ignoreCase = true)) tr("закрыто") else tr("открыто: %s", value)
        return title to v
    }

    private fun time(s: String?): Long? = s?.let { t ->
        runCatching { Instant.parse(if (t.endsWith("Z")) t else t + "Z").toEpochMilli() }.getOrNull()
    }

    fun parseKp(json: String): List<KpPoint> = (MiniJson.parse(json) as? List<*>).orEmpty().mapNotNull { e ->
        when (e) {
            is Map<*, *> -> KpPoint(time(e["time_tag"] as? String) ?: return@mapNotNull null, (e["kp"] as? Number)?.toDouble() ?: return@mapNotNull null, e["observed"] == "observed", e["noaa_scale"] as? String)
            // The older layout: a header row, then [time, kp, observed, scale].
            is List<*> -> {
                val t = time((e.getOrNull(0) as? String)?.replace(' ', 'T')) ?: return@mapNotNull null
                val kp = (e.getOrNull(1) as? String)?.toDoubleOrNull() ?: return@mapNotNull null
                KpPoint(t, kp, e.getOrNull(2) == "observed", e.getOrNull(3) as? String)
            }
            else -> null
        }
    }

    /** One value a day (the noon reading when there is one), oldest first. */
    fun parseFlux(json: String): List<Pair<Long, Double>> = (MiniJson.parse(json) as? List<*>).orEmpty().mapNotNull { e ->
        val m = e as? Map<*, *> ?: return@mapNotNull null
        val t = time(m["time_tag"] as? String) ?: return@mapNotNull null
        val f = (m["flux"] as? Number)?.toDouble() ?: return@mapNotNull null
        Triple(t, f, m["reporting_schedule"] == "Noon")
    }.groupBy { it.first / 86_400_000L }.map { (_, day) -> (day.firstOrNull { it.third } ?: day.first()).let { it.first to it.second } }.sortedBy { it.first }

    fun parseFlares(json: String): List<Flare> = (MiniJson.parse(json) as? List<*>).orEmpty().mapNotNull { e ->
        val m = e as? Map<*, *> ?: return@mapNotNull null
        Flare(time(m["max_time"] as? String) ?: return@mapNotNull null, (m["max_class"] as? String)?.ifBlank { null } ?: return@mapNotNull null)
    }.sortedByDescending { it.time }

    /** The D-RAP table: rows "lat | values", the header the longitudes. Only cells with absorption. */
    fun parseDrap(text: String): List<GridCell> {
        val lines = text.lines()
        val header = lines.firstOrNull { l -> !l.startsWith("#") && l.trim().split(Regex("\\s+")).let { p -> p.size > 10 && p.all { it.toDoubleOrNull() != null } } } ?: return emptyList()
        val lons = header.trim().split(Regex("\\s+")).map { it.toDouble() }
        return lines.filter { it.contains('|') }.flatMap { l ->
            val lat = l.substringBefore('|').trim().toDoubleOrNull() ?: return@flatMap emptyList()
            l.substringAfter('|').trim().split(Regex("\\s+")).mapIndexedNotNull { i, v ->
                val f = v.toDoubleOrNull() ?: return@mapIndexedNotNull null
                if (f <= 0.0 || i >= lons.size) null else GridCell(lat, lons[i], f)
            }
        }
    }

    /** OVATION: [lon 0..359, lat, %]; cells of 3° × 2° keep the highest value, only from [min] %. */
    fun parseAurora(json: String, min: Int = 10): List<GridCell> {
        val m = MiniJson.parse(json) as? Map<*, *> ?: return emptyList()
        val best = HashMap<Pair<Int, Int>, Double>()
        (m["coordinates"] as? List<*>).orEmpty().forEach { c ->
            val p = c as? List<*> ?: return@forEach
            val lon = (p.getOrNull(0) as? Number)?.toDouble() ?: return@forEach
            val lat = (p.getOrNull(1) as? Number)?.toDouble() ?: return@forEach
            val v = (p.getOrNull(2) as? Number)?.toDouble() ?: return@forEach
            if (v < min) return@forEach
            val key = Math.floorDiv(((lon + 180).mod(360.0) - 180).toInt(), 3) to Math.floorDiv(lat.toInt(), 2)
            if (v > (best[key] ?: 0.0)) best[key] = v
        }
        return best.map { (k, v) -> GridCell(k.second * 2 + 1.0, k.first * 3 + 1.5, v) }
    }

    /** KC2G's stations measured within [maxAgeMs], longitudes made −180..180. */
    fun parseIonosondes(json: String, now: Long = System.currentTimeMillis(), maxAgeMs: Long = 3 * 3_600_000L): List<Ionosonde> =
        (MiniJson.parse(json) as? List<*>).orEmpty().mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val st = m["station"] as? Map<*, *> ?: return@mapNotNull null
            val lat = (st["latitude"] as? String)?.toDoubleOrNull() ?: (st["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
            val lonRaw = (st["longitude"] as? String)?.toDoubleOrNull() ?: (st["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
            val muf = (m["mufd"] as? Number)?.toDouble() ?: return@mapNotNull null
            val t = time(m["time"] as? String) ?: return@mapNotNull null
            if (now - t > maxAgeMs) return@mapNotNull null
            Ionosonde(
                st["name"] as? String ?: "", LatLon(lat, (lonRaw + 180).mod(360.0) - 180), muf,
                (m["fof2"] as? Number)?.toDouble(), (m["cs"] as? Number)?.toInt() ?: 0, t,
            )
        }

    fun parseReceptions(xml: String): List<Reception> =
        Regex("<receptionReport ([^>]*)/>").findAll(xml).mapNotNull { r ->
            val a = Regex("(\\w+)=\"([^\"]*)\"").findAll(r.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            Reception(
                sender = a["senderCallsign"] ?: return@mapNotNull null, senderLocator = a["senderLocator"].orEmpty(),
                receiver = a["receiverCallsign"] ?: return@mapNotNull null, receiverLocator = a["receiverLocator"].orEmpty(),
                hz = a["frequency"]?.toLongOrNull() ?: return@mapNotNull null, mode = a["mode"].orEmpty(),
                snr = a["sNR"]?.toIntOrNull(), time = (a["flowStartSeconds"]?.toLongOrNull() ?: 0L) * 1000,
            )
        }.toList()

    // ---------- fetching ----------

    private suspend fun get(url: String, ua: String): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", ua)
            c.setRequestProperty("Accept-Encoding", "gzip")
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            val raw = if (c.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(c.inputStream) else c.inputStream
            raw.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /**
     * Asks again what is older than its [PERIOD] (or all with [force]); [parts] limits it ("psk" needs [myCall] or
     * [myLocator]). A part that fails keeps its old value and gets a line in [PropagationData.problems].
     */
    suspend fun refresh(
        old: PropagationData, ua: String, myCall: String, myLocator: String,
        parts: Set<String> = PERIOD.keys, force: Boolean = false, now: Long = System.currentTimeMillis(),
    ): PropagationData = coroutineScope {
        fun due(k: String) = k in parts && (force || now - (old.updated[k] ?: 0L) >= PERIOD.getValue(k))
        suspend fun <T> part(k: String, f: suspend () -> T): Result<T>? = if (!due(k)) null else runCatching { f() }
        val h = async { part("hamqsl") { get(HAMQSL, ua) } }
        val kp = async { part("kp") { parseKp(get(KP, ua)) } }
        val fl = async { part("flux") { parseFlux(get(FLUX, ua)) } }
        val xr = async { part("flares") { parseFlares(get(FLARES, ua)) } }
        val dr = async { part("drap") { parseDrap(get(DRAP, ua)) } }
        val au = async { part("aurora") { parseAurora(get(AURORA, ua)) } }
        val io = async { part("kc2g") { parseIonosondes(get(KC2G, ua), now) } }
        val field = myLocator.trim().take(2).uppercase().takeIf { Geo.isLocator(myLocator) }
        val call = myCall.trim().uppercase().takeIf { it.length >= 3 }
        val ps = async {
            part("psk") {
                // Two questions, once every 5 minutes: who heard me (an hour), what my grid field hears (15 minutes).
                val me = call?.let { parseReceptions(get("$PSK?senderCallsign=${enc(it)}&flowStartSeconds=-3600&rronly=1&rptlimit=300", ua)) }.orEmpty()
                val here = field?.let { parseReceptions(get("$PSK?receiverCallsign=${enc(it)}&modify=grid&flowStartSeconds=-900&rronly=1&rptlimit=1000", ua)) }.orEmpty()
                me to here
            }
        }
        var d = old
        val updated = old.updated.toMutableMap()
        val problems = old.problems.toMutableMap()
        fun <T> take(k: String, r: Result<T>?, apply: (T) -> Unit) {
            r ?: return
            r.onSuccess { apply(it); updated[k] = now; problems.remove(k) }
                .onFailure { problems[k] = it.message ?: it.javaClass.simpleName; updated[k] = now - PERIOD.getValue(k) + 120_000L }
        }
        take("hamqsl", h.await()) { x -> d = d.copy(solar = SolarData.parse(x) ?: d.solar, bands = parseBands(x).ifEmpty { d.bands }, vhf = parseVhf(x)) }
        take("kp", kp.await()) { d = d.copy(kp = it) }
        take("flux", fl.await()) { d = d.copy(flux = it) }
        take("flares", xr.await()) { d = d.copy(flares = it) }
        take("drap", dr.await()) { d = d.copy(drap = it) }
        take("aurora", au.await()) { d = d.copy(aurora = it) }
        take("kc2g", io.await()) { d = d.copy(ionosondes = it) }
        take("psk", ps.await()) { (me, here) -> d = d.copy(heardMe = me, heardHere = here) }
        d.copy(updated = updated, problems = problems)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- seen from the station's QTH ----------

    /** What the QTH has now: day or night, the next sunrise/sunset, the nearest ionosonde, absorption, aurora. */
    data class AtQth(
        val night: Boolean,
        /** Minutes until the sun crosses the horizon at the QTH (sunrise at night, sunset by day); null at polar day/night. */
        val changeInMin: Int?,
        val ionosonde: Ionosonde?, val ionosondeKm: Double?,
        /** D-RAP: the highest frequency losing 1 dB at the QTH, MHz (0 — no absorption). */
        val absorptionMhz: Double,
        /** OVATION: the highest aurora probability within ~1000 km, %. */
        val auroraPct: Int,
        /** The N0NBH verdict for day or night, whichever the QTH has now. */
        val bandsNow: List<Pair<String, String>>,
    )

    fun atQth(d: PropagationData, qth: LatLon, now: Long = System.currentTimeMillis()): AtQth {
        val night = Sun.isNight(qth, now)
        val change = (1..24 * 60).firstOrNull { m -> Sun.isNight(qth, now + m * 60_000L) != night }
        val near = d.ionosondes.filter { it.confidence >= 25 }.minByOrNull { Geo.distanceKm(qth, it.point) }
        val drap = d.drap.minByOrNull { (it.lat - qth.lat) * (it.lat - qth.lat) + lonDiff(it.lon, qth.lon).let { x -> x * x } / 4 }
            ?.takeIf { kotlin.math.abs(it.lat - qth.lat) <= 2.5 && kotlin.math.abs(lonDiff(it.lon, qth.lon)) <= 4.5 }?.value ?: 0.0
        val aurora = d.aurora.filter { Geo.distanceKm(qth, LatLon(it.lat, it.lon)) <= 1000 }.maxOfOrNull { it.value }?.toInt() ?: 0
        return AtQth(
            night, change, near, near?.let { Geo.distanceKm(qth, it.point) }, drap, aurora,
            d.bands.map { it.bands to if (night) it.night else it.day },
        )
    }

    private fun lonDiff(a: Double, b: Double) = ((a - b + 540).mod(360.0)) - 180

    /** Eight directions from the QTH: С, СВ, В, ЮВ, Ю, ЮЗ, З, СЗ. */
    val SECTORS: List<String>
        get() = if (ru.r3xed.qsolog.I18n.current == ru.r3xed.qsolog.Lang.EN) listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        else listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")

    fun sector(bearing: Double) = (((bearing + 22.5).mod(360.0)) / 45).toInt() % 8

    /**
     * "Что слышно у вас": receptions in one's grid field by band and by the direction the signal came from (bearing
     * from the QTH to the sender); the count is of different senders. Bands as in [BANDS], only the ones with signals.
     */
    fun activity(heardHere: List<Reception>, qth: LatLon): List<Pair<String, IntArray>> {
        val cells = HashMap<String, Array<HashSet<String>>>()
        for (r in heardHere) {
            val b = r.band ?: continue
            val p = Geo.locatorToLatLon(r.senderLocator) ?: continue
            if (Geo.distanceKm(qth, p) < 300) continue
            val s = sector(Geo.bearing(qth, p))
            cells.getOrPut(b) { Array(8) { HashSet() } }[s] += r.sender.uppercase()
        }
        return BANDS.filter { it in cells }.map { b -> b to IntArray(8) { cells.getValue(b)[it].size } }
    }

    /** "Кто вас слышит": per band, how many receivers and the farthest one (km) from the QTH. */
    fun heardMeByBand(heardMe: List<Reception>, qth: LatLon): List<Triple<String, Int, Double>> =
        heardMe.groupBy { it.band }.filterKeys { it != null }.map { (b, list) ->
            val far = list.mapNotNull { Geo.locatorToLatLon(it.receiverLocator) }.maxOfOrNull { Geo.distanceKm(qth, it) } ?: 0.0
            Triple(b!!, list.map { it.receiver }.distinct().size, far)
        }.sortedBy { BANDS.indexOf(it.first) }
}

/**
 * The NCDXF/IARU beacons: 18 stations, each 10 s on 14.100, 18.110, 21.150, 24.930 and 28.200 MHz in turn, a 3-minute
 * cycle synchronised to UTC (https://www.ncdxf.org/beacon/). Which one is on now is computed, no network needed.
 */
object Beacons {
    data class Beacon(val call: String, val place: String, val locator: String)

    val ALL = listOf(
        Beacon("4U1UN", "New York", "FN30as"), Beacon("VE8AT", "Nunavut", "EQ79ax"), Beacon("W6WX", "California", "CM97bd"),
        Beacon("KH6RS", "Hawaii", "BL10ts"), Beacon("ZL6B", "New Zealand", "RE78tw"), Beacon("VK6RBP", "Australia", "OF87av"),
        Beacon("JA2IGY", "Japan", "PM84jk"), Beacon("RR9O", "Novosibirsk", "NO14kx"), Beacon("VR2B", "Hong Kong", "OL72bg"),
        Beacon("4S7B", "Sri Lanka", "MJ96wv"), Beacon("ZS6DN", "South Africa", "KG33xi"), Beacon("5Z4B", "Kenya", "KI88hr"),
        Beacon("4X6TU", "Israel", "KM72jb"), Beacon("OH2B", "Finland", "KP20eh"), Beacon("CS3B", "Madeira", "IM12jt"),
        Beacon("LU4AA", "Argentina", "GF05tj"), Beacon("OA4B", "Peru", "FH17mw"), Beacon("YV5B", "Venezuela", "FJ69cc"),
    )
    val FREQS_KHZ = listOf(14100.0, 18110.0, 21150.0, 24930.0, 28200.0)

    /** The beacon on each band at [ms], and how many seconds it has left. */
    fun now(ms: Long = System.currentTimeMillis()): List<Triple<Double, Beacon, Int>> {
        val sec = (ms / 1000) % 180
        val slot = (sec / 10).toInt()
        val left = 10 - (sec % 10).toInt()
        return FREQS_KHZ.mapIndexed { band, khz -> Triple(khz, ALL[(slot - band).mod(18)], left) }
    }
}
