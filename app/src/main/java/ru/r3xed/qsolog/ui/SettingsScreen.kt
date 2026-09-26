package ru.r3xed.qsolog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import ru.r3xed.qsolog.ThemeMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.selection.selectable
import ru.r3xed.qsolog.UpdateState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.BuildConfig
import ru.r3xed.qsolog.data.AdifLabels
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.MODES

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onExportAdif: () -> Unit,
    onImportAdif: () -> Unit,
    onImportContest: () -> Unit,
) {
    val s = vm.settings
    val x = LocalExtra.current
    BackHandler { vm.closeSettings() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeSettings, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", Modifier.size(30.dp))
            }
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Each block opens on its own; the header sums up what is set. A first start opens what must be filled.
            SettingsBlock("Моя станция", listOfNotNull(s.myCall.ifBlank { null }, s.myLocator.ifBlank { null }, s.power.ifBlank { null }?.let { "$it Вт" }, s.station["MY_RIG"]?.ifBlank { null }).joinToString(" · ").ifBlank { "не заполнено" }, open = s.myCall.isBlank() || s.myLocator.isBlank()) {
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
                Text("Для новых записей", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                SettingField("Мощность, Вт", s.power, { vm.updateSettings(s.copy(power = it)) }, mono = true)
                AdifLabels.MINE.forEach { (key, label) ->
                    SettingField(label, s.station[key].orEmpty(), { vm.updateSettings(s.copy(station = s.station + (key to it))) })
                }
                Note("Эти значения попадают в каждую новую связь и хранятся в ней. Если позже их поменять, старые записи не изменятся.")
            }

            SettingsBlock(
                "Учётная запись XML API QRZ.ru",
                when {
                    s.qrzLogin.isBlank() -> "не указана"
                    vm.qrzOk == true -> "${s.qrzLogin} · подключено"
                    vm.qrzOk == false -> "${s.qrzLogin} · ошибка"
                    else -> s.qrzLogin
                },
                open = s.qrzLogin.isBlank() || s.qrzPassword.isBlank(),
            ) {
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
            QrzApiHelp(expandedByDefault = s.qrzLogin.isBlank())
            ActionButton("Проверить подключение", null, vm::testQrz)
            }

            // Station defaults: copied into each new contact, where they stay as that record's own values.
            SettingsBlock("Второй источник: HamQTH", if (vm.hamqthEnabled) "включён · без учётной записи" else "выключен") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field)
                        .toggleable(value = vm.hamqthEnabled, role = Role.Switch, onValueChange = vm::setHamqth)
                        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Страна и область по позывному", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = vm.hamqthEnabled, onCheckedChange = null)
                }
                Note(
                    "Если учётной записи QRZ.ru нет, позывного там нет или QRZ.ru не отвечает, программа спросит бесплатный " +
                        "справочник HamQTH.com. Он по префиксу даёт страну, область, зоны CQ и ITU и центр области — " +
                        "расстояние тогда примерное, со знаком ≈. Имени и точного QTH там нет. Учётная запись не нужна.",
                )
            }

            SettingsBlock("Диапазоны и виды связи", "диапазонов: ${vm.enabledBands.size} · видов: ${vm.enabledModes.size}") {
                Section("Диапазоны")
                ToggleGrid(BANDS, vm.enabledBands, vm::setBandEnabled)
                Note("Включённые диапазоны показываются кнопками при добавлении связи.")
                Section("Вид связи")
                ToggleGrid(MODES, vm.enabledModes, vm::setModeEnabled)
                Note("Если у записи диапазон или вид, который здесь выключен, кнопка для него в её карточке всё равно видна.")
            }

            SettingsBlock("Оформление", "тема: ${vm.themeMode.label.lowercase()}") {
                Text("Тема", style = MaterialTheme.typography.titleSmall)
                // Three options in one row; the chosen one is filled.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val on = vm.themeMode == mode
                        Box(
                            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary else x.field)
                                .selectable(selected = on, role = Role.RadioButton) { vm.setTheme(mode) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                mode.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Note("«Как в системе» переключается вместе с тёмной темой Android.")
            }
            SettingsBlock("Журнал связей", "записей: ${vm.total} · ADIF, CSV") {
                ActionButton("Экспорт в ADIF", Icons.Filled.FileUpload, onExportAdif)
                ActionButton("Импорт из ADIF", Icons.Filled.FileDownload, onImportAdif)
                Note("ADIF (.adi) понимают LogHX, UR5EQF, HamLog, N1MM, QRZ.com. Файл сохраняется в кодировке Windows-1251, как у LogHX. При импорте кодировка определяется сама (Windows-1251 или UTF-8). Все поля файла сохраняются в карточке.")
                ActionButton("Экспорт в CSV", Icons.Filled.FileUpload, onExportCsv)
                ActionButton("Импорт из CSV", Icons.Filled.FileDownload, onImportCsv)
                Note("CSV открывается в Excel: разделитель «;», все поля каждой связи, включая дополнительные поля ADIF. При любом импорте повторы пропускаются.")
                ActionButton("Экспорт в ЕРМАК / Cabrillo", Icons.Filled.FileUpload) { vm.openContestExport(selectedOnly = false) }
                ActionButton("Импорт из ЕРМАК / Cabrillo", Icons.Filled.FileDownload, onImportContest)
                Note("Отчёты для соревнований. ЕРМАК — российский формат (ermak.srr.ru), Cabrillo 3.0 — международный. Перед сохранением программа спросит код соревнования и категорию. Чтобы выгрузить только часть журнала, выберите записи долгим нажатием и нажмите «Экспорт».")

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
                Text("Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge, color = x.muted)
                Text(
                    PROJECT_URL.removePrefix("https://"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            }
            UpdateCheck(vm)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val PROJECT_URL = "https://github.com/vladkondratyev/qso-log"

/** "Проверить обновления": asks GitHub for the latest release; a newer one opens a dialog with its changes and a download. */
@Composable
private fun UpdateCheck(vm: AppViewModel) {
    val x = LocalExtra.current
    val uri = LocalUriHandler.current
    val state = vm.update
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = vm::checkUpdate,
            enabled = state != UpdateState.Checking,
            modifier = Modifier.height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (state == UpdateState.Checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.SystemUpdate, null)
            Spacer(Modifier.width(8.dp))
            Text(if (state == UpdateState.Checking) "Проверяю…" else "Проверить обновления", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        when (state) {
            UpdateState.UpToDate -> Text("У вас последняя версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge, color = x.ok)
            is UpdateState.Failed -> Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
    if (state is UpdateState.Available) {
        val r = state.release
        AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            icon = { Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Доступна версия ${r.version}") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Установлена ${BuildConfig.VERSION_NAME}. Что нового:", style = MaterialTheme.typography.titleSmall)
                    Text(r.notes.ifBlank { "Описание изменений есть на странице релиза." }, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                // The APK opens in the browser; Android then offers to install it over the current version.
                Button(onClick = { uri.openUri(r.apkUrl ?: r.pageUrl); vm.dismissUpdate() }) { Text("Скачать") }
            },
            dismissButton = { TextButton(onClick = vm::dismissUpdate) { Text("Позже") } },
        )
    }
}

private const val QRZ_API_REQUEST_URL = "https://www.qrz.ru/personal/settings/apiuser"
private const val QRZ_API_HELP_URL = "https://www.qrz.ru/help/api/xml"

/**
 * How to get XML API access on QRZ.ru, from their help page. Open right away while no login is entered;
 * afterwards folded into one "Как получить доступ?" line.
 */
@Composable
private fun QrzApiHelp(expandedByDefault: Boolean) {
    val x = LocalExtra.current
    val uri = LocalUriHandler.current
    var open by rememberSaveable(expandedByDefault) { mutableStateOf(expandedByDefault) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Свернуть" else "Развернуть") { open = !open }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Как получить доступ?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Войдите на qrz.ru. Ваш позывной должен быть в Callbook QRZ.ru.",
                "Откройте «Личные данные» → внизу раздел «XML API» → «Создать аккаунт».",
                "Укажите свой позывной и программу: QSO-LOG.",
                "Логин и пароль для XML API придут через некоторое время. Это не пароль от сайта.",
            ).forEachIndexed { i, step ->
                Row {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "Доступ дают только на постоянные позывные радиолюбителей и наблюдателей, только для личного аппаратного журнала.",
                style = MaterialTheme.typography.bodyMedium, color = x.muted,
            )
            OutlinedButton(
                onClick = { uri.openUri(QRZ_API_REQUEST_URL) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text("Запросить доступ на QRZ.ru", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(
                "Подробнее: qrz.ru/help/api/xml",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClickLabel = "Открыть справку QRZ.ru") { uri.openUri(QRZ_API_HELP_URL) }.padding(vertical = 4.dp),
            )
        }
    }
}

/** A collapsible settings block: title, one-line summary of what is set, chevron. */
@Composable
private fun SettingsBlock(title: String, summary: String, open: Boolean = false, content: @Composable () -> Unit) {
    val x = LocalExtra.current
    var expanded by rememberSaveable(title) { mutableStateOf(open) }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, x.line, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(x.field)
                .clickable(onClickLabel = if (expanded) "Свернуть" else "Развернуть") { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
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
