package ru.r3xed.qsolog

import ru.r3xed.qsolog.keys.Chord
import ru.r3xed.qsolog.keys.KeyAction
import ru.r3xed.qsolog.keys.KeyMap
import ru.r3xed.qsolog.ui.chord
import ru.r3xed.qsolog.ui.menuShortcut
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
import ru.r3xed.qsolog.ui.ContestsPane
import ru.r3xed.qsolog.ui.ExportDialog
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.ui.EditPane
import ru.r3xed.qsolog.ui.ContestPane
import ru.r3xed.qsolog.ui.DashboardPane
import ru.r3xed.qsolog.ui.LocalExtra
import ru.r3xed.qsolog.ui.LogPane
import ru.r3xed.qsolog.ui.TransferDialog
import ru.r3xed.qsolog.ui.MapPane
import ru.r3xed.qsolog.ui.QsoTheme
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import ru.r3xed.qsolog.ui.HistoryOnDialog
import ru.r3xed.qsolog.ui.HistoryPane
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

/** Actions of the menu bar: their first key is the menu item's shortcut (see [menuShortcut]). */
private val MENU_ACTIONS = setOf(KeyAction.NEW_QSO, KeyAction.DASHBOARD, KeyAction.MAP, KeyAction.HISTORY, KeyAction.SETTINGS)

private val DASH_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four)

/** Keys held down now, to tell the computer's auto-repeat from a new press of the Morse key. */
private val morseHeld = mutableSetOf<String>()

/** The log with no card open: the arrows walk through the rows, Enter opens, Space picks, Delete deletes. */
private fun logKey(state: AppState, e: androidx.compose.ui.input.key.KeyEvent, mod: Boolean, c: Chord?): Boolean {
    when {
        mod && e.key == Key.A -> state.selectAllShown()
        // One dialog for the cursor's row or the picked records; Enter deletes.
        KeyMap.hit(KeyAction.LOG_DELETE, c) -> return state.askDeleteLog()
        mod -> return false
        e.key == Key.DirectionDown -> state.moveLogCursor(1)
        e.key == Key.DirectionUp -> state.moveLogCursor(-1)
        e.key == Key.PageDown -> state.moveLogCursor(10)
        e.key == Key.PageUp -> state.moveLogCursor(-10)
        e.key == Key.MoveHome -> state.moveLogCursor(0, to = -1)
        e.key == Key.MoveEnd -> state.moveLogCursor(0, to = 1)
        (e.key == Key.Enter || e.key == Key.NumPadEnter) && state.logCursor != null -> state.openLogCursor()
        e.key == Key.Spacebar && state.logCursor != null -> state.toggleLogCursor()
        else -> return false
    }
    return true
}

/** The search history: arrows, Enter, Space, and one letter per action on the row, the picked ones or the open entry. */
private fun historyKey(state: AppState, e: androidx.compose.ui.input.key.KeyEvent, mod: Boolean, c: Chord?): Boolean {
    val open = state.historyOpen != null
    when {
        mod && e.key == Key.A && !open -> state.selectAllHistory()
        KeyMap.hit(KeyAction.H_FAVORITE, c) -> state.historyTargets().ifEmpty { null }?.let(state::toggleFavorite)
        KeyMap.hit(KeyAction.H_EDIT, c) -> state.historyTargets().ifEmpty { null }?.let { state.historyEditCalls = it }
        KeyMap.hit(KeyAction.H_DELETE, c) -> state.historyTargets().ifEmpty { null }?.let { state.historyDeleteAsk = it }
        KeyMap.hit(KeyAction.H_NEW, c) -> state.newQsoFromHistoryTarget()
        KeyMap.hit(KeyAction.H_MAP, c) && !open -> state.historyMap = !state.historyMap
        mod || e.isAltPressed -> return false
        e.key == Key.DirectionDown && !open -> state.moveHistoryCursor(1)
        e.key == Key.DirectionUp && !open -> state.moveHistoryCursor(-1)
        (e.key == Key.Enter || e.key == Key.NumPadEnter) && !open -> state.openHistoryCursor()
        e.key == Key.Spacebar && !open -> state.historyCursor?.let(state::toggleHistorySelected)
        else -> return false
    }
    return true
}

