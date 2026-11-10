package ru.r3xed.qsolog

/**
 * "Поиск по настройкам": every settings block with the words it holds. A hit opens the block's page and unfolds it.
 * [block] is the block's title exactly as the settings show it (Russian, translated by tr()).
 */
object SettingsIndex {
    data class Entry(val block: String, val page: SettingsPage, val words: String)

    val ALL = listOf(
        Entry("Мои станции", SettingsPage.MAIN, "позывной локатор qth мощность оператор трансивер антенна станция геолокация выезд дом клуб callsign locator power station"),
        Entry("Выезд и активация", SettingsPage.MAIN, "pota wwff sota iota rda активация выезд портатив референс park activation portable"),
        Entry("Ввод связи", SettingsPage.MAIN, "клавиатура время сохранения rst частота keypad time"),
        Entry("Соревнования", SettingsPage.MAIN, "контест contest ермак cabrillo номер обмен"),
        Entry("Диапазоны и виды связи", SettingsPage.MAIN, "диапазон вид связи band mode cw ssb ft8"),
        Entry("Журнал связей", SettingsPage.MAIN, "экспорт импорт adif csv google таблица синхронизация настроек учётные записи облако автокопия резервная копия удалить backup export import sync"),
        Entry("Интерфейс", SettingsPage.MAIN, "язык тема масштаб экран клавиши тёмная светлая своя цвет палитра обводка контраст таблица колонки language theme scale zoom keys custom colour contrast table columns"),
        Entry("CAT — трансивер", SettingsPage.HARDWARE, "cat трансивер icom kenwood yaesu omnirig hamlib rigctld com порт usb bluetooth ft-890 ft-990 radio трансвертер transverter xvtr"),
        Entry("Цифровые виды — WSJT-X, JTDX, MSHV", SettingsPage.HARDWARE, "wsjt jtdx mshv ft8 udp цифра digital"),
        Entry("Телеграф (CW) — DTR/RTS или cwdaemon", SettingsPage.HARDWARE, "cw телеграф ключ dtr rts cwdaemon скорость wpm keyer ptt задержка макросы контест"),
        Entry("Запускать вместе с QSO-LOG", SettingsPage.HARDWARE, "автозапуск программы запуск start apps"),
        Entry("Данные о корреспонденте", SettingsPage.SOURCES, "qrz.ru qrz.com hamqth qrzcq справочник логин пароль имя фото прошлой связи callbook"),
        Entry("DX-кластер", SettingsPage.SOURCES, "кластер cluster dxspider сервер споты уведомления watch список ожидания"),
        Entry("Онлайн-журналы: LoTW, eQSL, Club Log, QRZ.com, HAMLOG", SettingsPage.SOURCES, "lotw eqsl club log qrz.com hamlog хамлог сертификат p12 tqsl выгрузка upload certificate"),
    )

    /** The blocks whose title or words contain every word of [query] (either language). */
    fun search(query: String): List<Entry> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return ALL.filter { e ->
            val hay = (e.block + " " + tr(e.block) + " " + e.words).lowercase()
            words.all { it in hay }
        }
    }
}
