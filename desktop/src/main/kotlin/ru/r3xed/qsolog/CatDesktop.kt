package ru.r3xed.qsolog

import com.fazecast.jSerialComm.SerialPort
import ru.r3xed.qsolog.cat.CatConfig
import ru.r3xed.qsolog.cat.CatLink
import ru.r3xed.qsolog.cat.CatLinkType
import ru.r3xed.qsolog.cat.CatProtocol
import ru.r3xed.qsolog.cat.tcpLink
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
        if (c.protocol == CatProtocol.RIGCTLD || c.link == CatLinkType.NETWORK) tcpLink(c.host, c.port) else openSerial(c)

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
                p.closePort()
            }
        }
    }
}
