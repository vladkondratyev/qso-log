package ru.r3xed.qsolog

import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import ru.r3xed.qsolog.keys.Chord
import ru.r3xed.qsolog.keys.KeyAction
import ru.r3xed.qsolog.keys.KeyMap
import ru.r3xed.qsolog.ui.chord
import ru.r3xed.qsolog.ui.PaletteCommand
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
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
private val MENU_ACTIONS = setOf(
    KeyAction.NEW_QSO, KeyAction.DASHBOARD, KeyAction.MAP, KeyAction.HISTORY, KeyAction.SETTINGS,
    KeyAction.DX_CLUSTER, KeyAction.PROPAGATION, KeyAction.AWARDS, KeyAction.REFERENCE, KeyAction.ONLINE_LOGS, KeyAction.LOTW_SYNC,
    KeyAction.IMPORT_ADIF, KeyAction.SELECT_PART, KeyAction.BACKUP, KeyAction.LOG_VIEW, KeyAction.NEXT_STATION,
)

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

/** The key of the cluster's list as [AppState.dxKey] names it: "Up", "Down", "Enter", "Shift+Enter", "S", "0"–"9". */
private fun dxKeyName(e: androidx.compose.ui.input.key.KeyEvent): String? = when (e.key) {
    Key.DirectionUp -> "Up"
    Key.DirectionDown -> "Down"
    Key.Enter, Key.NumPadEnter -> if (e.isShiftPressed) "Shift+Enter" else "Enter"
    Key.S -> "S"
    else -> (e.key.keyCode - Key.Zero.keyCode).takeIf { it in 0L..9L }?.toString()
        ?: (e.key.keyCode - Key.NumPad0.keyCode).takeIf { it in 0L..9L }?.toString()
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main() {
    if (IS_MAC) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.name", "QSO-LOG")
    }
    application {
        val state = remember { AppState() }
        val windowState = rememberWindowState(width = 1024.dp, height = 700.dp, position = WindowPosition(Alignment.Center))
        // Errors inside the window: the lost focus of Compose 1.6 is reset quietly, the rest is written to errors.log.
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.window.LocalWindowExceptionHandlerFactory provides WindowErrors) {
        Window(
            onCloseRequest = { state.flushSettings(); state.stopServer(); state.stopCat(); state.stopDigi(); state.stopDxCluster(); state.closeCw(); state.stopStartApps(); exitApplication() },
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
                // ⌘+ typed with Shift, and the keypad's + and −: the menu has ⌘= and ⌘− only.
                if (e.type == KeyEventType.KeyDown && mod && !e.isAltPressed && e.key == Key.K && !IS_MAC) { state.showPalette = !state.showPalette; return@Window true }
                if (e.type == KeyEventType.KeyDown && mod && !e.isAltPressed) when {
                    e.key == Key.Plus || e.key == Key.NumPadAdd || (e.key == Key.Equals && e.isShiftPressed) -> { state.zoomIn(); return@Window true }
                    e.key == Key.NumPadSubtract || (e.key == Key.Minus && e.isShiftPressed) -> { state.zoomOut(); return@Window true }
                    e.key == Key.NumPad0 -> { state.zoomReset(); return@Window true }
                }
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
                // The sections and actions of the menus (2.9.6); the menu bar does the first key of each itself.
                else if (hit(KeyAction.DX_CLUSTER)) { state.outsideCard(state::openDxCluster); true }
                else if (hit(KeyAction.PROPAGATION)) { state.outsideCard(state::openPropagation); true }
                else if (hit(KeyAction.AWARDS)) { state.outsideCard(state::openAwards); true }
                else if (hit(KeyAction.REFERENCE)) { state.outsideCard(state::openReference); true }
                else if (hit(KeyAction.ONLINE_LOGS)) { state.openUpload(selectedOnly = false); true }
                else if (hit(KeyAction.LOTW_SYNC)) { state.syncLotw(); true }
                else if (hit(KeyAction.IMPORT_ADIF)) { state.outsideCard { state.importAdifTick++ }; true }
                else if (hit(KeyAction.SELECT_PART)) { state.outsideCard { state.showSelect = true }; true }
                else if (hit(KeyAction.BACKUP)) { state.backupNow(); true }
                else if (hit(KeyAction.LOG_VIEW)) { state.toggleTableMode(); true }
                else if (hit(KeyAction.NEXT_STATION)) { state.outsideCard(state::nextStation); true }
                // DX-кластер: arrows through the spots, Enter works one, S — one's own spot, digits — the band.
                else if (e.type == KeyEventType.KeyDown && state.pane == Pane.DxCluster && !mod && !e.isAltPressed && state.dxKey(dxKeyName(e))) true
                else if (e.type == KeyEventType.KeyDown && state.pane == Pane.Propagation && !mod && e.key == Key.F5) { state.refreshPropagation(force = true); true }
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
                    // On «Готов к записи» Esc switches the log between the usual rows and the compact two-line ones;
                    // with a search typed it is the field's and clears the search.
                    state.query.isEmpty() -> { state.toggleCompactLog(); true }
                    else -> false
                } else false
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(720, 480) }
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            androidx.compose.runtime.SideEffect { WindowErrors.resetFocus = { focusManager.clearFocus(force = true) } }
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
            LaunchedEffect(state.importAdifTick) { if (state.importAdifTick > 0) importAdif() }
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
                // As in other programs of the system: Файл · Правка · Вид · Трансивер · Сервис · Справка.
                Menu(tr("Файл")) {
                    Item(tr("Новый QSO"), shortcut = menuShortcut(KeyAction.NEW_QSO), onClick = { state.addQso() })
                    Item(tr("Сохранить связь"), enabled = state.pane == Pane.Edit, shortcut = shortcut(Key.S), onClick = state::trySave)
                    Item(
                        tr("Сохранить и следующая"), enabled = state.pane == Pane.Edit && state.form.isNew,
                        shortcut = KeyShortcut(Key.S, meta = IS_MAC, ctrl = !IS_MAC, shift = true), onClick = { state.saveAndNext() },
                    )
                    Separator()
                    Item(tr("Экспорт лога в ADIF…"), shortcut = menuShortcut(KeyAction.EXPORT_ADIF), onClick = exportAdif)
                    Item(tr("Импорт лога из ADIF…"), shortcut = menuShortcut(KeyAction.IMPORT_ADIF), onClick = { state.outsideCard(importAdif) })
                    Separator()
                    Item(tr("Экспорт лога в CSV…"), onClick = exportCsv)
                    Item(tr("Импорт лога из CSV…"), onClick = importCsv)
                    Separator()
                    Item(tr("Экспорт в ЕРМАК / Cabrillo…"), onClick = { state.openContestExport(selectedOnly = false) })
                    Item(tr("Импорт из ЕРМАК / Cabrillo…"), onClick = importContest)
                    Separator()
                    Item(tr("Сделать копию журнала"), shortcut = menuShortcut(KeyAction.BACKUP), onClick = { state.backupNow() })
                    Item(tr("Открыть папку копий"), onClick = state::openBackupFolder)
                    if (!IS_MAC) {
                        Separator()
                        Item(tr("Выход"), onClick = { state.flushSettings(); state.stopServer(); state.closeCw(); state.stopStartApps(); exitApplication() })
                    }
                }
                Menu(tr("Правка")) {
                    Item(tr("Найти в журнале"), onClick = state::requestSearchFocus)
                    Item(tr("Команды…"), shortcut = shortcut(Key.K), onClick = { state.showPalette = true })
                    Separator()
                    Item(tr("Выбрать часть журнала…"), shortcut = menuShortcut(KeyAction.SELECT_PART), onClick = { state.outsideCard { state.showSelect = true } })
                    Item(tr("Снять выбор"), enabled = state.selecting, onClick = state::clearSelection)
                    Item(tr("Печать QSL-наклеек"), enabled = state.selecting, onClick = state::printQslLabels)
                    Item(tr("Отметить: QSL отправлены"), enabled = state.selecting, onClick = state::markQslSent)
                    Item(tr("Удалить выбранные…"), enabled = state.selecting, onClick = { state.askDeleteLog() })
                    Separator()
                    Item(if (state.selecting) tr("Дозаполнить данные корреспондентов (выбранные)") else tr("Дозаполнить данные корреспондентов"), enabled = state.fillProgress == null, onClick = state::fillMissing)
                    Separator()
                    Item(tr("Сбросить фильтр журнала"), enabled = state.logFilter.active, onClick = { state.changeLogFilter(ru.r3xed.qsolog.data.LogFilter()) })
                }
                // "Масштаб интерфейса", as in a browser: ⌘+ ⌘− ⌘0 on the Mac, Ctrl elsewhere.
                Menu(tr("Вид")) {
                    Item(tr("Крупнее"), enabled = state.uiScale != ru.r3xed.qsolog.data.UiScale.STEPS.first(), shortcut = shortcut(Key.Equals), onClick = state::zoomIn)
                    Item(tr("Мельче"), enabled = state.uiScale != ru.r3xed.qsolog.data.UiScale.STEPS.last(), shortcut = shortcut(Key.Minus), onClick = state::zoomOut)
                    Item(tr("Обычный размер (100%)"), shortcut = shortcut(Key.Zero), onClick = state::zoomReset)
                    Menu(tr("Масштаб: %s", state.uiScale.label)) {
                        ru.r3xed.qsolog.data.UiScale.STEPS.forEach { sc ->
                            CheckboxItem(sc.label, checked = state.uiScale == sc, onCheckedChange = { state.changeUiScale(sc) })
                        }
                    }
                    Separator()
                    CheckboxItem(tr("Компактный журнал"), checked = state.compactLog, onCheckedChange = { state.toggleCompactLog() })
                    CheckboxItem(tr("Журнал таблицей"), checked = state.tableMode, shortcut = menuShortcut(KeyAction.LOG_VIEW), onCheckedChange = { state.toggleTableMode() })
                    Menu(tr("Колонки компактного журнала")) {
                        listOf("freq" to tr("Частота"), "rst" to "RST", "name" to tr("Имя и QTH"), "km" to tr("Расстояние")).forEach { (k, t) ->
                            CheckboxItem(t, checked = k in state.compactColumns, onCheckedChange = { state.toggleCompactColumn(k) })
                        }
                    }
                    Separator()
                    Item(tr("Дашборд"), shortcut = menuShortcut(KeyAction.DASHBOARD), onClick = state::dashboardOrDelete)
                    Item(tr("Дипломы"), shortcut = menuShortcut(KeyAction.AWARDS), onClick = { state.outsideCard(state::openAwards) })
                    Item(tr("Прохождение"), shortcut = menuShortcut(KeyAction.PROPAGATION), onClick = { state.outsideCard(state::openPropagation) })
                    Item(tr("Карта QSO"), shortcut = menuShortcut(KeyAction.MAP), onClick = state::openMap)
                    Item(tr("История поиска"), shortcut = menuShortcut(KeyAction.HISTORY), onClick = state::openHistory)
                }
                Menu(tr("Трансивер")) {
                    Item(tr("Пульт трансивера"), enabled = state.catConfig.enabled || state.rig != null, onClick = { state.showRigPanel = true })
                    Item(tr("CAT и программы…"), onClick = { state.openSettings(page = ru.r3xed.qsolog.SettingsPage.HARDWARE) })
                    Separator()
                    Item(tr("Следующая своя станция"), enabled = state.stations.size > 1, shortcut = menuShortcut(KeyAction.NEXT_STATION), onClick = { state.outsideCard(state::nextStation) })
                }
                Menu(tr("Сервис")) {
                    Item(tr("DX-кластер"), shortcut = menuShortcut(KeyAction.DX_CLUSTER), onClick = { state.outsideCard(state::openDxCluster) })
                    Item(tr("Онлайн-журналы: LoTW, eQSL, Club Log, QRZ.com, HAMLOG…"), shortcut = menuShortcut(KeyAction.ONLINE_LOGS), onClick = { state.openUpload(selectedOnly = false) })
                    Item(tr("Подтверждения LoTW"), shortcut = menuShortcut(KeyAction.LOTW_SYNC), onClick = state::syncLotw)
                    // Shown once the table is set up in the settings; the sync runs only when asked.
                    if (ru.r3xed.qsolog.data.SheetSync.isScriptUrl(state.sheetUrl)) {
                        Item(tr("Синхронизировать"), enabled = !state.sheetSyncing, onClick = state::syncSheet)
                    }
                    Separator()
                    Item(tr("Настройки"), shortcut = menuShortcut(KeyAction.SETTINGS), onClick = { state.openSettings() })
                }
                Menu(tr("Справка")) {
                    Item(tr("Справка и калькуляторы"), shortcut = menuShortcut(KeyAction.REFERENCE), onClick = { state.outsideCard(state::openReference) })
                    Item(tr("Горячие клавиши") + KeyMap.label(KeyAction.KEYS_HELP, IS_MAC).ifEmpty { null }?.let { " ($it)" }.orEmpty(), onClick = { state.showKeys = true })
                    Item(tr("Что нового"), onClick = { state.showWhatsNew = true })
                    Item(tr("Сообщить о проблеме"), onClick = {
                        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(ProblemReport.url(APP_VERSION, System.getProperty("os.name") + " " + System.getProperty("os.version"), System.getProperty("os.arch")))) }
                    })
                }
            } }
            // Theme from the settings; "system" follows the OS (macOS, Windows).
            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.CUSTOM -> state.customTheme.dark
            }
            QsoTheme(dark, state.uiScale, custom = state.customTheme.takeIf { state.themeMode == ThemeMode.CUSTOM }, focusRing = state.focusRing) {
                // Ctrl (or ⌘) with the mouse wheel: the scale, as in a browser; the lists below do not scroll then.
                var wheel by remember { mutableStateOf(0f) }
                androidx.compose.foundation.layout.Box(
                    Modifier.onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Scroll, androidx.compose.ui.input.pointer.PointerEventPass.Initial) { ev ->
                        val m = ev.keyboardModifiers
                        if (!(m.isCtrlPressed || m.isMetaPressed)) return@onPointerEvent
                        val ch = ev.changes.firstOrNull() ?: return@onPointerEvent
                        wheel += ch.scrollDelta.y
                        ev.changes.forEach { it.consume() }
                        // A notch of the wheel is 1; a touchpad sends many small steps — one zoom step per notch's worth.
                        if (wheel <= -1f) { state.zoomIn(); wheel = 0f } else if (wheel >= 1f) { state.zoomOut(); wheel = 0f }
                    },
                ) { key(state.language) { App(state, Exports(exportCsv, importCsv, exportAdif, importAdif, exportSelected, exportContest, importContest, saveExport)) } }
            }
        }
        }
    }
}

