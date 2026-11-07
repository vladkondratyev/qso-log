package ru.r3xed.qsolog

import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * The system's notifications on the computer (DX cluster spots): macOS Notification Center, the Windows toast, the Linux
 * notification daemon — through a tray icon made once. Where the system has no tray, nothing is shown.
 */
object DesktopNotify {
    private var icon: TrayIcon? = null

    @Synchronized
    fun show(title: String, text: String) {
        if (java.awt.GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return
        val t = icon ?: run {
            val img = javaClass.getResourceAsStream("/icon.png")?.use { javax.imageio.ImageIO.read(it) }
                ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            TrayIcon(img, "QSO-LOG").apply { isImageAutoSize = true }.also {
                runCatching { SystemTray.getSystemTray().add(it) }
                icon = it
            }
        }
        t.displayMessage(title, text, TrayIcon.MessageType.INFO)
    }
}
