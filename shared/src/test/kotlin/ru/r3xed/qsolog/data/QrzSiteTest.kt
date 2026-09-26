package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pages built like www.qrz.ru/db/CALL (September 2026), with made-up data. */
class QrzSiteTest {
    private fun page(details: String) = """
        <html><head><title>R0DEMO - Иван Демонстров :: Информация о позывном</title></head><body>
        <div class="content_center mRight_sidebar main_content">
        <div style="float:right;margin:0">
            <img src="/images/flags/48/Russia.png" alt="Россия" title="Россия" width="48" height="48" border="0"> <b>Россия</b>
        </div>
        <h1><b>R0DEMO</b></h1>
        <div id="infoBlock" class="inline">
            <h3 style="color:#ff9900;">ex CALL(s): <a href="/callbook/db/UA0DEMO">UA0DEMO</a></h3>
            <div style="font-size:1.2em">
                <b>Иван Петрович Д</b>
                <div style="color:gray;"><b>Ivan  D</b></div>            <br>
                Демоград,             Демонстрационная обл.                                    Россия<br>
                <br>RDA/URDA #KG-99<br>
            </div>
            <b>Просмотров:</b> 12<br>
            <div id="detailInfo" style="margin-top:25px;">$details</div>
        </div>
        <script>var x = 1;</script>
        </div></body></html>
    """.trimIndent()

    @Test
    fun anonymousPage() {
        val html = page("""<div>Для просмотра детальной информации вам необходимо <a href="/passport/login/">авторизоваться</a></div>""")
        assertTrue(QrzSite.needsLogin(html))
        val i = QrzSite.parse(html, "r0demo")!!
        assertEquals("R0DEMO", i.call)
        assertEquals("Иван Петрович", i.name)
        assertEquals("Демонстров", i.surname)
        assertEquals("Иван Петрович Демонстров", i.fullName)
        assertEquals("Демоград", i.city)
        assertEquals("Демонстрационная обл.", i.region)
        assertEquals("Россия", i.country)
        assertEquals("KG-99", i.rda)
        assertEquals("", i.locator)
        assertNull(i.position)
    }

    @Test
    fun detailsWithLocatorAndCoordinates() {
        val html = page("<table><tr><td>QTH-локатор:</td><td>ko85TS</td></tr><tr><td>Широта:</td><td>55.75</td></tr><tr><td>Долгота:</td><td>37.61</td></tr></table>")
        val i = QrzSite.parse(html, "R0DEMO")!!
        assertEquals(false, QrzSite.needsLogin(html))
        assertEquals("KO85ts", i.locator)
        assertEquals(55.75, i.lat)
        assertEquals(37.61, i.lon)
    }

    /** Photo before the data, a city without a region, and (logged in) the full name with the surname in the block. */
    @Test
    fun photoCityOnlyAndFullName() {
        val html = """
            <html><head><title>R0DEMO - Иван Демонстров :: Информация о позывном</title></head><body>
            <img src="/images/flags/48/Russia.png" alt="Россия" title="Россия">
            <div id="infoBlock" class="inline">
                <div style="width:500px;float:right"><div style="height:350px"><img src="https://static.example/photo.jpg" alt="R0DEMO"></div></div>
                <div style="font-family:Verdana,Helvetica,Arial Cyr,sans-serif;font-size:1.2em">
                    <b>Иван Петрович Демонстров</b>
                    <div style="color:gray;"><b>Ivan Petrovich Demonstrov</b></div>            <br>
                    Демоград                                                Россия<br>
                    <br>RDA/URDA #KG-99<br>
                </div>
                <b>Просмотров:</b> 12<br>
                <div id="detailInfo"></div>
            </div></body></html>
        """.trimIndent()
        val i = QrzSite.parse(html, "R0DEMO")!!
        assertEquals("Иван Петрович", i.name)
        assertEquals("Демонстров", i.surname)
        assertEquals("Иван Петрович Демонстров", i.fullName)
        assertEquals("Демоград", i.city)
        assertEquals("", i.region)
        assertEquals("KG-99", i.rda)
    }

    @Test
    fun unknownCallsign() {
        val html = "<html><title>Поиск в радиолюбительских позывных - ничего не найдено</title><h2>Ничего не найдено</h2>" +
            "<h3>Позывной <b>ZZ9DEMO</b> не найден в базе данных</h3></html>"
        assertNull(QrzSite.parse(html, "ZZ9DEMO"))
    }
}
