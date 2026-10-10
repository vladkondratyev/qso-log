package ru.r3xed.qsolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import ru.r3xed.qsolog.WhatsNew
import ru.r3xed.qsolog.tr

// The same file on Android and the computer (app/ and desktop/ ui/WhatsNewDialog.kt; there links open with openUrl): change both.

/** "Что нового в версии X": the points of this release; the full history is on the releases page. */
@Composable
fun WhatsNewDialog(version: String, desktop: Boolean, onClose: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Filled.NewReleases, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(tr("Что нового в версии %s", version)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WhatsNew.items(desktop).forEach { item ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Text(item, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text(tr("Понятно")) } },
        dismissButton = { TextButton(onClick = { uri.openUri(WhatsNew.RELEASES_URL) }) { Text(tr("Все версии")) } },
    )
}

/** Under the version in the settings: "Что нового" and "Сообщить о проблеме" (a GitHub issue with the version and the device). */
@Composable
fun AboutActions(onWhatsNew: () -> Unit, reportUrl: () -> String) {
    val uri = LocalUriHandler.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onWhatsNew) {
            Icon(Icons.Filled.NewReleases, null)
            Text("  " + tr("Что нового"))
        }
        TextButton(onClick = { uri.openUri(reportUrl()) }) {
            Icon(Icons.Filled.BugReport, null)
            Text("  " + tr("Сообщить о проблеме"))
        }
    }
}
