package ru.r3xed.qsolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.r3xed.qsolog.tr

/**
 * A key from a physical keyboard (Bluetooth, USB OTG), not from the on-screen one: some on-screen keyboards send
 * Enter and Space as key events too, and those must keep doing what the field's own keyboard action says.
 */
internal fun KeyEvent.fromHardware(): Boolean = (nativeKeyEvent.flags and android.view.KeyEvent.FLAG_SOFT_KEYBOARD) == 0

internal val DIGIT_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

/** F1 or Ctrl+/: every key of an external keyboard on one screen. */
@Composable
fun HardwareKeysDialog(onClose: () -> Unit) {
    val keys = listOf(
        "Ctrl+N · F9" to tr("новая связь"),
        "Enter" to tr("записать связь из любого поля (новая карточка — записать и открыть следующую)"),
        tr("Пробел") to tr("из позывного — к частоте или RST (в контесте — к принятому коду)"),
        "Tab · Shift+Tab" to tr("следующее и предыдущее поле"),
        "↑ · Ctrl+E" to tr("исправить последнюю записанную связь (↑ — в пустом позывном)"),
        "Esc" to tr("очистить новую карточку, второй раз — закрыть"),
        "Ctrl+F" to tr("поиск по журналу; Enter в поиске — новая связь с найденным"),
        "F2 · F3 · F4 · F5" to tr("позывной, частота, RST отправлен, RST принят"),
        "F6" to tr("текущее время UTC"),
        "F8" to tr("сохранить и открыть следующую"),
        "F12" to tr("сохранить"),
        "Alt+1…9" to tr("выбрать диапазон"),
        "Alt+Shift+1…9" to tr("выбрать вид связи"),
        "PgUp · PgDn" to tr("контест: предыдущая и следующая связь"),
        "Ctrl+D · Ctrl+M · Ctrl+," to tr("дашборд, карта QSO, настройки"),
        "F1 · Ctrl+/" to tr("эта подсказка"),
    )
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Внешняя клавиатура")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // The key on its own line, what it does under it: long combinations fit on a phone.
                keys.forEach { (k, what) ->
                    Column {
                        Text(k, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(what, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    tr("Команды в поле позывного (вместо позывного, затем Enter или Пробел): 20m или 20 — диапазон, CW, SSB, FT8 — вид связи, 14195 или 7.074 — частота. Поле очищается, карточка остаётся."),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    tr("Когда подключена клавиатура, своя экранная клавиатура программы прячется, а курсор сразу стоит в позывном."),
                    style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Закрыть")) } },
    )
}
