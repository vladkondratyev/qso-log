package ru.r3xed.qsolog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import ru.r3xed.qsolog.ui.LogScreen
import ru.r3xed.qsolog.ui.MapScreen
import ru.r3xed.qsolog.ui.QsoTheme
import ru.r3xed.qsolog.ui.SettingsScreen
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        enableEdgeToEdge()
        setContent {
            QsoTheme {
                val snackbar = remember { SnackbarHostState() }
                val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                    uri?.let(vm::exportCsv)
                }
                val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(vm::importCsv)
                }
                // .adi has no registered MIME type; octet-stream keeps the file name as given.
                val exportAdif = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                    uri?.let(vm::exportAdif)
                }
                val importAdif = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(vm::importAdif)
                }
                val exportSelectedAdif = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                    uri?.let(vm::exportSelectedAdif)
                }
                val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    vm.say(
                        if (granted) "Микрофон разрешён. Удерживайте «Добавить QSO», чтобы записать голос"
                        else "Без доступа к микрофону голосовые заметки недоступны. Разрешить можно в настройках Android"
                    )
                }
                LaunchedEffect(Unit) {
                    vm.messages.collect { m ->
                        val r = snackbar.showSnackbar(
                            m.text,
                            actionLabel = if (m.undo != null) "Отменить" else null,
                            duration = if (m.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                        )
                        if (r == SnackbarResult.ActionPerformed) m.undo?.invoke()
                    }
                }
                // Surface sets the default text colour from the theme (light grey on the dark theme);
                // without it Text falls back to black on every theme.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize()) {
                        when (vm.screen) {
                            Screen.Log -> LogScreen(
                                vm,
                                hasMicPermission = {
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                },
                                requestMicPermission = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                                onExportSelected = { exportSelectedAdif.launch(vm.selectedAdifFileName()) },
                            )
                            Screen.Map -> MapScreen(vm)
                            Screen.Edit -> EditScreen(vm)
                            Screen.Settings -> SettingsScreen(
                                vm,
                                onExportCsv = { export.launch(vm.csvFileName()) },
                                onImportCsv = { import.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                                onExportAdif = { exportAdif.launch(vm.adifFileName()) },
                                onImportAdif = { importAdif.launch(arrayOf("*/*")) },
                            )
                        }
                        SnackbarHost(
                            snackbar,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = if (vm.screen == Screen.Edit) 80.dp else 16.dp),
                        )
                    }
                }
            }
        }
    }
}
