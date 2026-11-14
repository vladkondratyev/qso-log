package ru.r3xed.qsolog

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import java.awt.event.WindowEvent
import java.io.File
import java.time.LocalDateTime
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/**
 * What an error inside the window does. Compose's own handler shows "Error" and closes the window.
 *
 * Compose 1.6 has one error that is no error of the program: "Event can't be processed because we do not have an active
 * focus target". The field that had the focus is gone (the scale changed with Ctrl and the wheel, a list row scrolled
 * away) and the next key, even the Ctrl let go, finds no field. That one is written to errors.log and the focus is reset;
 * the window stays. Any other error is written there too and then handled as before: a message and the window closed
 * (the log is in its database already, nothing typed into a saved card is lost).
 */
@OptIn(ExperimentalComposeUiApi::class)
object WindowErrors : WindowExceptionHandlerFactory {
    /** Clears the focus of the window; set from its content (LocalFocusManager). */
    @Volatile var resetFocus: (() -> Unit)? = null

    val file: File get() = File(AppDirs.data, "errors.log")

    fun isLostFocus(t: Throwable) = t is IllegalStateException && t.message.orEmpty().contains("active focus target")

    fun write(t: Throwable) {
        runCatching {
            val f = file
            // Kept short: the newest 200 KB.
            if (f.length() > 200_000) f.writeText(f.readText().takeLast(100_000))
            f.appendText("${LocalDateTime.now()} QSO-LOG $APP_VERSION ${System.getProperty("os.name")} ${System.getProperty("os.version")}\n${t.stackTraceToString()}\n")
        }
    }

    override fun exceptionHandler(window: java.awt.Window) = WindowExceptionHandler { t ->
        write(t)
        if (isLostFocus(t)) {
            SwingUtilities.invokeLater { runCatching { resetFocus?.invoke() } }
            return@WindowExceptionHandler
        }
        SwingUtilities.invokeLater {
            if (window.isDisplayable) JOptionPane.showMessageDialog(
                window,
                (t.message ?: t.javaClass.simpleName) + "\n\n" + tr("Подробности записаны в файл %s — пришлите его, пожалуйста, вместе с описанием, что вы делали.", file.absolutePath),
                "QSO-LOG", JOptionPane.ERROR_MESSAGE,
            )
            window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
        }
    }
}

/**
 * The folder of the last file saved or opened (ADIF, CSV, Cabrillo, a voice note): the next file dialog starts there,
 * not in the home folder. Kept on this computer only ("last_dir" is a local key of the settings sync).
 */
object FileDirs {
    @Volatile var prefs: Settings? = null

    /** The folder to start in, when it still exists. */
    val last: String? get() = prefs?.text("last_dir")?.ifBlank { null }?.takeIf { File(it).isDirectory }

    fun remember(dir: String?) {
        if (!dir.isNullOrBlank()) prefs?.putText("last_dir", dir)
    }

    /** [d] opens in the last folder and, once closed with a file, remembers its folder. */
    fun show(d: java.awt.FileDialog) {
        last?.let { d.directory = it }
        d.isVisible = true
        if (d.file != null) remember(d.directory)
    }
}
