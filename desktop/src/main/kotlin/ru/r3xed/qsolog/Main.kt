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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import ru.r3xed.qsolog.ui.Mono
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
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
import ru.r3xed.qsolog.ui.ContestPane
import ru.r3xed.qsolog.ui.DashboardPane
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
        val windowState = rememberWindowState(width = 1024.dp, height = 700.dp, position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = { state.flushSettings(); exitApplication() },
            title = "QSO-LOG",
            icon = painterResource("icon.png"),
            state = windowState,
            onPreviewKeyEvent = { e ->
                // One key instead of two: F9 opens a new card from anywhere, F1 lists the keys.
                val mod = if (IS_MAC) e.isMetaPressed else e.isCtrlPressed
                if (e.type == KeyEventType.KeyDown && e.key == Key.F9) { state.addQso(); true }
                // Ctrl+E (⌘E): the contact just logged opens to be fixed; Ctrl+F (⌘F): the search.
                else if (e.type == KeyEventType.KeyDown && mod && e.key == Key.E) { state.editLast(); true }
                else if (e.type == KeyEventType.KeyDown && mod && e.key == Key.F) { state.requestSearchFocus(); true }
                else if (e.type == KeyEventType.KeyDown && e.key == Key.F1) { state.showKeys = !state.showKeys; true }
                else if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) when {
                    state.showKeys -> { state.showKeys = false; true }
                    state.selecting -> { state.clearSelection(); true }
                    // A new card with something typed is wiped first (the band, mode and frequency stay), a second Esc closes it.
                    state.pane == Pane.Edit -> {
                        if (state.confirmClose) state.confirmClose = false
                        else if (state.form.isNew && state.form.call.isNotBlank()) state.clearCard()
                        else state.requestClose()
                        true
                    }
                    state.pane == Pane.Contest -> {
                        if (state.confirmClose) state.confirmClose = false
                        else if (state.form.isNew && (state.form.call.isNotBlank() || !state.form.adif[ru.r3xed.qsolog.data.ContestMode.RCVD].isNullOrBlank())) state.clearCard()
                        else state.requestCloseContest()
                        true
                    }
                    state.pane == Pane.Settings -> { state.closeSettings(); true }
                    state.pane != Pane.Empty -> { state.pane = Pane.Empty; true }
                    else -> false
                } else false
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(720, 480) }
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
            val exportContest = { h: ru.r3xed.qsolog.data.Cabrillo.Header, onlyNew: Boolean, mark: Boolean, contestOnly: Boolean ->
                val file = chooseFile(window, tr("Экспорт в %s", h.format.title), save = true, suggested = state.prepareContest(h, onlyNew, mark, contestOnly), ext = h.format.extension)
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
                    Item(tr("Дашборд"), shortcut = shortcut(Key.D), onClick = state::openDashboard)
                    Item(tr("Карта QSO"), shortcut = shortcut(Key.M), onClick = state::openMap)
                    Item(tr("Справка и калькуляторы"), onClick = state::openReference)
                    Item(tr("Горячие клавиши") + " (F1)", onClick = { state.showKeys = true })
                    Item(tr("Настройки"), shortcut = shortcut(Key.Comma), onClick = { state.openSettings() })
                    // Shown once the table is set up in the settings; the sync runs only when asked.
                    if (ru.r3xed.qsolog.data.SheetSync.isScriptUrl(state.sheetUrl)) {
                        Item(tr("Синхронизировать"), enabled = !state.sheetSyncing, onClick = state::syncSheet)
                    }
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
    val exportContest: (ru.r3xed.qsolog.data.Cabrillo.Header, Boolean, Boolean, Boolean) -> Unit,
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
                        Pane.Contest -> key(state.editSession) { ContestPane(state) }
                        Pane.Dashboard -> DashboardPane(state)
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
                    hasContest = state.hasContestQsos(target.only),
                    count = { onlyNew, contestOnly -> state.exportCandidates(ExportFormat.CONTEST, target.only, onlyNew, contestOnly).size },
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
            if (state.showKeys) KeysDialog(onClose = { state.showKeys = false })
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

/** F1: every key of the card in one table. */
@Composable
private fun KeysDialog(onClose: () -> Unit) {
    val mod = if (IS_MAC) "⌘" else "Ctrl+"
    val keys = listOf(
        "F1" to tr("эта подсказка"),
        "F2" to tr("позывной"),
        "F3" to tr("частота"),
        "F4" to tr("RST отправлен"),
        "F5" to tr("RST принят"),
        "F6" to tr("текущее время UTC"),
        "F8 · $NEXT_SHORTCUT" to tr("сохранить и открыть следующую"),
        "F9 · $NEW_SHORTCUT" to tr("новая связь"),
        "F12 · $SAVE_SHORTCUT" to tr("сохранить"),
        "Enter" to tr("записать связь из любого поля (новая карточка — записать и открыть следующую)"),
        tr("Пробел") to tr("из позывного — к частоте или RST (в контесте — к принятому коду)"),
        "Tab · Shift+Tab" to tr("следующее и предыдущее поле"),
        "↑ · ${mod}E" to tr("исправить последнюю записанную связь (↑ — в пустом позывном)"),
        "${mod}F" to tr("поиск по журналу; Enter в поиске — новая связь с найденным"),
        "Alt+1…9" to tr("выбрать диапазон"),
        "Alt+Shift+1…9" to tr("выбрать вид связи"),
        "Esc" to tr("очистить новую карточку, второй раз — закрыть; отменить выбор"),
        "PgUp · PgDn" to tr("контест: предыдущая и следующая связь"),
        "${mod}D · ${mod}M · $mod," to tr("дашборд, карта QSO, настройки"),
        "Shift+Enter" to tr("новая строка в комментарии"),
    )
    // Two columns in a wide dialog: the whole list fits the default window; it scrolls in a smaller one.
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth(0.92f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(tr("Горячие клавиши")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val half = (keys.size + 1) / 2
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    listOf(keys.take(half), keys.drop(half)).forEach { part ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            part.forEach { (k, what) ->
                                Row {
                                    Text(k, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(190.dp))
                                    Text(what, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
                Text(
                    tr("Команды в поле позывного (вместо позывного, затем Enter или Пробел): 20m или 20 — диапазон, CW, SSB, FT8 — вид связи, 14195 или 7.074 — частота. Поле очищается, карточка остаётся."),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp),
                )
                if (IS_MAC) Text(
                    tr("На Mac клавиши F нажимаются вместе с fn, если в настройках клавиатуры не включено «Использовать F1, F2 и т. д. как стандартные функциональные клавиши»."),
                    style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Закрыть")) } },
    )
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
