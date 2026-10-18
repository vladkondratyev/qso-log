package ru.r3xed.qsolog

import com.fazecast.jSerialComm.SerialPort
import ru.r3xed.qsolog.cat.CatConfig
import ru.r3xed.qsolog.cat.CatLink
import ru.r3xed.qsolog.cat.CatLinkType
import ru.r3xed.qsolog.cat.CatProtocol
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
    fun open(c: CatConfig): CatLink =
        if (c.protocol.network || c.link == CatLinkType.NETWORK) tcpLink(c.host, c.port) else openSerial(c)

    private fun openSerial(c: CatConfig): CatLink {
        val name = c.device.ifBlank { ports().firstOrNull()?.key }
            ?: throw IOException(tr("COM-порт не найден. Подключите трансивер кабелем USB или интерфейсом CAT/CI-V и нажмите «Обновить список»."))
        val p = SerialPort.getCommPort(name)
        p.setComPortParameters(c.baud, 8, if (c.protocol == CatProtocol.YAESU_OLD) SerialPort.TWO_STOP_BITS else SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
        p.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
        p.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)
        if (!p.openPort()) throw IOException(tr("Не удалось открыть порт %s: он занят другой программой или нет доступа", name))
        // DTR and RTS may key the transmitter or send CW (the radio's "USB SEND / USB keying" settings): keep them low.
        p.clearDTR()
        p.clearRTS()
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
