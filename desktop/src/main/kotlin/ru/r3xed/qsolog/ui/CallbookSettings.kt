package ru.r3xed.qsolog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ru.r3xed.qsolog.tr

/**
 * An account of a callbook asked after QRZ.ru and QRZ.com (HamQTH, QRZCQ): login, password, "Проверить вход" and its
 * answer ([status]: true — logged in, false — refused, null — checking). The same file is used by the phone and the desktop.
 */
@Composable
fun CallbookAccount(
    title: String,
    about: String,
    login: String,
    password: String,
    status: Pair<Boolean?, String>?,
    onLogin: (String) -> Unit,
    onPassword: (String) -> Unit,
    onCheck: () -> Unit,
) {
    val x = LocalExtra.current
    Section(title)
    Note(about)
    SettingField(tr("Логин (позывной)"), login, { onLogin(it.trim().uppercase()) }, mono = true)
    SecretField(tr("Пароль"), password, onPassword)
    status?.let { (ok, text) ->
        Text(
            text, style = MaterialTheme.typography.titleSmall,
            color = when (ok) { true -> x.ok; false -> MaterialTheme.colorScheme.error; null -> x.muted },
        )
    }
    if (login.isNotBlank()) ActionButton(tr("Проверить вход"), null, onCheck)
}
