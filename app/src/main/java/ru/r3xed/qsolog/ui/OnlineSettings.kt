package ru.r3xed.qsolog.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.data.OnlineConfig
import ru.r3xed.qsolog.data.OnlineLog
import ru.r3xed.qsolog.tr

// The same file is in the phone and the computer app.

/** The summary line of the block: the logbooks that are set up. */
fun onlineSummary(c: OnlineConfig, myCall: String, lotwReady: Boolean): String = listOfNotNull(
    "LoTW".takeIf { lotwReady },
    "eQSL".takeIf { c.problem(OnlineLog.EQSL, myCall) == null },
    "Club Log".takeIf { c.problem(OnlineLog.CLUBLOG, myCall) == null },
    "QRZ.com".takeIf { c.problem(OnlineLog.QRZCOM, myCall) == null },
).joinToString(", ").ifBlank { tr("учётные записи не указаны") }

/**
 * The accounts of LoTW, eQSL, Club Log and the QRZ.com logbook. Uploads are made from the log (⋮ → «Экспорт и импорт»,
 * or the picked records). [computer]: LoTW is signed by TQSL right here — its location and, if not found, its path;
 * [tqslFound] — where TQSL was found, or null.
 */
@Composable
fun OnlineSettings(c: OnlineConfig, onChange: (OnlineConfig) -> Unit, computer: Boolean, tqslFound: String? = null) {
    Note(tr("Здесь — только учётные записи. Выгрузка — из журнала: ⋮ → «Экспорт и импорт» → «Онлайн-журналы», или «Экспорт» у выбранных записей. Отметка о выгрузке с датой и временем видна в карточке каждой связи, и в следующий раз уходят только новые связи."))
    Section("LoTW (ARRL)")
    if (computer) {
        SettingField(tr("Station Location из TQSL"), c.lotwLocation, { onChange(c.copy(lotwLocation = it)) })
        SettingField(tr("Путь к TQSL, если он не найден сам"), c.tqslPath, { onChange(c.copy(tqslPath = it.trim())) }, mono = true)
        Note(
            (if (tqslFound != null) tr("TQSL найден: %s.", tqslFound) else tr("TQSL не найден: установите его с lotw.arrl.org или укажите путь.")) + " " +
                tr("QSO-LOG передаёт связи в TQSL, он подписывает их вашим сертификатом и отправляет в LoTW. Station Location — название места станции, как оно записано в TQSL (Station → Station Locations)."),
        )
    } else {
        Note(tr("LoTW принимает только связи, подписанные вашим сертификатом в программе TQSL от ARRL. Для Android её нет: телефон сохраняет файл, вы подписываете его в TQSL на компьютере («Sign a log and upload it») — или выгружаете из QSO-LOG для компьютера, он вызывает TQSL сам."))
    }
    Section("eQSL")
    SettingField(tr("Логин eQSL (позывной)"), c.eqslUser, { onChange(c.copy(eqslUser = it.trim().uppercase())) }, mono = true)
    SecretField(tr("Пароль eQSL"), c.eqslPassword) { onChange(c.copy(eqslPassword = it)) }
    SettingField(tr("QTH Nickname, если у позывного несколько профилей"), c.eqslNickname, { onChange(c.copy(eqslNickname = it)) })
    Note(tr("Связи уходят в eQSL из журнала, как в другие онлайн-журналы. Полученные карточки: ⋮ → «Экспорт и импорт» → «Карточки eQSL» — у найденных связей появится отметка «eQSL получена», а карточки, которых нет в журнале, можно добавить."))
    Section("Club Log")
    SettingField(tr("E-mail Club Log"), c.clublogEmail, { onChange(c.copy(clublogEmail = it.trim())) }, mono = true)
    SecretField(tr("Пароль Club Log"), c.clublogPassword) { onChange(c.copy(clublogPassword = it)) }
    SecretField(tr("Ключ приложения Club Log (API key)"), c.clublogAppKey) { onChange(c.copy(clublogAppKey = it.trim())) }
    Note(tr("Club Log пускает программы только с ключом приложения: его выдают по запросу на clublog.org (Help Desk → «Request an API key»). Позывной берётся из «Моей станции»."))
    Section("QRZ.com Logbook")
    SecretField(tr("API-ключ журнала QRZ.com"), c.qrzcomKey) { onChange(c.copy(qrzcomKey = it.trim())) }
    Note(tr("Ключ: qrz.com → My Logbook → Settings → «API Access Key». Нужна подписка QRZ.com, дающая доступ к API журнала. Это QRZ.com, не QRZ.ru."))
}

/** A key or a password: hidden, with an eye to show it. */
@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) tr("Скрыть пароль") else tr("Показать пароль"))
            }
        },
        shape = RoundedCornerShape(12.dp),
    )
}
