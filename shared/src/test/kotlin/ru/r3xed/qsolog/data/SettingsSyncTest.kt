package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSyncTest {
    private val all = mapOf("theme" to "dark", "qrz_login" to "UA3DEMO", "qrz_password" to "x", "cat_device" to "COM3", "sheet_url" to "u", "ui_scale" to "S70")

    @Test
    fun whatGoes() {
        assertEquals(mapOf("theme" to "dark"), SettingsSync.outgoing(all, credentials = false))
        assertEquals(setOf("theme", "qrz_login", "qrz_password"), SettingsSync.outgoing(all, credentials = true).keys)
        assertTrue("qrz_password" in SettingsSync.SECRET_KEYS)
    }

    @Test
    fun whoWins() {
        val local = mapOf("theme" to "dark")
        val synced = SettingsSync.State("2026-11-10T10:00:00.000Z", SettingsSync.hash(local))
        // Another device wrote later: take it.
        val r = SettingsSync.Remote("2026-11-10T11:00:00.000Z", "phone", mapOf("theme" to "light"))
        assertEquals(SettingsSync.Step.Take(mapOf("theme" to "light"), r.updated), SettingsSync.plan(r, local, synced, "pc"))
        // This device wrote it: nothing new, unless something changed here.
        val mine = r.copy(updated = synced.remoteStamp, device = "pc")
        assertEquals(SettingsSync.Step.Nothing, SettingsSync.plan(mine, local, synced, "pc"))
        assertEquals(SettingsSync.Step.Send, SettingsSync.plan(mine, mapOf("theme" to "custom"), synced, "pc"))
        // An empty table gets this device's settings.
        assertEquals(SettingsSync.Step.Send, SettingsSync.plan(SettingsSync.Remote("", "", emptyMap()), local, synced, "pc"))
    }

    /** The client against a stand-in of the script: what is sent comes back, with the device and the time. */
    @Test
    fun exchangeWithTheScript() = kotlinx.coroutines.runBlocking {
        var stored = mapOf<String, Any?>()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/exec") { ex ->
            val body = if (ex.requestMethod == "POST") {
                stored = MiniJson.parse(ex.requestBody.readBytes().decodeToString()) as Map<String, Any?>
                MiniJson.encode(mapOf("ok" to true, "app" to "QSO-LOG-SETTINGS", "updated" to "2026-11-10T12:00:00.000Z"))
            } else MiniJson.encode(mapOf("ok" to true, "app" to "QSO-LOG-SETTINGS", "updated" to "2026-11-10T12:00:00.000Z", "device" to stored["device"], "settings" to stored["settings"]))
            val b = body.toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/exec"
            assertEquals("2026-11-10T12:00:00.000Z", SettingsSync.send(url, "pc-1", mapOf("theme" to "dark", "keymap" to "a=b|c=d")))
            val r = SettingsSync.fetch(url)
            assertEquals("pc-1", r.device)
            assertEquals(mapOf("theme" to "dark", "keymap" to "a=b|c=d"), r.settings)
        } finally {
            server.stop(0)
        }
    }
}
