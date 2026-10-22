package ru.r3xed.qsolog.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import ru.r3xed.qsolog.cat.CatConfig
import ru.r3xed.qsolog.cat.CatLink
import ru.r3xed.qsolog.cat.CatLinkType
import ru.r3xed.qsolog.cat.CatProtocol
import ru.r3xed.qsolog.cat.IcomLanLink
import ru.r3xed.qsolog.cat.StreamLink
import ru.r3xed.qsolog.cat.tcpLink
import ru.r3xed.qsolog.cw.CwConfig
import ru.r3xed.qsolog.cw.CwLine
import ru.r3xed.qsolog.cw.KeyLine
import ru.r3xed.qsolog.tr
import java.io.IOException
import java.util.UUID

/** A radio or an adapter the phone can see: [key] is kept in the settings, [title] is shown. */
data class CatDevice(val key: String, val title: String)

/** The phone's ways to the transceiver: a USB cable (OTG), Bluetooth (serial profile), the network. */
class CatAndroid(private val context: Context) {
    private val usb get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** Serial adapters on USB: the radio's own USB (CP210x, FTDI), CI-V and CAT cables (CH340, PL2303, CDC). */
    fun usbDevices(): List<CatDevice> = usbDrivers().flatMap { d ->
        d.ports.indices.map { i ->
            val dev = d.device
            val name = listOfNotNull(dev.manufacturerName?.trim(), dev.productName?.trim()).distinct().joinToString(" ")
                .ifBlank { d.javaClass.simpleName.removeSuffix("SerialDriver") }
            CatDevice(usbKey(dev, i), name + (if (d.ports.size > 1) " · " + tr("порт %s", i + 1) else "") +
                " (%04X:%04X)".format(dev.vendorId, dev.productId))
        }
    }

    private fun usbDrivers(): List<UsbSerialDriver> {
        val found = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
        // Icom's own USB (IC-705, IC-905) is a CDC serial port the default table may not list by its ids.
        val extra = usb.deviceList.values.filter { dev -> dev.vendorId == ICOM_VID && found.none { it.device.deviceId == dev.deviceId } }
            .map { CdcAcmSerialDriver(it) }
        return found + extra
    }

    private fun usbKey(dev: UsbDevice, port: Int) = "%04x:%04x:%d".format(dev.vendorId, dev.productId, port)

