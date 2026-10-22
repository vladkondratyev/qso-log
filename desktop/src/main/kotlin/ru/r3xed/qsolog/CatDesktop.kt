package ru.r3xed.qsolog

import com.fazecast.jSerialComm.SerialPort
import ru.r3xed.qsolog.cat.CatConfig
import ru.r3xed.qsolog.cat.CatLink
import ru.r3xed.qsolog.cat.CatLinkType
import ru.r3xed.qsolog.cat.CatProtocol
import ru.r3xed.qsolog.cat.IcomLanLink
import ru.r3xed.qsolog.cat.OmniRigDriver
import ru.r3xed.qsolog.cat.StreamLink
import ru.r3xed.qsolog.cat.tcpLink
import ru.r3xed.qsolog.cw.CwConfig
import ru.r3xed.qsolog.cw.CwLine
import ru.r3xed.qsolog.cw.KeyLine
import java.io.IOException

/** A COM port the computer has: [key] is kept in the settings, [title] is shown. */
data class CatDevice(val key: String, val title: String)

/** The computer's ways to the transceiver: a COM port (the radio's USB, a CI-V or CAT cable) or the network. */
object CatDesktop {
    fun ports(): List<CatDevice> = try {
        SerialPort.getCommPorts()
            // macOS lists each port twice: the "tty." twin waits for a carrier, the "cu." one is for programs.
            .filter { !it.systemPortName.startsWith("tty.") }
            .map { p ->
                val about = listOf(p.descriptivePortName, p.portDescription).map { it.trim() }.filter { it.isNotEmpty() && it != p.systemPortName }.distinct().joinToString(" · ")
                CatDevice(p.systemPortName, if (about.isEmpty()) p.systemPortName else "${p.systemPortName} — $about")
            }
            .sortedBy { it.key }
    } catch (e: Throwable) {
        emptyList() // no native library for this system: the network still works
    }

    /** Opens the link of [c]. Throws with a message for the user when it cannot. */
    fun open(c: CatConfig, baud: Int = c.baud): CatLink = when {
        c.protocol == CatProtocol.OMNIRIG -> openOmniRig(c)
        c.viaIcomLan -> IcomLanLink(c.host, c.port, c.lanUser, c.lanPassword, computer = computerName())
        c.protocol.network || c.link == CatLinkType.NETWORK -> tcpLink(c.host, c.port)
        else -> openSerial(c, baud)
    }

    /** "QSO-LOG-SHACK": the name the radio shows for the connected program (16 characters at most). */
    private fun computerName(): String =
        "QSO-LOG-" + (System.getenv("COMPUTERNAME") ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull() ?: "PC").take(8)

    /**
     * OmniRig through a PowerShell helper (see [OmniRigDriver.SCRIPT]): its first line says whether OmniRig is there.
     */
    private fun openOmniRig(c: CatConfig): CatLink {
        if (!IS_WINDOWS) throw IOException(tr("OmniRig работает только в Windows"))
        val script = java.io.File(AppDirs.data, "omnirig.ps1")
        script.writeText(OmniRigDriver.SCRIPT)
        val p = try {
            ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath, c.omniRig.toString())
                .redirectErrorStream(true).start()
        } catch (e: IOException) {
            throw IOException(tr("Не удалось запустить PowerShell для OmniRig: %s", e.message ?: ""))
        }
        // The first line, byte by byte: the rest of the stream belongs to the link's reader.
        val first = StringBuilder()
        val deadline = System.currentTimeMillis() + 15_000
        val input = p.inputStream
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val b = input.read()
                if (b < 0 || b == '\n'.code) break
                if (b != '\r'.code) first.append(b.toChar())
            } else if (!p.isAlive) break else Thread.sleep(20)
        }
        val line = first.toString().trim()
        if (!line.startsWith("OK")) {
            p.destroy()
            throw IOException(
                if (line.startsWith("ERR")) tr("OmniRig не найден: установите OmniRig и настройте в нём трансивер (Rig 1 или Rig 2). %s", line.removePrefix("ERR").trim())
                else tr("OmniRig не ответил")
            )
        }
        return StreamLink(input, p.outputStream) { p.destroy() }
    }

    private fun openSerial(c: CatConfig, baud: Int): CatLink {
        val name = c.device.ifBlank { ports().firstOrNull()?.key }
            ?: throw IOException(tr("COM-порт не найден. Подключите трансивер кабелем USB или интерфейсом CAT/CI-V и нажмите «Обновить список»."))
        val p = SerialPort.getCommPort(name)
        p.setComPortParameters(baud, 8, if (c.protocol == CatProtocol.YAESU_OLD) SerialPort.TWO_STOP_BITS else SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
        p.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
        p.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)
        if (!p.openPort()) throw IOException(tr("Не удалось открыть порт %s: он занят другой программой или нет доступа", name))
        // DTR and RTS may key the transmitter or send CW (the radio's "USB SEND / USB keying" settings): keep them low.
        // RTS is on for radios that send only while it is (RTS/CTS flow control of the TS-590, TS-890, TS-990).
        p.clearDTR()
        if (c.rtsOn) p.setRTS() else p.clearRTS()
        catPort = name to p
        return object : CatLink {
            override fun write(b: ByteArray) {
                if (p.writeBytes(b, b.size) < 0) throw IOException(tr("Порт %s закрыт", name))
            }

            override fun read(buf: ByteArray, timeoutMs: Int): Int {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (true) {
                    val n = p.bytesAvailable()
                    if (n < 0) throw IOException(tr("Порт %s закрыт", name))
                    if (n > 0) return p.readBytes(buf, minOf(n, buf.size)).coerceAtLeast(0)
                    if (System.currentTimeMillis() >= deadline) return 0
                    Thread.sleep(3)
                }
            }

            override fun close() {
                if (catPort?.second === p) catPort = null
                p.closePort()
            }
        }
    }

    /** The CAT's open COM port: the CW key line on the same port switches its DTR or RTS (one port, two uses). */
    @Volatile private var catPort: Pair<String, SerialPort>? = null

    /**
     * The CW key line: [CwConfig.line] of the COM port [CwConfig.device]. When CAT already has that port open (one
     * CP2102 cable for both CI-V and the key), it is shared — a port opens only once.
     */
    fun openKeyLine(c: CwConfig): KeyLine {
        val name = c.device.ifBlank { ports().firstOrNull()?.key } ?: throw IOException(tr("COM-порт ключа не выбран"))
        val shared = catPort?.takeIf { it.first == name }?.second
        val p = shared ?: SerialPort.getCommPort(name).also {
            it.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
            it.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
            if (!it.openPort()) throw IOException(tr("Не удалось открыть порт %s: он занят другой программой или нет доступа", name))
            it.clearDTR()
            it.clearRTS()
        }
        fun set(line: CwLine, on: Boolean) {
            if (line == CwLine.DTR) { if (on) p.setDTR() else p.clearDTR() } else { if (on) p.setRTS() else p.clearRTS() }
        }
        val other = if (c.line == CwLine.DTR) CwLine.RTS else CwLine.DTR
        set(c.line, c.invert)
        return object : KeyLine {
            override fun key(down: Boolean) = set(c.line, down != c.invert)
            override fun ptt(on: Boolean) = set(other, on)
            override fun close() {
                runCatching { set(c.line, false) }
                if (shared == null) p.closePort()
            }
        }
    }
}