fun main() {
    if (IS_MAC) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.name", "QSO-LOG")
    }
    application {
        val state = remember { AppState() }
        val windowState = rememberWindowState(width = 1024.dp, height = 700.dp, position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = { state.flushSettings(); state.stopServer(); state.stopCat(); state.stopDigi(); state.closeCw(); state.stopStartApps(); exitApplication() },
            title = "QSO-LOG",
            icon = painterResource("icon.png"),
            state = windowState,
            onPreviewKeyEvent = { e ->
                // The Morse trainer is open: an external key through an adapter sends keys (Ctrl, brackets, Space by default).
                if (e.type == KeyEventType.KeyDown || e.type == KeyEventType.KeyUp) {
                    val id = "${e.key.keyCode}"
                    val down = e.type == KeyEventType.KeyDown
                    // The computer repeats a held key: only the first press counts.
                    val repeat = down && id in morseHeld
                    if (down) morseHeld.add(id) else morseHeld.remove(id)
                    if (ru.r3xed.qsolog.morse.MorseKeyBus.key(id, down, repeat)) return@Window true
                    // The card in CW with the hand keyer on: the paddle keys key the radio.
                    if (state.cwHandKey(id, down, repeat)) return@Window true
                }
                // The keys are the user's (Настройки → Дополнительные настройки → Клавиши); the defaults are those of KeyAction.
                val mod = if (IS_MAC) e.isMetaPressed else e.isCtrlPressed
                val c = if (e.type == KeyEventType.KeyDown) e.chord() else null
                val inCard = state.pane == Pane.Edit || state.pane == Pane.Contest
                // The menu bar has the first key of these actions as its shortcut and does them itself.
                fun hit(a: KeyAction) = KeyMap.hit(a, c) && (a !in MENU_ACTIONS || c != KeyMap.chords(a).firstOrNull())
                // The card in CW with the key line on: F1–F8 send the messages, on top of the card's own keys.
                if (inCard && state.cwKey(c)) true
                else if (hit(KeyAction.NEW_QSO)) { state.addQso(); true }
                else if (hit(KeyAction.EXPORT_ADIF)) { state.openExport(ExportFormat.ADIF, selectedOnly = false); true }
                else if (hit(KeyAction.EDIT_LAST)) { state.editLast(); true }
                else if (hit(KeyAction.CONTEST_MODE) && !inCard) { state.toggleContestMode(); true }
                else if (hit(KeyAction.SYNC)) { state.syncByKey(); true }
                else if (hit(KeyAction.RIG_PANEL)) { state.showRigPanel = !state.showRigPanel; true }
                else if (hit(KeyAction.DASHBOARD)) { state.dashboardOrDelete(); true }
                else if (hit(KeyAction.MAP)) { state.openMap(); true }
                else if (hit(KeyAction.HISTORY)) { state.openHistory(); true }
                else if (hit(KeyAction.SETTINGS)) { state.openSettings(); true }
                else if (e.type == KeyEventType.KeyDown && state.pane == Pane.Empty && !state.searchFocused && !state.showKeys && state.logDeleteAsk == null && !state.showTransfer && logKey(state, e, mod, c)) true
                // Alt+W or Ctrl+Esc (⌘Esc) wipes a new card, as in classic loggers: a clean card, the cursor in the callsign.
                else if (inCard && hit(KeyAction.CLEAR_CARD)) { if (state.form.isNew) state.clearCard(); true }
                else if (e.type == KeyEventType.KeyDown && state.pane == Pane.History && !state.historyTyping && state.historyEditCalls == null && state.historyDeleteAsk == null && historyKey(state, e, mod, c)) true
                else if (e.type == KeyEventType.KeyDown && state.pane == Pane.Dashboard && !mod && DASH_KEYS.indexOf(e.key) >= 0) {
                    state.changeDashFilter(state.dashFilter.copy(period = ru.r3xed.qsolog.data.StatPeriod.entries[DASH_KEYS.indexOf(e.key)]))
                    true
                }
                else if (hit(KeyAction.SEARCH)) { state.requestSearchFocus(); true }
                else if (hit(KeyAction.KEYS_HELP)) { state.showKeys = !state.showKeys; true }
                // Esc closes the card; with changes it asks first (Enter in the dialog closes).
                else if (inCard && !state.showKeys && !state.showTransfer && hit(KeyAction.CLOSE_CARD)) {
                    if (state.confirmClose) state.confirmClose = false
                    else if (state.pane == Pane.Edit) state.requestClose() else state.requestCloseContest()
                    true
                }
                else if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && !mod) when {
                    state.showKeys -> { state.showKeys = false; true }
                    state.selecting -> { state.clearSelection(); true }
                    state.logDeleteAsk != null -> { state.logDeleteAsk = null; true }
                    state.showTransfer -> { state.showTransfer = false; true }
                    state.pane == Pane.Edit || state.pane == Pane.Contest -> false
                    state.pane == Pane.Settings -> { state.closeSettings(); true }
                    state.pane == Pane.History -> { state.closeHistory(); true }
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
            val exportContest = { h: ru.r3xed.qsolog.data.Cabrillo.Header, onlyNew: Boolean, mark: Boolean, contestOnly: Boolean, ref: String? ->
                val file = chooseFile(window, tr("Экспорт в %s", h.format.title), save = true, suggested = state.prepareContest(h, onlyNew, mark, contestOnly, ref), ext = h.format.extension)
                if (file != null) state.exportContest(file) else state.cancelContest()
            }
            val importContest = {
                chooseFile(window, tr("Импорт из ЕРМАК / Cabrillo"), save = false, ext = "cbr")?.let(state::importContest)
                Unit
            }
            // Texts are read through tr() while composing: a new language rebuilds the menu and the window.
            key(state.language) { MenuBar {
                Menu(tr("Файл")) {
                    Item(tr("Новый QSO"), shortcut = menuShortcut(KeyAction.NEW_QSO), onClick = { state.addQso() })
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
                    Item(tr("Выбрать часть журнала…"), onClick = { state.showSelect = true })
                    Separator()
                    Item(tr("Дашборд"), shortcut = menuShortcut(KeyAction.DASHBOARD), onClick = state::dashboardOrDelete)
                    Item(tr("Карта QSO"), shortcut = menuShortcut(KeyAction.MAP), onClick = state::openMap)
                    Item(tr("История поиска"), shortcut = menuShortcut(KeyAction.HISTORY), onClick = state::openHistory)
                    Item(tr("Справка и калькуляторы"), onClick = state::openReference)
                    Item(tr("Горячие клавиши") + KeyMap.label(KeyAction.KEYS_HELP, IS_MAC).ifEmpty { null }?.let { " ($it)" }.orEmpty(), onClick = { state.showKeys = true })
                    if (state.catConfig.enabled || state.rig != null) Item(tr("Пульт трансивера"), onClick = { state.showRigPanel = true })
                    Item(tr("Настройки"), shortcut = menuShortcut(KeyAction.SETTINGS), onClick = { state.openSettings() })
                    Item(tr("Оборудование и программы…"), onClick = { state.openSettings(page = ru.r3xed.qsolog.SettingsPage.HARDWARE) })
                    // Shown once the table is set up in the settings; the sync runs only when asked.
                    if (ru.r3xed.qsolog.data.SheetSync.isScriptUrl(state.sheetUrl)) {
                        Item(tr("Синхронизировать"), enabled = !state.sheetSyncing, onClick = state::syncSheet)
                    }
                    if (!IS_MAC) {
                        Separator()
                        Item(tr("Выход"), onClick = { state.flushSettings(); state.stopServer(); state.closeCw(); state.stopStartApps(); exitApplication() })
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
    val exportContest: (ru.r3xed.qsolog.data.Cabrillo.Header, Boolean, Boolean, Boolean, String?) -> Unit,
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
                        Pane.History -> HistoryPane(state)
                        Pane.Contests -> ContestsPane(state)
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
                    contests = state.contestsInLog(target.only),
                    initialContest = state.contestReportDefault(target.only),
                    count = { onlyNew, contestOnly, ref -> state.exportCandidates(ExportFormat.CONTEST, target.only, onlyNew, contestOnly, ref).size },
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
            state.eqslInbox?.let { inbox ->
                ru.r3xed.qsolog.ui.EqslInboxDialog(
                    inbox,
                    onOpenSettings = { state.closeEqslInbox(); state.openSettings(page = ru.r3xed.qsolog.SettingsPage.SOURCES) },
                    onAdd = state::addEqslCards,
                    onDismiss = state::closeEqslInbox,
                )
            }
            state.uploadTarget?.let { target ->
                ru.r3xed.qsolog.ui.UploadDialog(
                    selected = target.only?.size,
                    total = state.total,
                    count = { log, onlyNew -> state.uploadCandidates(log, target.only, onlyNew).size },
                    problem = state::uploadProblem,
                    busy = state.uploading,
                    lotwNote = if (state.lotwCert != null) tr("Связи подписываются вашим сертификатом LoTW (%s) и сразу уходят в LoTW.", state.lotwCert!!.callsign)
                    else tr("Связи подписывает и отправляет программа TQSL от ARRL с вашим сертификатом: QSO-LOG запускает её сама, с Station Location из настроек. Или загрузите в настройках файл сертификата .p12 — тогда TQSL не нужен."),
                    lotwAction = tr("Отправить"),
                    onOpenSettings = { state.closeUpload(); state.openSettings() },
                    onDismiss = state::closeUpload,
                    onConfirm = state::upload,
                )
            }
            if (state.showKeys) KeysDialog(state.cwConfig.messages.takeIf { state.cwConfig.enabled }, onClose = { state.showKeys = false })
            if (state.showRigPanel) ru.r3xed.qsolog.ui.RigPanel(state)
            if (state.showTransfer) TransferDialog(
                total = state.total,
                onExport = { format -> state.showTransfer = false; state.openExport(format, selectedOnly = false) },
                onUpload = { state.showTransfer = false; state.openUpload(selectedOnly = false) },
                onEqslInbox = { state.showTransfer = false; state.openEqslInbox() },
                onLotwSync = { state.showTransfer = false; state.syncLotw() },
                onSelect = { state.showTransfer = false; state.showSelect = true },
                onImportAdif = { state.showTransfer = false; files.importAdif() },
                onImportCsv = { state.showTransfer = false; files.importCsv() },
                onImportContest = { state.showTransfer = false; files.importContest() },
                onDismiss = { state.showTransfer = false },
            )
            if (state.showSelect) ru.r3xed.qsolog.ui.SelectDialog(state.allQsos, onPick = state::selectIds, onDismiss = { state.showSelect = false })
            if (!state.showWhatsNew) ru.r3xed.qsolog.ui.UpdateDialog(state)
            state.adifImport?.let { (name, bytes) -> ru.r3xed.qsolog.ui.AdifImportDialog(name, bytes, onImport = { state.importAdif(bytes, it) }, onDismiss = { state.adifImport = null }) }
            // Not over the first-start setup: it waits until the log.
            if (state.showWhatsNew && state.pane != Pane.Welcome) ru.r3xed.qsolog.ui.WhatsNewDialog(APP_VERSION, desktop = true, onClose = state::closeWhatsNew)
            if (state.historyAsk) HistoryOnDialog(onConfirm = { state.changeHistoryOn(true) }, onDismiss = { state.historyAsk = false })
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
private fun KeysDialog(cwMessages: List<String>?, onClose: () -> Unit) {
    // The sections in two columns of a wide dialog; it scrolls in a small window.
    val sections = KeyHints.sections(IS_MAC, cwMessages)
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.widthIn(max = 1180.dp).fillMaxWidth(0.94f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(tr("Горячие клавиши")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Balanced by lines: "everywhere" and the card on the left, the log, contest, history and dashboard on the right.
                val left = listOf(sections[0], sections[2])
                val columns = listOf(left, sections - left.toSet())
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    columns.forEach { col ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            col.forEach { sec ->
                                Text(sec.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                                sec.keys.forEach { (k, what) ->
                                    Row {
                                        Text(k, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(200.dp))
                                        Text(what, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(KeyHints.commands, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
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
