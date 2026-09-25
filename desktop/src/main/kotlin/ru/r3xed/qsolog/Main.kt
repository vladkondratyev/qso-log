package ru.r3xed.qsolog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ru.r3xed.qsolog.ui.EditPane
import ru.r3xed.qsolog.ui.LocalExtra
import ru.r3xed.qsolog.ui.LogPane
import ru.r3xed.qsolog.ui.QsoTheme
import ru.r3xed.qsolog.ui.SettingsPane
import java.awt.Dimension
import java.awt.FileDialog
import java.io.File

val IS_MAC = System.getProperty("os.name").lowercase().contains("mac")
val NEW_SHORTCUT = if (IS_MAC) "⌘N" else "Ctrl+N"
val SAVE_SHORTCUT = if (IS_MAC) "⌘S" else "Ctrl+S"

private fun shortcut(key: Key) = KeyShortcut(key, meta = IS_MAC, ctrl = !IS_MAC)

fun main() {
    if (IS_MAC) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.name", "QSO Log")
    }
    application {
        val state = remember { AppState() }
        val windowState = rememberWindowState(width = 1280.dp, height = 880.dp, position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = { state.flushSettings(); exitApplication() },
            title = "QSO Log",
            icon = painterResource("icon.png"),
            state = windowState,
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && state.pane != Pane.Empty) {
                    state.pane = Pane.Empty; true
                } else false
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(980, 640) }
            val exportCsv = {
                chooseFile(window, "Экспорт лога в CSV", save = true, suggested = state.csvFileName())?.let(state::exportCsv)
                Unit
            }
            val exportAdif = {
                chooseFile(window, "Экспорт лога в ADIF", save = true, suggested = state.adifFileName(), ext = "adi")?.let(state::exportAdif)
                Unit
            }
            val importAdif = {
                chooseFile(window, "Импорт лога из ADIF", save = false, ext = "adi")?.let(state::importAdif)
                Unit
            }
            val importCsv = {
                chooseFile(window, "Импорт лога из CSV", save = false)?.let(state::importCsv)
                Unit
            }
            MenuBar {
                Menu("Файл") {
                    Item("Новая связь", shortcut = shortcut(Key.N), onClick = state::newQso)
                    Item("Сохранить связь", enabled = state.pane == Pane.Edit, shortcut = shortcut(Key.S), onClick = state::trySave)
                    Separator()
                    Item("Экспорт лога в ADIF…", onClick = exportAdif)
                    Item("Импорт лога из ADIF…", onClick = importAdif)
                    Separator()
                    Item("Экспорт лога в CSV…", onClick = exportCsv)
                    Item("Импорт лога из CSV…", onClick = importCsv)
                    Separator()
                    Item("Настройки", shortcut = shortcut(Key.Comma), onClick = { state.pane = Pane.Settings })
                    if (!IS_MAC) {
                        Separator()
                        Item("Выход", onClick = { state.flushSettings(); exitApplication() })
                    }
                }
            }
            QsoTheme { App(state, exportCsv, importCsv) }
        }
    }
}

@Composable
private fun App(state: AppState, onExport: () -> Unit, onImport: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        state.messages.collect { m ->
            val r = snackbar.showSnackbar(
                m.text,
                actionLabel = if (m.undo != null) "Отменить" else null,
                duration = if (m.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) m.undo?.invoke()
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxSize()) {
            LogPane(state, NEW_SHORTCUT, Modifier.width(440.dp).fillMaxHeight())
            VerticalDivider(color = LocalExtra.current.line)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (state.pane) {
                    Pane.Empty -> EmptyPane(state)
                    Pane.Edit -> EditPane(state)
                    Pane.Settings -> SettingsPane(state, onExport, onImport)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).widthIn(max = 640.dp))
    }
}

@Composable
private fun EmptyPane(state: AppState) {
    val x = LocalExtra.current
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Готов к записи", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Нажмите «Новая связь» или $NEW_SHORTCUT. Чтобы исправить запись, выберите её в логе слева.",
            style = MaterialTheme.typography.bodyLarge, color = x.muted, modifier = Modifier.widthIn(max = 560.dp),
        )
        val s = state.settings
        if (s.myLocator.isBlank()) {
            Text(
                "Укажите свой QTH-локатор в настройках, чтобы видеть расстояние до абонентов.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.widthIn(max = 560.dp),
            )
        } else {
            Text("${s.myCall} · ${s.myLocator}" + if (s.myQth.isNotBlank()) " · ${s.myQth}" else "", style = MaterialTheme.typography.titleMedium, color = x.muted)
        }
    }
}

/** Native file dialog (Finder / Explorer). */
private fun chooseFile(window: ComposeWindow, title: String, save: Boolean, suggested: String? = null, ext: String = "csv"): File? {
    val dialog = FileDialog(window, title, if (save) FileDialog.SAVE else FileDialog.LOAD)
    if (suggested != null) dialog.file = suggested
    if (!save) {
        dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".$ext") || it.endsWith(".txt") || (ext == "adi" && it.endsWith(".adif")) } }
        if (!IS_MAC) dialog.file = "*.$ext" // Windows ignores the filter above and uses this pattern instead
    }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return if (save && !name.lowercase().endsWith(".$ext")) File(dir, "$name.$ext") else File(dir, name)
}
