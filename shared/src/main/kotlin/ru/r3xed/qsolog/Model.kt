package ru.r3xed.qsolog

import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.QrzInfo
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Everything typed into the contact form. Date and time are kept as text so half-typed values are allowed. */
data class Form(
    val id: Long = 0,
    val call: String = "",
    val date: String = "",
    val time: String = "",
    val band: String = "",
    val mode: String = "",
    val freq: String = "",
    val rstSent: String = "",
    val rstRcvd: String = "",
    val name: String = "",
    val qth: String = "",
    val country: String = "",
    val locator: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val power: String = "",
    val qslSent: Boolean = false,
    val qslRcvd: Boolean = false,
    val comment: String = "",
    /** Voice note file name (see [ru.r3xed.qsolog.data.Qso.audio]). */
    val audio: String = "",
    /** Voice note removed in this card; the file is deleted only when the card is saved. */
    val removedAudio: String = "",
    /** Station fields of this record. New contacts take them from the settings. */
    val myCall: String = "",
    val myLocator: String = "",
    /** Extra ADIF fields (see [ru.r3xed.qsolog.data.Qso.adif]). */
    val adif: Map<String, String> = emptyMap(),
    /** End of the contact (ADIF QSO_DATE_OFF / TIME_OFF) as ДД.ММ.ГГГГ and ЧЧ:ММ. */
    val dateOff: String = "",
    val timeOff: String = "",
    /**
     * The receive band/frequency and the end time follow the main band, frequency and start time.
     * False only for an imported contact whose values differed (split operation, a long QSO); those are kept.
     */
    val bandRxFollows: Boolean = true,
    val freqRxFollows: Boolean = true,
    val timeOffFollows: Boolean = true,
    /** Distance and bearing from an imported log, used when positions are unknown. */
    val distanceKm: Double? = null,
    val bearing: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Name, QTH and locator were filled from QRZ.ru and may be replaced when the callsign changes. */
    val infoFromQrz: Boolean = false,
) {
    val position: LatLon?
        get() = if (lat != null && lon != null) LatLon(lat, lon) else Geo.locatorToLatLon(locator)
    val isNew get() = id == 0L
}

sealed interface Lookup {
    data object Idle : Lookup
    data object Loading : Lookup
    data object NotFound : Lookup
    data class Found(val info: QrzInfo) : Lookup
    data class Failed(val message: String) : Lookup
}

val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun utc(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)

