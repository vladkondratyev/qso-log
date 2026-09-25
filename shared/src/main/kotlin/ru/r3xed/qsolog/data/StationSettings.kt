package ru.r3xed.qsolog.data

data class StationSettings(
    val myCall: String = "",
    val myLocator: String = "",
    val myQth: String = "",
    val qrzLogin: String = "",
    val qrzPassword: String = "",
    /** Default transmit power for new contacts, W. */
    val power: String = "",
    /** Station ADIF fields copied into every new contact: keys of [AdifLabels.MINE] (OPERATOR, MY_RIG, …). */
    val station: Map<String, String> = emptyMap(),
) {
    val myPosition get() = Geo.locatorToLatLon(myLocator)
}