    @SuppressLint("MissingPermission")
    fun bluetoothDevices(): List<CatDevice> {
        if (!bluetoothAllowed()) return emptyList()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty().map { CatDevice(it.address, (it.name ?: it.address) + " (" + it.address + ")") }.sortedBy { it.title }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun bluetoothAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private var permissionAsked = mutableSetOf<String>()

    /** Opens the link of [c]. Throws with a message for the user when it cannot. */
    fun open(c: CatConfig, baud: Int = c.baud): CatLink = when {
        c.viaIcomLan -> IcomLanLink(c.host, c.port, c.lanUser, c.lanPassword, computer = "QSO-LOG-" + Build.MODEL.take(8))
        c.protocol.network || c.link == CatLinkType.NETWORK -> tcpLink(c.host, c.port)
        c.link == CatLinkType.BLUETOOTH -> openBluetooth(c)
        else -> openUsb(c, baud)
    }

    private fun openUsb(c: CatConfig, baud: Int): CatLink {
        val (p, key) = openUsbPort(c.device, baud, twoStops = c.protocol == CatProtocol.YAESU_OLD, rts = c.rtsOn)
        catUsb = key to p
        return object : CatLink {
            override fun write(b: ByteArray) = p.write(b, 1000)
            override fun read(buf: ByteArray, timeoutMs: Int): Int = p.read(buf, timeoutMs.coerceAtLeast(1))
            override fun close() {
                if (catUsb?.second === p) catUsb = null
                runCatching { p.close() }
            }
        }
    }

    /** The CAT's open USB port and its key: the CW key line on the same adapter switches its DTR or RTS. */
    @Volatile private var catUsb: Pair<String, UsbSerialPort>? = null

    /** Opens the USB serial port [device] (a [usbKey]; the first adapter when not found). Returns it and its key. */
    private fun openUsbPort(
        device: String, baud: Int, twoStops: Boolean, rts: Boolean = false,
        missing: () -> String = { tr("Трансивер не подключён по USB. Нужен кабель OTG (USB-C или micro-USB → USB трансивера)") },
    ): Pair<UsbSerialPort, String> {
        val drivers = usbDrivers()
        if (drivers.isEmpty()) throw IOException(missing())
        val parts = device.split(':')
        val port = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val driver = drivers.firstOrNull { usbKey(it.device, 0).substringBeforeLast(':') == device.substringBeforeLast(':') && port < it.ports.size }
            ?: drivers.first()
        val dev = driver.device
        if (!usb.hasPermission(dev)) {
            if (permissionAsked.add(dev.deviceName)) {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pi = PendingIntent.getBroadcast(context, 0, Intent(USB_PERMISSION).setPackage(context.packageName), flags)
                usb.requestPermission(dev, pi)
            }
            throw IOException(tr("Разрешите QSO-LOG доступ к USB-устройству в появившемся окне"))
        }
        val conn = usb.openDevice(dev) ?: throw IOException(tr("Не удалось открыть USB-устройство"))
        val index = port.coerceIn(0, driver.ports.size - 1)
        val p = driver.ports[index]
        try {
            p.open(conn)
            p.setParameters(baud, 8, if (twoStops) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            // DTR and RTS may key the transmitter or send CW (the radio's "USB SEND / USB keying" settings): keep them low.
            // RTS is on for radios that send only while it is (RTS/CTS flow control of the TS-590, TS-890, TS-990).
            runCatching { p.dtr = false }
            runCatching { p.rts = rts }
        } catch (e: Exception) {
            runCatching { p.close() }
            throw IOException(tr("Не удалось открыть USB-устройство") + ": " + (e.message ?: e.javaClass.simpleName))
        }
        return p to usbKey(dev, index)
    }

    /**
     * The CW key line: [CwConfig.line] of the USB adapter [CwConfig.device]. When CAT already has that adapter open (one
     * CP2102 cable for both CI-V and the key), its port is shared; otherwise the adapter is opened for the key alone.
     */
    fun openKeyLine(c: CwConfig): KeyLine {
        val shared = catUsb?.takeIf { (key, _) -> c.device.isBlank() || key == c.device }?.second
        val p = shared ?: openUsbPort(c.device, 9600, twoStops = false) { tr("USB-адаптер ключа не подключён. Нужен кабель OTG и адаптер CP2102, CH340 или FTDI") }.first
        fun set(line: CwLine, on: Boolean) {
            if (line == CwLine.DTR) p.dtr = on else p.rts = on
        }
        val other = if (c.line == CwLine.DTR) CwLine.RTS else CwLine.DTR
        set(c.line, c.invert)
        return object : KeyLine {
            override fun key(down: Boolean) = set(c.line, down != c.invert)
            override fun ptt(on: Boolean) = set(other, on)
            override fun close() {
                runCatching { set(c.line, false) }
                if (shared == null) runCatching { p.close() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openBluetooth(c: CatConfig): CatLink {
        if (!bluetoothAllowed()) throw IOException(tr("Разрешите QSO-LOG доступ к устройствам Bluetooth (настройки CAT)"))
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: throw IOException(tr("На телефоне нет Bluetooth"))
        if (!adapter.isEnabled) throw IOException(tr("Bluetooth выключен"))
        if (c.device.isBlank()) throw IOException(tr("Выберите трансивер из сопряжённых устройств Bluetooth"))
        val dev = adapter.getRemoteDevice(c.device)
        runCatching { adapter.cancelDiscovery() }
        var socket: BluetoothSocket = dev.createRfcommSocketToServiceRecord(SPP)
        try {
            socket.connect()
        } catch (e: IOException) {
            runCatching { socket.close() }
            socket = dev.createInsecureRfcommSocketToServiceRecord(SPP)
            try {
                socket.connect()
            } catch (e2: IOException) {
                runCatching { socket.close() }
                throw IOException(tr("Нет соединения по Bluetooth с %s: включите Bluetooth на трансивере и сопряжение с телефоном", dev.name ?: c.device))
            }
        }
        val s = socket
        return StreamLink(s.inputStream, s.outputStream) { s.close() }
    }

    companion object {
        const val ICOM_VID = 0x0C26
        const val USB_PERMISSION = "ru.r3xed.qsolog.USB_PERMISSION"
        /** Serial Port Profile: the radio's Bluetooth "data" link (IC-705) and Bluetooth CAT adapters. */
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
