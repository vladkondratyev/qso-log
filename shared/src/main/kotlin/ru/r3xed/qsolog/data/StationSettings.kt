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
    /** Online logbooks (see [OnlineLog]). Keys and passwords are kept like the QRZ.ru password. */
    val qrzcomKey: String = "",
    val eqslUser: String = "",
    val eqslPassword: String = "",
    val eqslNickname: String = "",
    val clublogEmail: String = "",
    val clublogPassword: String = "",
    val clublogKey: String = "",
    /** LoTW on a computer: TQSL's station location name and, if not found by itself, the path to tqsl. */
    val lotwLocation: String = "",
    val tqslPath: String = "",
    /** Default transmit power for new contacts, W. */
    val power: String = "",
    /** Station ADIF fields copied into every new contact: keys of [AdifLabels.MINE] (OPERATOR, MY_RIG, …). */
    val station: Map<String, String> = emptyMap(),
) {
    val myPosition get() = Geo.locatorToLatLon(myLocator)
}