/** "⌘K": every command of the menus, searchable by name, with its key on the right. */
fun paletteCommands(state: AppState, files: Exports): List<PaletteCommand> {
    fun k(a: KeyAction) = KeyMap.label(a, IS_MAC)
    val m = if (IS_MAC) "⌘" else "Ctrl+"
    return listOf(
        PaletteCommand(tr("Новый QSO"), k(KeyAction.NEW_QSO)) { state.addQso() },
        PaletteCommand(tr("Найти в журнале"), k(KeyAction.SEARCH), state::requestSearchFocus),
        PaletteCommand(tr("Дашборд"), k(KeyAction.DASHBOARD), state::openDashboard),
        PaletteCommand(tr("Дипломы"), k(KeyAction.AWARDS), state::openAwards),
        PaletteCommand(tr("Прохождение"), k(KeyAction.PROPAGATION), state::openPropagation),
        PaletteCommand(tr("Карта QSO"), k(KeyAction.MAP), state::openMap),
        PaletteCommand(tr("DX-кластер"), k(KeyAction.DX_CLUSTER), state::openDxCluster),
        PaletteCommand(tr("История поиска"), k(KeyAction.HISTORY), state::openHistory),
        PaletteCommand(tr("Справка и калькуляторы"), k(KeyAction.REFERENCE), state::openReference),
        PaletteCommand(tr("Настройки"), k(KeyAction.SETTINGS)) { state.openSettings() },
        PaletteCommand(tr("Оборудование и программы…")) { state.openSettings(page = ru.r3xed.qsolog.SettingsPage.HARDWARE) },
        PaletteCommand(tr("Справочники и онлайн-журналы")) { state.openSettings(page = ru.r3xed.qsolog.SettingsPage.SOURCES) },
        PaletteCommand(tr("Пульт трансивера"), k(KeyAction.RIG_PANEL)) { state.showRigPanel = true },
        PaletteCommand(tr("Следующая своя станция"), k(KeyAction.NEXT_STATION), state::nextStation),
        PaletteCommand(tr("Экспорт лога в ADIF…"), k(KeyAction.EXPORT_ADIF), files.exportAdif),
        PaletteCommand(tr("Импорт лога из ADIF…"), k(KeyAction.IMPORT_ADIF), files.importAdif),
        PaletteCommand(tr("Экспорт лога в CSV…"), "", files.exportCsv),
        PaletteCommand(tr("Экспорт в ЕРМАК / Cabrillo…")) { state.openContestExport(selectedOnly = false) },
        PaletteCommand(tr("Онлайн-журналы: LoTW, eQSL, Club Log, QRZ.com, HAMLOG…"), k(KeyAction.ONLINE_LOGS)) { state.openUpload(selectedOnly = false) },
        PaletteCommand(tr("Подтверждения LoTW"), k(KeyAction.LOTW_SYNC), state::syncLotw),
        PaletteCommand(tr("Выбрать часть журнала…"), k(KeyAction.SELECT_PART)) { state.showSelect = true },
        PaletteCommand(tr("Печать QSL-наклеек"), "", state::printQslLabels),
        PaletteCommand(tr("Отметить: QSL отправлены"), "", state::markQslSent),
        PaletteCommand(tr("Дозаполнить данные корреспондентов"), "", state::fillMissing),
        PaletteCommand(tr("Сделать копию журнала"), k(KeyAction.BACKUP)) { state.backupNow() },
        PaletteCommand(tr("Открыть папку копий"), "", state::openBackupFolder),
        PaletteCommand(tr("Компактный журнал"), "", state::toggleCompactLog),
        PaletteCommand(tr("Журнал таблицей"), k(KeyAction.LOG_VIEW), state::toggleTableMode),
        PaletteCommand(tr("Крупнее"), "$m+", state::zoomIn),
        PaletteCommand(tr("Мельче"), "$m−", state::zoomOut),
        PaletteCommand(tr("Обычный размер (100%)"), "${m}0", state::zoomReset),
        PaletteCommand("CONTEST MODE", k(KeyAction.CONTEST_MODE), state::toggleContestMode),
        PaletteCommand(tr("Горячие клавиши"), k(KeyAction.KEYS_HELP)) { state.showKeys = true },
        PaletteCommand(tr("Что нового")) { state.showWhatsNew = true },
    ) + state.savedFilters.map { f -> PaletteCommand(tr("Фильтр: %s", f.name)) { state.applySavedFilter(f) } } +
        (state.activation?.let { listOf(PaletteCommand(tr("Завершить активацию %s", it.title)) { state.stopActivation(export = true) }) } ?: emptyList())
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
    // The settings table: what another device changed comes on start (it works after a restart).
    LaunchedEffect(Unit) { state.syncSettings() }
    // Surface sets the default text colour from the theme, so text is light grey in the dark theme.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
            // «Журнал таблицей»: the table over the whole window; a contact opens as a card over it, other screens take its place.
            val table = state.tableMode && state.pane in setOf(Pane.Empty, Pane.Edit, Pane.Contest)
            if (table) Box(Modifier.weight(1f).fillMaxWidth()) {
                ru.r3xed.qsolog.ui.LogTable(state, NEW_SHORTCUT, files.exportSelected, Modifier.fillMaxSize())
                when (state.pane) {
                    Pane.Edit -> ru.r3xed.qsolog.ui.TableCardOverlay { key(state.editSession) { EditPane(state) } }
                    Pane.Contest -> ru.r3xed.qsolog.ui.TableCardOverlay { key(state.editSession) { ContestPane(state) } }
                    else -> {}
                }
            } else Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!state.tableMode) {
                    LogPane(state, NEW_SHORTCUT, files.exportSelected, Modifier.width(460.dp * ru.r3xed.qsolog.ui.LocalTextBoost.current).fillMaxHeight())
                    VerticalDivider(color = LocalExtra.current.line)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (state.pane) {
                        Pane.Empty -> EmptyPane(state)
                        // A fresh card (also "＋ Следующая") starts with fresh fields, focus and scroll.
                        Pane.Edit -> key(state.editSession) { EditPane(state) }
                        Pane.Contest -> key(state.editSession) { ContestPane(state) }
                        Pane.Dashboard -> DashboardPane(state)
                        Pane.History -> HistoryPane(state)
                        Pane.Contests -> ContestsPane(state)
                        Pane.DxCluster -> ru.r3xed.qsolog.ui.DxClusterPane(state)
                        Pane.Settings -> SettingsPane(state, files.exportCsv, files.importCsv, files.exportAdif, files.importAdif, files.importContest)
                        Pane.Map -> MapPane(state)
                        Pane.Welcome -> WelcomePane(state)
                        Pane.Reference -> ReferencePane(onClose = state::closeReference, myPosition = state.settings.myPosition)
                        Pane.Awards -> ru.r3xed.qsolog.ui.AwardsPane(state)
                        Pane.Propagation -> ru.r3xed.qsolog.ui.PropagationPane(state)
                    }
                }
            }
            // The status bar: the radio, the cluster, the station in work, today's contacts, the band conditions.
            ru.r3xed.qsolog.ui.StatusBar(state)
            }
            if (state.showPalette) ru.r3xed.qsolog.ui.CommandPalette(paletteCommands(state, files)) { state.showPalette = false }
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
            if (state.showKeys) KeysDialog(state.cwMessages.takeIf { state.cwConfig.enabled }, onClose = { state.showKeys = false })
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
    val sections = KeyHints.sections(IS_MAC, cwMessages, desktop = true)
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.widthIn(max = 1180.dp).fillMaxWidth(0.94f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(tr("Горячие клавиши")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Balanced by lines: the sections in order, the left column takes them until it has half the lines.
                val half = sections.sumOf { it.keys.size + 2 } / 2
                var acc = 0
                val left = sections.takeWhile { sec -> (acc < half).also { acc += sec.keys.size + 2 } }
                val columns = listOf(left, sections.drop(left.size))
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
    FileDirs.show(dialog)
    val name = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return if (save && !name.lowercase().endsWith(".$ext")) File(dir, "$name.$ext") else File(dir, name)
}

const val PROJECT_URL = "https://github.com/vladkondratyev/qso-log"
val USER_AGENT = "QSO-LOG/$APP_VERSION (+$PROJECT_URL)"
