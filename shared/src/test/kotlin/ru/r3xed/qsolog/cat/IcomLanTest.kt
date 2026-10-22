package ru.r3xed.qsolog.cat

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IcomLanTest {
    private fun le16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, i: Int) = le16(b, i) or (le16(b, i + 2) shl 16)
    private fun be32(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
    private fun putLe16(b: ByteArray, i: Int, v: Int) { b[i] = v.toByte(); b[i + 1] = (v shr 8).toByte() }
    private fun putLe32(b: ByteArray, i: Int, v: Int) { putLe16(b, i, v); putLe16(b, i + 2, v shr 16) }
    private fun putBe16(b: ByteArray, i: Int, v: Int) { b[i] = (v shr 8).toByte(); b[i + 1] = v.toByte() }

    /**
     * A radio on the network as far as the log needs it: the handshake of each stream, the login with a name and
     * password, its capabilities, the CI-V stream answering frequency questions of an IC-705 at A4h.
     */
    private class FakeRadio(val user: String, val password: String, val busyBy: String? = null) {
        val control = DatagramSocket(0, InetAddress.getLoopbackAddress())
        val civ = DatagramSocket(0, InetAddress.getLoopbackAddress())
        val audio = DatagramSocket(0, InetAddress.getLoopbackAddress())
        @Volatile var loginUser: ByteArray? = null
        @Volatile var running = true
        @Volatile var civFrames = 0
        val t = IcomLanTest()

        fun reply(s: DatagramSocket, to: DatagramPacket, b: ByteArray) = s.send(DatagramPacket(b, b.size, to.socketAddress))

        fun control(type: Int, theirId: Int, myId: Int): ByteArray = ByteArray(0x10).also {
            t.putLe32(it, 0, 0x10); t.putLe16(it, 4, type); t.putLe32(it, 8, myId); t.putLe32(it, 0x0c, theirId)
        }

        fun serve(s: DatagramSocket, id: Int, on: (ByteArray, DatagramPacket) -> Unit) = thread(isDaemon = true) {
            s.soTimeout = 200
            val buf = ByteArray(2048)
            while (running) {
                val p = DatagramPacket(buf, buf.size)
                try { s.receive(p) } catch (e: SocketTimeoutException) { continue } catch (e: Exception) { return@thread }
                val b = buf.copyOf(p.length)
                val type = t.le16(b, 4)
                val sender = t.le32(b, 8)
                if (b.size == 0x10 && type == 0x03) reply(s, p, control(0x04, sender, id))
                else if (b.size == 0x10 && type == 0x06) reply(s, p, control(0x06, sender, id))
                else on(b, p)
            }
        }

        fun start() {
            serve(control, 0x1111) { b, p ->
                val sender = t.le32(b, 8)
                if (b.size == 0x80) {
                    loginUser = b.copyOfRange(0x40, 0x50)
                    val r = ByteArray(0x60)
                    t.putLe32(r, 0, 0x60); t.putLe32(r, 8, 0x1111); t.putLe32(r, 0x0c, sender)
                    r[0x1a] = b[0x1a]; r[0x1b] = b[0x1b]
                    t.putLe32(r, 0x1c, 0x12345678)
                    val ok = b.copyOfRange(0x40, 0x40 + user.length).contentEquals(IcomLanLink.passcode(user)) &&
                        b.copyOfRange(0x50, 0x50 + password.length).contentEquals(IcomLanLink.passcode(password))
                    if (!ok) t.putLe32(r, 0x30, 0xFEFFFFFF.toInt())
                    "FTTH".toByteArray().copyInto(r, 0x40)
                    reply(control, p, r)
                    if (!ok) return@serve
                    // Capabilities: one radio, its MAC address, name and CI-V address.
                    val caps = ByteArray(0x42 + 0x66)
                    t.putLe32(caps, 0, caps.size); t.putLe32(caps, 8, 0x1111); t.putLe32(caps, 0x0c, sender)
                    t.putBe16(caps, 0x40, 1)
                    t.putLe16(caps, 0x42 + 7, 0x8010)
                    byteArrayOf(1, 2, 3, 4, 5, 6).copyInto(caps, 0x42 + 0x0a)
                    "IC-705".toByteArray().copyInto(caps, 0x42 + 0x10)
                    caps[0x42 + 0x52] = 0xA4.toByte()
                    reply(control, p, caps)
                    val info = ByteArray(0x90)
                    t.putLe32(info, 0, 0x90)
                    if (busyBy != null) { t.putLe32(info, 0x60, 1); busyBy.toByteArray().copyInto(info, 0x64) }
                    reply(control, p, info)
                } else if (b.size == 0x90 && b[0x15].toInt() == 0x03) {
                    // The stream request: the MAC address of the capabilities comes back.
                    check(b.copyOfRange(0x2a, 0x30).contentEquals(byteArrayOf(1, 2, 3, 4, 5, 6)))
                    check(t.be32(b, 0x7c) > 0)
                    val st = ByteArray(0x50)
                    t.putLe32(st, 0, 0x50)
                    t.putBe16(st, 0x42, civ.localPort)
                    t.putBe16(st, 0x46, audio.localPort)
                    reply(control, p, st)
                }
            }
            serve(audio, 0x3333) { _, _ -> }
            serve(civ, 0x2222) { b, p ->
                if (b.size > 0x15 && t.le16(b, 0x11) + 0x15 == b.size) {
                    civFrames++
                    val frame = b.copyOfRange(0x15, b.size)
                    // FE FE A4 E0 03 FD → the frequency 7 074 000 Hz.
                    if (frame.size == 6 && frame[4].toInt() == 0x03) {
                        val answer = CivDriver.frame(0xE0, 0xA4, 0x03, data = CivDriver.civFreqBytes(7_074_000))
                        val r = ByteArray(0x15 + answer.size)
                        t.putLe32(r, 0, r.size)
                        r[0x10] = 0xC1.toByte()
                        t.putLe16(r, 0x11, answer.size)
                        answer.copyInto(r, 0x15)
                        reply(civ, p, r)
                    }
                }
            }
        }

        fun stop() {
            running = false
            control.close(); civ.close(); audio.close()
        }
    }

    @Test
    fun passcodeIsIcomsTable() {
        // The table is a shuffle of the printable characters; the position shifts the character.
        assertContentEquals(byteArrayOf(0x47, 0x47), IcomLanLink.passcode(" \u001f"))
        assertEquals(16, IcomLanLink.passcode("a-very-long-user-name").size)
        val all = (32..126).map { IcomLanLink.passcode(it.toChar().toString())[0].toInt() and 0xFF }
        assertEquals((32..126).toSet(), all.toSet())
    }

    @Test
    fun logsInAndPollsTheFrequency() {
        val radio = FakeRadio("r0demo", "secret123").apply { start() }
        try {
            val link = IcomLanLink("127.0.0.1", radio.control.localPort, "r0demo", "secret123", timeoutMs = 5000)
            assertEquals("IC-705", link.radioName)
            assertEquals(0xA4, link.radioCiv)
            val driver = CivDriver(0xA4, probe = false)
            val s = driver.poll(CatIo(link), RigState())
            assertEquals(7_074_000L, s.freqHz)
            assertTrue(radio.civFrames > 0)
            link.close()
        } finally {
            radio.stop()
        }
    }

    @Test
    fun wrongPasswordIsSaid() {
        val radio = FakeRadio("r0demo", "secret123").apply { start() }
        try {
            val e = assertFailsWith<IOException> { IcomLanLink("127.0.0.1", radio.control.localPort, "r0demo", "wrong", timeoutMs = 5000) }
            assertTrue(e.message!!.contains("Network User"), e.message)
        } finally {
            radio.stop()
        }
    }

    @Test
    fun busyRadioNamesTheOtherComputer() {
        val radio = FakeRadio("r0demo", "secret123", busyBy = "SHACK-PC").apply { start() }
        try {
            val e = assertFailsWith<IOException> { IcomLanLink("127.0.0.1", radio.control.localPort, "r0demo", "secret123", timeoutMs = 1500) }
            assertTrue(e.message!!.contains("SHACK-PC"), e.message)
        } finally {
            radio.stop()
        }
    }

    @Test
    fun silentAddressIsSaid() {
        val e = assertFailsWith<IOException> { IcomLanLink("127.0.0.1", 9, "u", "p", timeoutMs = 1200) }
        assertTrue(e.message!!.contains("Network Control"), e.message)
    }
}
