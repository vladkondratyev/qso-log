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
    /** QRZ.com XML API account (callsign; the full record needs the XML subscription), asked before the site, see [QrzComXml]. */
    val qrzComXmlLogin: String = "",
    val qrzComXmlPassword: String = "",
    /** QRZ.com account (callsign or e-mail): the callsign page after logging in, see [QrzCom]. */
    val qrzComLogin: String = "",
    val qrzComPassword: String = "",
    /** HamQTH.com account (callsign): its callbook, after QRZ.ru and QRZ.com, see [HamQthBook]. */
    val hamqthLogin: String = "",
    val hamqthPassword: String = "",
    /** QRZCQ.com account (callsign; the XML API needs its premium membership), see [QrzCq]. */
    val qrzcqLogin: String = "",
    val qrzcqPassword: String = "",
    /** Default transmit power for new contacts, W. */
    val power: String = "",
    /** Station ADIF fields copied into every new contact: keys of [AdifLabels.MINE] (OPERATOR, MY_RIG, …). */
    val station: Map<String, String> = emptyMap(),
) {
    val myPosition get() = Geo.locatorToLatLon(myLocator)
}
