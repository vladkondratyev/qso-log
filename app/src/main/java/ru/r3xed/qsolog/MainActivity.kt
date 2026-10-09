package ru.r3xed.qsolog

import androidx.compose.runtime.key
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.content.res.Configuration as AndroidConfig
import androidx.compose.ui.platform.LocalConfiguration
import ru.r3xed.qsolog.ui.HardwareKeysDialog
import ru.r3xed.qsolog.ui.HistoryScreen
import ru.r3xed.qsolog.ui.HistoryOnDialog
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import ru.r3xed.qsolog.ui.EditScreen
import ru.r3xed.qsolog.ui.ContestScreen
import ru.r3xed.qsolog.ui.DashboardScreen
import ru.r3xed.qsolog.ui.LogScreen
import ru.r3xed.qsolog.ui.MapScreen
import ru.r3xed.qsolog.ui.ContestExportDialog
import ru.r3xed.qsolog.ui.ExportDialog
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.ui.QsoTheme
import ru.r3xed.qsolog.ui.SettingsScreen
import ru.r3xed.qsolog.ui.WelcomeScreen
import ru.r3xed.qsolog.ui.ReferenceScreen
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /**
     * Keys of a physical keyboard that work on every screen, before the focused field sees them: Ctrl+N / F9 a new
     * contact, Ctrl+E the contact just logged, Ctrl+F the search, F1 / Ctrl+/ the list of keys, Ctrl+D / Ctrl+M /
     * Ctrl+, / Ctrl+H the dashboard, map, settings and search history. Esc nobody took goes back, as the system Back does.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The Morse trainer is open: an external key through an adapter (keys), or wired as a headset button.
        ru.r3xed.qsolog.morse.MorseKeyBus.session?.let { s ->
            val which = when (event.keyCode) {
                KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_VOLUME_UP -> 1
                KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_VOLUME_DOWN -> 2
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> 0
                else -> -1
            }
            if (which >= 0) {
                if (event.action == KeyEvent.ACTION_UP) s.press(which, false)
                else if (event.repeatCount == 0) s.press(which, true)
                return true
            }
        }
        val soft = event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0
        val physical = !soft && event.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD
        // Typing on a keyboard whose attachment the system did not report: switch to the text fields all the same.
        if (physical && event.action == KeyEvent.ACTION_DOWN && vm.keypadShown && event.isPrintingKey &&
            (vm.screen == Screen.Edit || vm.screen == Screen.Contest)) vm.hardKeyboard = true
        if (soft || event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val inCard = vm.screen == Screen.Edit || vm.screen == Screen.Contest
        val inLog = vm.screen == Screen.Log
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        val handled = when {
            event.keyCode == KeyEvent.KEYCODE_F9 || (ctrl && event.keyCode == KeyEvent.KEYCODE_N) -> {
                if (!inCard) vm.addQso()
                true
            }
            ctrl && !event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_E -> {
                if (inLog || inCard) vm.editLast()
                true
            }
            ctrl && event.keyCode == KeyEvent.KEYCODE_F -> {
                // From a card: only an untouched one is left for the log.
                if (inCard && !vm.hasUnsavedChanges) vm.closeEditor()
                if (vm.screen == Screen.Log) vm.requestSearchFocus()
                true
            }
            event.keyCode == KeyEvent.KEYCODE_F1 || (ctrl && event.keyCode == KeyEvent.KEYCODE_SLASH) -> { vm.showKeys = !vm.showKeys; true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_D && inLog -> { vm.openDashboard(); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_M && inLog -> { vm.openMap(); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_COMMA && inLog -> { vm.openSettings(); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_H && inLog -> { vm.openHistory(); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_K && !inCard -> { vm.toggleContestMode(); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_R && !inCard -> { vm.syncByKey(); true }
            ctrl && event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_E && inLog -> { vm.openExport(ExportFormat.ADIF, selectedOnly = false); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_A && inLog && !vm.searchFocused -> { vm.selectAllShown(); true }
            inLog && !vm.searchFocused && !ctrl -> logKey(event)
            vm.screen == Screen.History && !vm.historyTyping && vm.historyEditCalls == null && vm.historyDeleteAsk == null -> historyKey(event, ctrl)
            vm.screen == Screen.Dashboard && event.keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_4 -> {
                vm.changeDashFilter(vm.dashFilter.copy(period = ru.r3xed.qsolog.data.StatPeriod.entries[event.keyCode - KeyEvent.KEYCODE_1]))
                true
            }
            else -> false
        }
        if (handled) return true
        if (super.dispatchKeyEvent(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && vm.screen != Screen.Log) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return false
    }

    /** The log without a focused field: the arrows walk through the rows, Enter opens, Space picks, Delete deletes. */
    private fun logKey(e: KeyEvent): Boolean {
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> vm.moveLogCursor(1)
            KeyEvent.KEYCODE_DPAD_UP -> vm.moveLogCursor(-1)
            KeyEvent.KEYCODE_PAGE_DOWN -> vm.moveLogCursor(10)
            KeyEvent.KEYCODE_PAGE_UP -> vm.moveLogCursor(-10)
            KeyEvent.KEYCODE_MOVE_HOME -> vm.moveLogCursor(0, to = -1)
            KeyEvent.KEYCODE_MOVE_END -> vm.moveLogCursor(0, to = 1)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> vm.openLogCursor()
            KeyEvent.KEYCODE_SPACE -> vm.toggleLogCursor()
            KeyEvent.KEYCODE_FORWARD_DEL -> vm.deleteLogCursor()
            KeyEvent.KEYCODE_ESCAPE -> if (vm.selecting) vm.clearSelection() else if (vm.logCursor != null) vm.logCursor = null else return false
            else -> return false
        }
        return true
    }

    /** The search history: arrows, Enter, Space, and one letter per action on the row, the picked ones or the open entry. */
    private fun historyKey(e: KeyEvent, ctrl: Boolean): Boolean {
        val open = vm.historyOpen != null
        when {
            ctrl && e.keyCode == KeyEvent.KEYCODE_A && !open -> vm.selectAllHistory()
            ctrl -> return false
            e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !open -> vm.moveHistoryCursor(1)
            e.keyCode == KeyEvent.KEYCODE_DPAD_UP && !open -> vm.moveHistoryCursor(-1)
            (e.keyCode == KeyEvent.KEYCODE_ENTER || e.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && !open -> vm.openHistoryCursor()
            e.keyCode == KeyEvent.KEYCODE_SPACE && !open -> vm.historyCursor?.let(vm::toggleHistorySelected)
            e.keyCode == KeyEvent.KEYCODE_F -> vm.historyTargets().ifEmpty { null }?.let(vm::toggleFavorite)
            e.keyCode == KeyEvent.KEYCODE_E -> vm.historyTargets().ifEmpty { null }?.let { vm.historyEditCalls = it }
            e.keyCode == KeyEvent.KEYCODE_FORWARD_DEL -> vm.historyTargets().ifEmpty { null }?.let { vm.historyDeleteAsk = it }
            e.keyCode == KeyEvent.KEYCODE_N -> vm.newQsoFromHistoryTarget()
            e.keyCode == KeyEvent.KEYCODE_M && !open -> vm.historyMap = !vm.historyMap
            else -> return false
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        enableEdgeToEdge()
        setContent {
            // Theme from the settings; "system" follows Android. Status and navigation bar icons follow it too.
            val dark = when (vm.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            // A Bluetooth or USB keyboard attached: the app's on-screen keypad gives way to the text fields.
            val cfg = LocalConfiguration.current
            LaunchedEffect(cfg.keyboard, cfg.hardKeyboardHidden) {
                vm.hardKeyboard = cfg.keyboard == AndroidConfig.KEYBOARD_QWERTY && cfg.hardKeyboardHidden == AndroidConfig.HARDKEYBOARDHIDDEN_NO
            }
            QsoTheme(dark) {
                val snackbar = remember { SnackbarHostState() }
                val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                    uri?.let(vm::exportFile)
                }
                val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(vm::importCsv)
                }
                // .adi has no registered MIME type; octet-stream keeps the file name as given.
                val exportAdif = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                    uri?.let(vm::exportFile)
                }
                val importAdif = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(vm::importAdif)
                }
                // Contest reports: .txt (ЕРМАК) and .cbr (Cabrillo) are plain text.
                val exportContest = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                    uri?.let(vm::exportContest)
                }
                val importContest = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(vm::importContest)
                }
                val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    vm.say(
                        if (granted && vm.screen == Screen.Edit) tr("Микрофон разрешён. Нажмите значок микрофона ещё раз")
                        else if (granted) tr("Микрофон разрешён. Удерживайте «Добавить QSO», чтобы записать голос")
                        else tr("Без доступа к микрофону голосовые заметки недоступны. Разрешить можно в настройках Android")
                    )
                }
                LaunchedEffect(Unit) {
                    vm.messages.collect { m ->
                        val r = snackbar.showSnackbar(
                            m.text,
                            actionLabel = if (m.undo != null) tr("Отменить") else null,
                            duration = if (m.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                        )
                        if (r == SnackbarResult.ActionPerformed) m.undo?.invoke()
                    }
                }
                // Surface sets the default text colour from the theme (light grey on the dark theme);
                // without it Text falls back to black on every theme.
                // Texts are read through tr() while composing: a new language re-creates the screen.
                key(vm.language) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize()) {
                        when (vm.screen) {
                            Screen.Log -> LogScreen(
                                vm,
                                hasMicPermission = {
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                },
                                requestMicPermission = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                            )
                            Screen.Map -> MapScreen(vm)
                            Screen.Welcome -> WelcomeScreen(vm)
                            Screen.Reference -> ReferenceScreen(onClose = vm::closeReference, myPosition = vm.settings.myPosition)
                            // A fresh card (also "＋ Следующая") starts with fresh fields, focus and scroll.
                            Screen.Edit -> key(vm.editSession) { EditScreen(
                                vm,
                                hasMicPermission = {
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                },
                                requestMicPermission = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                            ) }
                            Screen.Contest -> key(vm.editSession) { ContestScreen(vm) }
                            Screen.Dashboard -> DashboardScreen(vm)
                            Screen.History -> HistoryScreen(vm)
                            Screen.Settings -> SettingsScreen(
                                vm,
                                onImportCsv = { import.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                                onImportAdif = { importAdif.launch(arrayOf("*/*")) },
                                onImportContest = { importContest.launch(arrayOf("*/*")) },
                            )
                        }
                        if (vm.showKeys) HardwareKeysDialog(onClose = { vm.showKeys = false })
                        if (vm.historyAsk) HistoryOnDialog(onConfirm = { vm.changeHistoryOn(true) }, onDismiss = { vm.historyAsk = false })
                        vm.contestTarget?.let { target ->
                            ContestExportDialog(
                                defaults = vm.contestDefaults(),
                                selected = target.only?.size,
                                hasContest = vm.hasContestQsos(target.only),
                                count = { onlyNew, contestOnly -> vm.exportCandidates(ExportFormat.CONTEST, target.only, onlyNew, contestOnly).size },
                                onDismiss = vm::closeContestExport,
                                onConfirm = { h, onlyNew, mark, contestOnly -> exportContest.launch(vm.prepareContest(h, onlyNew, mark, contestOnly)) },
                            )
                        }
                        vm.exportTarget?.let { target ->
                            ExportDialog(
                                format = target.format,
                                selected = target.only?.size,
                                count = { onlyNew -> vm.exportCandidates(target.format, target.only, onlyNew).size },
                                onDismiss = vm::closeExport,
                                utf8 = vm.adifUtf8,
                                onUtf8 = vm::chooseAdifUtf8,
                                onConfirm = { onlyNew, mark ->
                                    val name = vm.prepareExport(onlyNew, mark)
                                    if (target.format == ExportFormat.CSV) export.launch(name) else exportAdif.launch(name)
                                },
                            )
                        }
                        // In the contest card the keyboard stays open and the fields fill the screen: messages go to the top.
                        if (vm.screen == Screen.Contest) SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 4.dp))
                        else SnackbarHost(
                            snackbar,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = if (vm.screen == Screen.Edit) 80.dp else 16.dp),
                        )
                    }
                }
                }
            }
        }
    }
}
