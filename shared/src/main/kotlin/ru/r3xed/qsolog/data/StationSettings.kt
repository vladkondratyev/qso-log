package ru.r3xed.qsolog.data

data class StationSettings(
    val myCall: String = "",
    val myLocator: String = "",
    val myQth: String = "",
    val qrzLogin: String = "",
    val qrzPassword: String = "",
    /** Login of the QRZ.ru site itself (e-mail): a fallback source when there is no XML API access, see [QrzSite]. */
    val qrzSiteEmail: String = "",
    val qrzSitePassword: String = "",
    /** Default transmit power for new contacts, W. */
    val power: String = "",
    /** Station ADIF fields copied into every new contact: keys of [AdifLabels.MINE] (OPERATOR, MY_RIG, …). */
    val station: Map<String, String> = emptyMap(),
) {
    val myPosition get() = Geo.locatorToLatLon(myLocator)
}
