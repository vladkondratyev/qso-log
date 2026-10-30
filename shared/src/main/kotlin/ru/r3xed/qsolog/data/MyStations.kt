package ru.r3xed.qsolog.data

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * One of "Мои станции": home, portable, the club station… The one in work is copied into [StationSettings]
 * (myCall, myLocator, myQth, power, station), where the rest of the app reads it; the others wait in the list.
 */
data class MyStation(
    val id: String,
    /** What the user calls it: "Дом", "Выезд", "Клуб"; may be empty. */
    val title: String = "",
    val call: String = "",
    val locator: String = "",
    val qth: String = "",
    val power: String = "",
    /** Station ADIF fields copied into every new contact (OPERATOR, MY_RIG, …), as in [StationSettings.station]. */
    val station: Map<String, String> = emptyMap(),
    /** Android: the locator comes from the phone's location. */
    val autoLocator: Boolean = false,
)

object MyStations {
    /** The station in work, taken from the settings (its [id], [title] and [autoLocator] are the list's). */
    fun of(s: StationSettings, id: String, title: String = "", autoLocator: Boolean = false) = MyStation(
        id = id, title = title, call = s.myCall, locator = s.myLocator, qth = s.myQth, power = s.power,
        station = s.station, autoLocator = autoLocator,
    )

    /** The settings with [st] in work. */
    fun apply(s: StationSettings, st: MyStation) = s.copy(
        myCall = st.call, myLocator = st.locator, myQth = st.qth, power = st.power, station = st.station,
    )

    /** One line per station, its fields URL-encoded: key=value&key=value. */
    fun encode(list: List<MyStation>): String = list.joinToString("\n") { st ->
        (listOf("id" to st.id, "title" to st.title, "call" to st.call, "locator" to st.locator, "qth" to st.qth, "power" to st.power, "auto" to st.autoLocator.toString()) +
            st.station.map { (k, v) -> "s.$k" to v })
            .joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
    }

    fun decode(text: String): List<MyStation> = text.lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val m = line.split('&').mapNotNull { p ->
            val i = p.indexOf('=')
            if (i < 0) null else dec(p.substring(0, i)) to dec(p.substring(i + 1))
        }
        val f = m.toMap()
        val id = f["id"]?.ifBlank { null } ?: return@mapNotNull null
        MyStation(
            id = id, title = f["title"].orEmpty(), call = f["call"].orEmpty(), locator = f["locator"].orEmpty(),
            qth = f["qth"].orEmpty(), power = f["power"].orEmpty(), autoLocator = f["auto"] == "true",
            station = m.filter { it.first.startsWith("s.") }.associate { it.first.removePrefix("s.") to it.second },
        )
    }

    /** Different callsigns among the stations: two or more bring the "Станция" filter to the log. */
    fun calls(list: List<MyStation>): List<String> = list.map { it.call.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()

    /** The station's line in a list: "Дом · KO85ts · Москва". */
    fun describe(st: MyStation, autoText: String): String =
        listOf(st.title, if (st.autoLocator) autoText else st.locator, st.qth).filter { it.isNotBlank() }.joinToString(" · ")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}
