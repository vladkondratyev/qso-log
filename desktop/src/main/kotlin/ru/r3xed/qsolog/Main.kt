package ru.r3xed.qsolog

import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import ru.r3xed.qsolog.ui.ContestExportDialog
import ru.r3xed.qsolog.ui.ExportDialog
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.ui.EditPane
import ru.r3xed.qsolog.ui.LocalExtra
import ru.r3xed.qsolog.ui.LogPane
import ru.r3xed.qsolog.ui.MapPane
import ru.r3xed.qsolog.ui.QsoTheme
import ru.r3xed.qsolog.ui.SettingsPane
import ru.r3xed.qsolog.ui.WelcomePane
import ru.r3xed.qsolog.ui.ReferencePane
import java.awt.Dimension
import java.awt.FileDialog
import java.io.File

val IS_MAC = System.getProperty("os.name").lowercase().contains("mac")
val IS_WINDOWS = System.getProperty("os.name").lowercase().contains("win")
val NEW_SHORTCUT = if (IS_MAC) "⌘N" else "Ctrl+N"
val SAVE_SHORTCUT = if (IS_MAC) "⌘S" else "Ctrl+S"
val NEXT_SHORTCUT = if (IS_MAC) "⇧⌘S" else "Ctrl+Shift+S"

private fun shortcut(key: Key) = KeyShortcut(key, meta = IS_MAC, ctrl = !IS_MAC)

fun main() {
    if (IS_MAC) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.name", "QSO-LOG")
    }
    application {
        val state = remember { AppState() }
        val windowState = rememberWindowState(width = 1280.dp, height = 880.dp, position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = { state.flushSettings(); exitApplication() },
            title = "QSO-LOG",
            icon = painterResource("icon.png"),
            state = windowState,
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) when {
                    state.selecting -> { state.clearSelection(); true }
                    state.pane == Pane.Edit -> { if (state.confirmClose) state.confirmClose = false else state.requestClose(); true }
                    state.pane == Pane.Settings -> { state.closeSettings(); true }
                    state.pane != Pane.Empty -> { state.pane = Pane.Empty; true }
                    else -> false
                } else false
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(980, 640) }
            // ADIF and CSV: first the dialog (only new or all, the export mark), then the save dialog.
            val exportCsv = { state.openExport(ExportFormat.CSV, selectedOnly = false) }
            val exportAdif = { state.openExport(ExportFormat.ADIF, selectedOnly = false) }
            val saveExport = { onlyNew: Boolean, mark: Boolean ->
                state.prepareExport(onlyNew, mark)?.let { (format, name) ->
                    state.exportFile(
                        if (format == ExportFormat.CSV) chooseFile(window, tr("Экспорт в %s", format.title), save = true, suggested = name)
                        else chooseFile(window, tr("Экспорт в %s", format.title), save = true, suggested = name, ext = "adi"),
                    )
                }
                Unit
            }
            val importAdif = {
                chooseFile(window, tr("Импорт лога из ADIF"), save = false, ext = "adi")?.let(state::importAdif)
                Unit
            }
            val importCsv = {
                chooseFile(window, tr("Импорт лога из CSV"), save = false)?.let(state::importCsv)
                Unit
            }
            val exportSelected = { state.openExport(ExportFormat.ADIF, selectedOnly = true) }
            // Contest reports: the dialog picks the header, then the save dialog; .txt (ЕРМАК) or .cbr (Cabrillo).
            val exportContest = { h: ru.r3xed.qsolog.data.Cabrillo.Header, onlyNew: Boolean, mark: Boolean ->
                val file = chooseFile(window, tr("Экспорт в %s", h.format.title), save = true, suggested = state.prepareContest(h, onlyNew, mark), ext = h.format.extension)
                if (file != null) state.exportContest(file) else state.cancelContest()
            }
            val importContest = {
                chooseFile(window, tr("Импорт из ЕРМАК / Cabrillo"), save = false, ext = "cbr")?.let(state::importContest)
                Unit
            }
            // Texts are read through tr() while composing: a new language rebuilds the menu and the window.
            key(state.language) { MenuBar {
                Menu(tr("Файл")) {
                    Item(tr("Новый QSO"), shortcut = shortcut(Key.N), onClick = { state.addQso() })
                    Item(tr("Сохранить связь"), enabled = state.pane == Pane.Edit, shortcut = shortcut(Key.S), onClick = state::trySave)
                    Item(
                        tr("Сохранить и следующая"), enabled = state.pane == Pane.Edit && state.form.isNew,
                        shortcut = KeyShortcut(Key.S, meta = IS_MAC, ctrl = !IS_MAC, shift = true), onClick = { state.saveAndNext() },
                    )
                    Separator()
                    Item(tr("Экспорт лога в ADIF…"), onClick = exportAdif)
                    Item(tr("Импорт лога из ADIF…"), onClick = importAdif)
                    Separator()
                    Item(tr("Экспорт лога в CSV…"), onClick = exportCsv)
                    Item(tr("Импорт лога из CSV…"), onClick = importCsv)
                    Separator()
                    Item(tr("Экспорт в ЕРМАК / Cabrillo…"), onClick = { state.openContestExport(selectedOnly = false) })
                    Item(tr("Импорт из ЕРМАК / Cabrillo…"), onClick = importContest)
                    Separator()
                    Item(tr("Карта QSO"), shortcut = shortcut(Key.M), onClick = state::openMap)
                    Item(tr("Справка и калькуляторы"), onClick = state::openReference)
                    Item(tr("Настройки"), shortcut = shortcut(Key.Comma), onClick = { state.openSettings() })
                    if (!IS_MAC) {
                        Separator()
                        Item(tr("Выход"), onClick = { state.flushSettings(); exitApplication() })
                    }
                }
            } }
            // Theme from the settings; "system" follows the OS (macOS, Windows).
            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            QsoTheme(dark) { key(state.language) { App(state, Exports(exportCsv, importCsv, exportAdif, importAdif, exportSelected, exportContest, importContest, saveExport)) } }
        }
    }
}

