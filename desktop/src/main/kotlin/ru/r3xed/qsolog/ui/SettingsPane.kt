package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.APP_VERSION
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.IS_MAC
import ru.r3xed.qsolog.IS_WINDOWS
import ru.r3xed.qsolog.PROJECT_URL
import ru.r3xed.qsolog.Pane
import ru.r3xed.qsolog.data.AdifLabels
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.MODES

@Composable
fun SettingsPane(
    vm: AppState,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onExportAdif: () -> Unit,
    onImportAdif: () -> Unit,
) {
    val s = vm.settings
    val x = LocalExtra.current

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.pane = Pane.Empty }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад (Esc)", Modifier.size(30.dp))
            }
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Моя станция")
            SettingField("Мой позывной", s.myCall, { vm.updateSettings(s.copy(myCall = it.uppercase().trim())) }, mono = true, caps = true)
            val locOk = s.myLocator.isBlank() || Geo.isLocator(s.myLocator)
            SettingField(
                "QTH-локатор (например KO84ab)", s.myLocator, { vm.updateSettings(s.copy(myLocator = it.trim())) },
                mono = true, error = if (locOk) null else "Локатор: 2 буквы, 2 цифры, 2 буквы",
            )
            SettingField("Город / адрес QTH", s.myQth, { vm.updateSettings(s.copy(myQth = it)) })
            ActionButton("Определить локатор", Icons.Filled.MyLocation, vm::findMyLocator)
            Note("Расстояние и азимут считаются от QTH-локатора. Если вы сменили город, нажмите «Определить локатор»: кнопка возьмёт координаты из вашей карточки на QRZ.ru, а если их там нет, найдёт по городу.")

            // Station defaults: copied into each new contact, where they stay as that record's own values.
            Section("Моя станция для новых записей")
            SettingField("Мощность, Вт", s.power, { vm.updateSettings(s.copy(power = it)) }, mono = true)
            AdifLabels.MINE.forEach { (key, label) ->
                SettingField(label, s.station[key].orEmpty(), { vm.updateSettings(s.copy(station = s.station + (key to it))) })
            }
            Note("Эти значения попадают в каждую новую связь и хранятся в ней. Если позже их поменять, старые записи не изменятся.")

            Section("Учётная запись QRZ.ru")
            SettingField("Логин", s.qrzLogin, { vm.updateSettings(s.copy(qrzLogin = it.trim())) }, mono = true)
            var show by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = s.qrzPassword,
                onValueChange = { vm.updateSettings(s.copy(qrzPassword = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Пароль") },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { show = !show }) {
                        Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) "Скрыть пароль" else "Показать пароль")
                    }
                },
                shape = RoundedCornerShape(12.dp),
            )
            vm.qrzStatus?.let { status ->
                val color = when (vm.qrzOk) {
                    true -> x.ok
                    false -> MaterialTheme.colorScheme.error
                    null -> x.muted
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(color, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(status, color = color, style = MaterialTheme.typography.titleSmall)
                }
            }
            ActionButton("Проверить подключение", null, vm::testQrz)
            Note(
                when {
                    IS_MAC -> "Пароль хранится в Связке ключей macOS."
                    IS_WINDOWS -> "Пароль хранится в Диспетчере учётных данных Windows."
                    else -> "Пароль хранится в связке ключей (GNOME Keyring или KWallet). Если её нет, пароль записывается в файл настроек в домашней папке."
                },
            )

            Section("Диапазоны в карточке связи")
            ToggleGrid(BANDS, vm.enabledBands, vm::setBandEnabled)
            Note("Включённые диапазоны показываются кнопками при добавлении связи.")
            Section("Виды связи в карточке")
            ToggleGrid(MODES, vm.enabledModes, vm::setModeEnabled)
            Note("Если у записи диапазон или вид, который здесь выключен, кнопка для него в её карточке всё равно видна.")

            Section("Журнал связей · записей: ${vm.total}")
            ActionButton("Экспорт в ADIF", Icons.Filled.FileUpload, onExportAdif)
            ActionButton("Импорт из ADIF", Icons.Filled.FileDownload, onImportAdif)
            Note("ADIF (.adi) понимают LogHX, UR5EQF, HamLog, N1MM, QRZ.com. Файл сохраняется в кодировке Windows-1251, как у LogHX. При импорте кодировка определяется сама (Windows-1251 или UTF-8). Все поля файла сохраняются в карточке.")
            ActionButton("Экспорт в CSV", Icons.Filled.FileUpload, onExportCsv)
            ActionButton("Импорт из CSV", Icons.Filled.FileDownload, onImportCsv)
            Note("CSV открывается в Excel: разделитель «;», все поля каждой связи, включая дополнительные поля ADIF. При любом импорте повторы пропускаются.")

            var confirmDeleteAll by remember { mutableStateOf(false) }
            Button(
                onClick = { confirmDeleteAll = true },
                enabled = vm.total > 0,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) {
                Icon(Icons.Filled.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text("Удалить всю историю QSO", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (confirmDeleteAll) {
                AlertDialog(
                    onDismissRequest = { confirmDeleteAll = false },
                    icon = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Удалить всю историю QSO?") },
                    text = {
                        Text(
                            "Будут удалены все связи (${vm.total}) и голосовые заметки. Отменить это нельзя. " +
                                "Если нужна копия, сначала сделайте экспорт в ADIF или CSV.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { confirmDeleteAll = false; vm.deleteAll() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                        ) { Text("Удалить всё") }
                    },
                    dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Отмена") } },
                )
            }
            // About: version, tap to open the project page.
            val uri = LocalUriHandler.current
            Column(
                Modifier.fillMaxWidth().padding(top = 28.dp)
                    .clickable(onClickLabel = "Открыть страницу проекта на GitHub") { uri.openUri(PROJECT_URL) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                QsoLogo(fontSize = 22.sp)
                Text("Версия $APP_VERSION", style = MaterialTheme.typography.bodyLarge, color = x.muted)
                Text(
                    PROJECT_URL.removePrefix("https://"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Items with an on/off switch each, two to a row. */
@Composable
private fun ToggleGrid(items: List<String>, enabled: Set<String>, onToggle: (String, Boolean) -> Unit) {
    val x = LocalExtra.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    val on = item in enabled
                    Row(
                        Modifier.weight(1f)
                            .background(x.field, RoundedCornerShape(12.dp))
                            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle(item, it) })
                            .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        // The row handles the tap; the switch only shows the state.
                        Switch(checked = on, onCheckedChange = null)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = LocalExtra.current.muted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
}

@Composable
private fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
        if (icon != null) {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    mono: Boolean = false,
    caps: Boolean = false,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        textStyle = if (mono) TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        else MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
        keyboardOptions = KeyboardOptions(
            capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
            autoCorrect = false,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}
