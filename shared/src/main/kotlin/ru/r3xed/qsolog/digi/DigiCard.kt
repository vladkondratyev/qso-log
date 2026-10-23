package ru.r3xed.qsolog.digi

import ru.r3xed.qsolog.Form
import ru.r3xed.qsolog.data.AdifLabels
import ru.r3xed.qsolog.data.HamQth
import ru.r3xed.qsolog.data.Qso

/**
 * A contact logged in WSJT-X while its card was open ([DigiConfig.openCard]): the program's fields (time, reports,
 * frequency) stay, the card adds what it knows of the station — name, QTH, region from the callbooks — and what the
 * user typed: a note, a voice note. The card's precise position wins over the program's 4-character locator it agrees with.
 */
fun Qso.withCard(f: Form): Qso {
    val cardPlace = f.locator.isNotBlank() && (locator.isBlank() || f.locator.trim().startsWith(locator.trim(), ignoreCase = true))
    return copy(
        name = name.ifBlank { f.name.trim() },
        qth = qth.ifBlank { f.qth.trim() },
        country = country.ifBlank { f.country.trim() },
        locator = if (cardPlace) f.locator.trim() else locator,
        lat = if (cardPlace) f.lat else lat,
        lon = if (cardPlace) f.lon else lon,
        comment = listOf(comment.trim(), f.comment.trim()).filter { it.isNotEmpty() }.distinct().joinToString(" "),
        audio = audio.ifBlank { f.audio },
        qslSent = qslSent || f.qslSent,
        // "≈ region" only goes with the card's own position: the program's locator is the station's.
        adif = f.adif.filterKeys { it in AdifLabels.THEM || (cardPlace && it == HamQth.POSITION_FIELD) }.filterValues { it.isNotBlank() } + adif,
    )
}