/** File dialogs, opened from the menu, the settings pane and the selection bar. */
class Exports(
    val exportCsv: () -> Unit,
    val importCsv: () -> Unit,
    val exportAdif: () -> Unit,
    val importAdif: () -> Unit,
    val exportSelected: () -> Unit,
    val exportContest: (ru.r3xed.qsolog.data.Cabrillo.Header, Boolean, Boolean) -> Unit,
    val importContest: () -> Unit,
    /** Saves the ADIF / CSV export chosen in its dialog: only new or all, with or without the export mark. */
    val saveExport: (onlyNew: Boolean, mark: Boolean) -> Unit,
)

@Composable
fun App(state: AppState, files: Exports) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        state.messages.collect { m ->
            val r = snackbar.showSnackbar(
                m.text,
                actionLabel = if (m.undo != null) tr("Отменить") else null,
                duration = if (m.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) m.undo?.invoke()
        }
    }
    // Surface sets the default text colour from the theme, so text is light grey in the dark theme.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                LogPane(state, NEW_SHORTCUT, files.exportSelected, Modifier.width(460.dp).fillMaxHeight())
                VerticalDivider(color = LocalExtra.current.line)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (state.pane) {
                        Pane.Empty -> EmptyPane(state)
                        // A fresh card (also "＋ Следующая") starts with fresh fields, focus and scroll.
                        Pane.Edit -> key(state.editSession) { EditPane(state) }
                        Pane.Settings -> SettingsPane(state, files.exportCsv, files.importCsv, files.exportAdif, files.importAdif, files.importContest)
                        Pane.Map -> MapPane(state)
                        Pane.Welcome -> WelcomePane(state)
                        Pane.Reference -> ReferencePane(onClose = state::closeReference, myPosition = state.settings.myPosition)
                    }
                }
            }
            state.contestTarget?.let { target ->
                ContestExportDialog(
                    defaults = state.contestDefaults(),
                    selected = target.only?.size,
                    count = { onlyNew -> state.exportCandidates(ExportFormat.CONTEST, target.only, onlyNew).size },
                    onDismiss = state::closeContestExport,
                    onConfirm = files.exportContest,
                )
            }
            state.exportTarget?.let { target ->
                ExportDialog(
                    format = target.format,
                    selected = target.only?.size,
                    count = { onlyNew -> state.exportCandidates(target.format, target.only, onlyNew).size },
                    onDismiss = state::closeExport,
                    onConfirm = files.saveExport,
                    utf8 = state.adifUtf8,
                    onUtf8 = state::chooseAdifUtf8,
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).widthIn(max = 640.dp))
        }
    }
}

@Composable
private fun EmptyPane(state: AppState) {
    val x = LocalExtra.current
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(tr("Готов к записи"), style = MaterialTheme.typography.headlineMedium)
        Text(
            tr("Нажмите «Добавить QSO» или %s. Удерживайте кнопку, чтобы записать голосовую заметку. Чтобы исправить запись, выберите её в логе слева.", NEW_SHORTCUT),
            style = MaterialTheme.typography.bodyLarge, color = x.muted, modifier = Modifier.widthIn(max = 560.dp),
        )
        val s = state.settings
        if (s.myLocator.isBlank()) {
            Text(
                tr("Укажите свой QTH-локатор в настройках, чтобы видеть расстояние до абонентов."),
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
        dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".$ext") || it.endsWith(".txt") || (ext == "adi" && it.endsWith(".adif")) || (ext == "cbr" && it.endsWith(".log")) } }
        if (!IS_MAC) dialog.file = "*.$ext" // Windows ignores the filter above and uses this pattern instead
    }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return if (save && !name.lowercase().endsWith(".$ext")) File(dir, "$name.$ext") else File(dir, name)
}

const val PROJECT_URL = "https://github.com/vladkondratyev/qso-log"
val USER_AGENT = "QSO-LOG/$APP_VERSION (+$PROJECT_URL)"
