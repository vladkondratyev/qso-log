package ru.r3xed.qsolog.ui

import androidx.compose.foundation.ScrollState
import ru.r3xed.qsolog.SettingsPage
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import ru.r3xed.qsolog.I18n
import ru.r3xed.qsolog.data.SheetSync
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.text.selection.SelectionContainer
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.Lang
import ru.r3xed.qsolog.tr
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
import androidx.compose.material.icons.filled.Keyboard
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ru.r3xed.qsolog.data.ContestMode
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.data.KeypadMode
import ru.r3xed.qsolog.cat.CatLinkType
import ru.r3xed.qsolog.cat.CatProtocol
import ru.r3xed.qsolog.cat.CatStatus
import ru.r3xed.qsolog.digi.DigiStatus
import ru.r3xed.qsolog.digi.describe
import ru.r3xed.qsolog.digi.localAddresses
import ru.r3xed.qsolog.cat.Rigs
import ru.r3xed.qsolog.cat.describe
import ru.r3xed.qsolog.cat.short
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onImportCsv: () -> Unit,
    onImportAdif: () -> Unit,
    onImportContest: () -> Unit,
    onLaunchApps: () -> Unit = {},
) {
    val s = vm.settings
    val x = LocalExtra.current
    BackHandler { vm.closeSettings() }

    // Each sub-page starts at the top; the main page keeps its place while one is open.
    val pageScroll = remember(vm.settingsPage) { ScrollState(0) }
    val scroll = if (vm.settingsPage == SettingsPage.MAIN) vm.settingsScroll else pageScroll

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeSettings, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), Modifier.size(30.dp))
            }
            Text(settingsTitle(vm.settingsPage), style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (vm.settingsPage) {
                SettingsPage.MAIN -> {
                    // Each block opens on its own; the header sums up what is set. A first start opens what must be filled.
                    SettingsBlock(tr("Моя станция"), listOfNotNull(s.myCall.ifBlank { null }, s.myLocator.ifBlank { null }, s.power.ifBlank { null }?.let { tr("%s Вт", it) }, s.station["MY_RIG"]?.ifBlank { null }).joinToString(" · ").ifBlank { tr("не заполнено") }, open = s.myCall.isBlank() || s.myLocator.isBlank()) {
                        SettingField(tr("Мой позывной"), s.myCall, { vm.updateSettings(s.copy(myCall = it.uppercase().trim())) }, mono = true, caps = true)
                        val locOk = s.myLocator.isBlank() || Geo.isLocator(s.myLocator)
                        SettingField(
                            tr("QTH-локатор (например KO84ab)"), s.myLocator, { vm.updateSettings(s.copy(myLocator = it.trim())) },
                            mono = true, error = if (locOk) null else tr("Локатор: 2 буквы, 2 цифры, 2 буквы"),
                        )
                        SettingField(tr("Город / адрес QTH"), s.myQth, { vm.updateSettings(s.copy(myQth = it)) })
                        ActionButton(tr("Определить локатор"), Icons.Filled.MyLocation, vm::findMyLocator)
                        Note(tr("Расстояние и азимут считаются от QTH-локатора. Если вы сменили город, нажмите «Определить локатор»: кнопка возьмёт координаты из вашей карточки на QRZ.ru, а если их там нет, найдёт по городу."))
                        // Station defaults: copied into each new contact, where they stay as that record's own values.
                        Text(tr("Для новых записей"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        SettingField(tr("Мощность, Вт"), s.power, { vm.updateSettings(s.copy(power = it)) }, mono = true)
                        AdifLabels.MINE.forEach { (key, label) ->
                            SettingField(tr(label), s.station[key].orEmpty(), { vm.updateSettings(s.copy(station = s.station + (key to it))) })
                        }
                        Note(tr("Эти значения попадают в каждую новую связь и хранятся в ней. Если позже их поменять, старые записи не изменятся."))
                    }

                    SettingsBlock(
                        tr("Ввод связи"),
                        tr("клавиатура: %s", vm.keypad.label().lowercase()) + " · " +
                            (if (vm.timeOnSave) tr("время — при сохранении") else tr("время — при открытии карточки")),
                    ) {
                        Text(tr("Клавиатура в карточке"), style = MaterialTheme.typography.titleSmall)
                        KeypadModeRow(vm.keypad, vm::changeKeypad)
                        Note(tr("Своя клавиатура — крупные клавиши с цифрами и латиницей прямо в карточке, системная не открывается. Работает и в обычной карточке «Новый QSO» (позывной, частота, RST), и в режиме соревнований; имя, город и комментарий вводятся системной. Компактная на треть ниже, карточке остаётся больше места. Выбор запоминается. Если подключена внешняя клавиатура, своя прячется сама."))
                        if (vm.keypad != KeypadMode.OFF) {
                            KeypadHoldRow(vm.keypadHoldMs, vm::changeKeypadHoldMs)
                            Note(tr("Жесты своей клавиатуры: провести пальцем влево и, не отрывая, обратно вправо — стереть поле; нажать и удержать палец — клавиши бледнеют, ведите пальцем в сторону, чтобы передвинуть курсор между буквами (как удержание пробела на iPhone)."))
                        }
                        // The same list as F1 and the first tab of the reference: worth reading before a keyboard is attached.
                        ActionButton(tr("Список клавиш"), Icons.Filled.Keyboard) { vm.showKeys = true }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field)
                                .toggleable(value = vm.timeOnSave, role = Role.Switch, onValueChange = vm::changeTimeOnSave)
                                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr("Фиксировать время сохранения карточки QSO"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
                            Switch(checked = vm.timeOnSave, onCheckedChange = null)
                        }
                        Note(tr("Выключено: время связи — момент, когда открыта карточка «Новый QSO». Включено: в новой карточке идут часы, а время связи ставится в момент сохранения (если вы не меняли его вручную) — удобно, когда станцию долго зовут в пайл-апе. У сохранённой связи время берётся из журнала."))
                    }

                    SettingsBlock(
                        tr("Соревнования"),
                        "CONTEST MODE · " + if (vm.contestMode) tr("вкл.") + (if (vm.contestGrids) " · " + tr("обмен локаторами") else "") else tr("выкл."),
                    ) {
                        ContestModeSettings(vm)
                    }

                    SettingsBlock(tr("Диапазоны и виды связи"), tr("диапазонов: %s · видов: %s", vm.enabledBands.size, vm.enabledModes.size)) {
                        Section(tr("Диапазоны"))
                        ToggleGrid(BANDS, vm.enabledBands, vm::setBandEnabled)
                        Note(tr("Включённые диапазоны показываются кнопками при добавлении связи."))
                        Section(tr("Вид связи"))
                        ToggleGrid(MODES, vm.enabledModes, vm::setModeEnabled)
                        Note(tr("Если у записи диапазон или вид, который здесь выключен, кнопка для него в её карточке всё равно видна."))
                    }

                    PageRow(tr("Оборудование и программы"), hardwareSummary(vm), Icons.Filled.Router) { vm.settingsPage = SettingsPage.HARDWARE }
                    PageRow(tr("Справочники и онлайн-журналы"), sourcesSummary(vm), Icons.Filled.TravelExplore) { vm.settingsPage = SettingsPage.SOURCES }

                    SettingsBlock(
                        tr("Журнал связей"),
                        // Sync first: the line is cut on narrow phones, and it is the part that changes.
                        (if (vm.sheetUrl.isNotBlank()) tr("синхронизация Google: вкл.") else tr("синхронизация Google: выкл.")) + " · " + tr("записей: %s", vm.total),
                    ) {
                        ActionButton(tr("Экспорт и импорт…"), Icons.Filled.ImportExport) { vm.showTransfer = true }
                        ActionButton(tr("Выбрать часть журнала…"), Icons.Filled.FilterList) { vm.showSelect = true }
                        Note(tr("ADIF, CSV, ЕРМАК / Cabrillo и онлайн-журналы — в окне «Экспорт и импорт» (оно же в меню ⋮ журнала). «Выбрать часть журнала» отмечает записи за период, на нужных диапазонах и видах; потом нажмите «Экспорт» над журналом."))

                        SheetSyncSettings(vm)

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
                            Text(tr("Удалить всю историю QSO"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        if (confirmDeleteAll) {
                            AlertDialog(
                                onDismissRequest = { confirmDeleteAll = false },
                                icon = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                                title = { Text(tr("Удалить всю историю QSO?")) },
                                text = {
                                    Text(
                                        tr("Будут удалены все связи (%s) и голосовые заметки. Отменить это нельзя. Если нужна копия, сначала сделайте экспорт в ADIF или CSV.", vm.total),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { confirmDeleteAll = false; vm.deleteAll() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                                    ) { Text(tr("Удалить всё")) }
                                },
                                dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(tr("Отмена")) } },
                            )
                        }
                    }

                    SettingsBlock(
                        tr("Интерфейс"),
                        listOfNotNull(
                            if (vm.language == Lang.SYSTEM) I18n.current.title else vm.language.title,
                            tr("тема: %s", vm.themeMode.label.lowercase()),
                            if (vm.keepScreenOn) tr("экран не гаснет") else null,
                            tr("клавиши: %s", if (vm.keymap.isEmpty()) tr("как в программе") else tr("свои")),
                        ).joinToString(" · "),
                    ) {
                        Text(tr("Язык / Language"), style = MaterialTheme.typography.titleSmall)
                        // Same row of options as the theme; the chosen one is filled.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Lang.entries.forEach { l ->
                                val on = vm.language == l
                                Box(
                                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                                        .background(if (on) MaterialTheme.colorScheme.primary else x.field)
                                        .selectable(selected = on, role = Role.RadioButton) { vm.changeLanguage(l) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (l == Lang.SYSTEM) tr("Как в системе") else l.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                        Note(tr("Язык интерфейса") + ": Русский / English. " + tr("«Как в системе» — русский, если язык системы русский, украинский или белорусский, иначе английский."))
                        Text(tr("Тема"), style = MaterialTheme.typography.titleSmall)
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
                        Note(tr("«Как в системе» переключается вместе с тёмной темой Android."))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field)
                                .toggleable(value = vm.keepScreenOn, role = Role.Switch, onValueChange = vm::changeKeepScreenOn)
                                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr("Не выключать экран во время работы приложения"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
                            Switch(checked = vm.keepScreenOn, onCheckedChange = null)
                        }
                        Note(tr("Пока QSO-LOG на экране, телефон не гасит экран и не блокируется. Батарея расходуется быстрее."))
                        KeysSettings(vm)
                    }

                    // About: version, tap to open the project page.
                    val uri = LocalUriHandler.current
                    Column(
                        Modifier.fillMaxWidth().padding(top = 28.dp)
                            .clickable(onClickLabel = tr("Открыть страницу проекта на GitHub")) { uri.openUri(PROJECT_URL) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        QsoLogo(fontSize = 22.sp)
                        Text(tr("Версия %s", BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge, color = x.muted)
                        Text(
                            PROJECT_URL.removePrefix("https://"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AboutActions(onWhatsNew = { vm.showWhatsNew = true }) {
                            ru.r3xed.qsolog.ProblemReport.url(
                                BuildConfig.VERSION_NAME, "Android " + android.os.Build.VERSION.RELEASE,
                                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                            )
                        }
                    }
                    UpdateCheck(vm)
                    Spacer(Modifier.height(24.dp))
                }
                SettingsPage.HARDWARE -> {
                    SettingsBlock(
                        tr("CAT — трансивер"),
                        if (!vm.catConfig.enabled) tr("выключено") + " · " + Rigs.title(vm.catConfig.model)
                        else vm.catStatus.let { st -> (st as? CatStatus.Connected)?.state?.takeIf { !st.silent }?.let { Rigs.title(vm.catConfig.model) + " · " + it.short() } ?: st.describe() },
                    ) {
                        CatSettings(vm)
                    }

                    SettingsBlock(
                        tr("Цифровые виды — WSJT-X, JTDX, MSHV"),
                        if (!vm.digiConfig.enabled) tr("выключено") else vm.digiStatus.describe(),
                    ) {
                        DigiSettings(vm)
                    }

                    SettingsBlock(tr("Телеграф (CW) — ключ на DTR/RTS"), cwSummary(vm)) {
                        CwSettings(vm)
                    }

                    SettingsBlock(
                        tr("Запускать вместе с QSO-LOG"),
                        vm.startApps.count { it.enabled }.let { if (it > 0) tr("приложений: %s", it) else tr("ничего") },
                    ) {
                        StartAppsSettings(vm, onLaunchApps)
                    }
                    Spacer(Modifier.height(24.dp))
                }
                SettingsPage.SOURCES -> {
                    // Where callsign data comes from, in the order it is asked: XML API → the site → HamQTH.
                    SettingsBlock(
                        tr("Данные о корреспонденте"),
                        listOf(
                            "XML API: " + when {
                                s.qrzLogin.isBlank() -> tr("нет")
                                vm.qrzOk == true -> tr("подключено")
                                vm.qrzOk == false -> tr("ошибка")
                                else -> tr("указан")
                            },
                            tr("сайт: ") + when {
                                s.qrzSiteEmail.isBlank() -> tr("нет")
                                vm.qrzSiteOk == true -> tr("вход выполнен")
                                vm.qrzSiteOk == false -> tr("ошибка")
                                else -> tr("указан")
                            },
                            "QRZ.com: " + when {
                                s.qrzComLogin.isBlank() -> tr("нет")
                                vm.qrzComOk == true -> tr("вход выполнен")
                                vm.qrzComOk == false -> tr("ошибка")
                                else -> tr("указан")
                            },
                            "HamQTH: " + if (vm.hamqthEnabled) tr("вкл") else tr("выкл"),
                        ).joinToString(" · "),
                        open = s.qrzLogin.isBlank() && s.qrzSiteEmail.isBlank() && s.qrzComLogin.isBlank(),
                    ) {
                        Note(tr("Данные корреспондента ищутся по порядку: 1) XML API QRZ.ru — основной и правильный способ; 2) сайт QRZ.ru по вашим e-mail и паролю — запасной, если XML API не указан или не ответил; 3) сайт QRZ.com — если QRZ.ru не знает позывного (зарубежные станции) или не ответил; 4) HamQTH — только страна и область, если остальные ничего не дали."))
                        Section(tr("1. QRZ.ru — XML API (основной)"))
                        SettingField(tr("Логин"), s.qrzLogin, { vm.updateSettings(s.copy(qrzLogin = it.trim())) }, mono = true)
                        var show by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = s.qrzPassword,
                            onValueChange = { vm.updateSettings(s.copy(qrzPassword = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("Пароль")) },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { show = !show }) {
                                    Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) tr("Скрыть пароль") else tr("Показать пароль"))
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
                    ActionButton(tr("Проверить подключение"), null, vm::testQrz)
                    Note(tr("Это основной и правильный способ подключения: XML API — официальный интерфейс QRZ.ru для программ-журналов. Если указаны и эта учётная запись, и учётная запись сайта ниже, данные берутся через XML API."))
                        Section(tr("2. Сайт QRZ.ru (запасной)"))
                        Note(tr("Запасной способ, если доступа к XML API нет. Правильнее подключаться через XML API (блок выше): это официальный интерфейс для программ. Здесь программа входит на сайт qrz.ru с вашими e-mail и паролем и читает страницу позывного (www.qrz.ru/db/ПОЗЫВНОЙ): имя, город, область, RDA и то, что сайт показывает после входа."))
                        SettingField(tr("E-mail на qrz.ru"), s.qrzSiteEmail, { vm.updateSettings(s.copy(qrzSiteEmail = it.trim())) }, mono = true)
                        var showSite by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = s.qrzSitePassword,
                            onValueChange = { vm.updateSettings(s.copy(qrzSitePassword = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("Пароль от сайта")) },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                            visualTransformation = if (showSite) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showSite = !showSite }) {
                                    Icon(if (showSite) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showSite) tr("Скрыть пароль") else tr("Показать пароль"))
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        vm.qrzSiteStatus?.let { status ->
                            val color = when (vm.qrzSiteOk) {
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
                        ActionButton(tr("Проверить вход"), null, vm::testQrzSite)
                        Note(tr("Ограничения: страница сайта — не интерфейс для программ, после изменений на сайте поиск может перестать работать; каждый запрос добавляет абоненту «Просмотр»; правила сайта могут запрещать автоматические запросы. Если указана и учётная запись XML API, данные берутся через неё, а сайт — только когда XML API не ответил."))
                        Section(tr("3. Сайт QRZ.com"))
                        Note(tr("Для зарубежных позывных, которых нет на QRZ.ru. Программа входит на qrz.com с вашей учётной записью (подойдёт бесплатная) и читает страницу позывного (www.qrz.com/db/ПОЗЫВНОЙ): имя, город, страну, локатор, координаты и зоны."))
                        SettingField(tr("Логин QRZ.com (позывной или e-mail)"), s.qrzComLogin, { vm.updateSettings(s.copy(qrzComLogin = it.trim())) }, mono = true)
                        var showCom by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = s.qrzComPassword,
                            onValueChange = { vm.updateSettings(s.copy(qrzComPassword = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("Пароль QRZ.com")) },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                            visualTransformation = if (showCom) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showCom = !showCom }) {
                                    Icon(if (showCom) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showCom) tr("Скрыть пароль") else tr("Показать пароль"))
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        vm.qrzComStatus?.let { status ->
                            val color = when (vm.qrzComOk) {
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
                        ActionButton(tr("Проверить вход"), null, vm::testQrzCom)
                        Note(tr("Ограничения те же, что у сайта QRZ.ru: страница — не интерфейс для программ и может измениться; учётные записи с двухфакторным входом не поддерживаются."))
                        Section(tr("4. HamQTH — страна и область"))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field)
                                .toggleable(value = vm.hamqthEnabled, role = Role.Switch, onValueChange = vm::setHamqth)
                                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr("Страна и область по позывному"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = vm.hamqthEnabled, onCheckedChange = null)
                        }
                        Note(
                            tr("Если учётной записи QRZ.ru нет, позывного там нет или QRZ.ru не отвечает, программа спросит бесплатный справочник HamQTH.com. Он по префиксу даёт страну, область, зоны CQ и ITU и центр области — расстояние тогда примерное, со знаком ≈. Имени и точного QTH там нет. Учётная запись не нужна."),
                        )
                    }

                    SettingsBlock(tr("Онлайн-журналы: LoTW, Club Log, QRZ.com"), onlineSummary(vm.online, s.myCall, lotwReady = true)) {
                        OnlineSettings(vm.online, vm::changeOnline, computer = false)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
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
            Text(if (state == UpdateState.Checking) tr("Проверяю…") else tr("Проверить обновления"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        when (state) {
            UpdateState.UpToDate -> Text(tr("У вас последняя версия %s", BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge, color = x.ok)
            is UpdateState.Failed -> Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
    if (state is UpdateState.Available) {
        val r = state.release
        AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            icon = { Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(tr("Доступна версия %s", r.version)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Установлена %s. Что нового:", BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleSmall)
                    Text(r.notes.ifBlank { tr("Описание изменений есть на странице релиза.") }, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                // The APK opens in the browser; Android then offers to install it over the current version.
                Button(onClick = { uri.openUri(r.apkUrl ?: r.pageUrl); vm.dismissUpdate() }) { Text(tr("Скачать")) }
            },
            dismissButton = { TextButton(onClick = vm::dismissUpdate) { Text(tr("Позже")) } },
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
            Modifier.fillMaxWidth().clickable(onClickLabel = if (open) tr("Свернуть") else tr("Развернуть")) { open = !open }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("Как получить доступ?"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                tr("Войдите на qrz.ru. Ваш позывной должен быть в Callbook QRZ.ru."),
                tr("Откройте «Личные данные» → внизу раздел «XML API» → «Создать аккаунт»."),
                tr("Укажите свой позывной и программу: QSO-LOG."),
                tr("Логин и пароль для XML API придут через некоторое время. Это не пароль от сайта."),
            ).forEachIndexed { i, step ->
                Row {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                tr("Доступ дают только на постоянные позывные радиолюбителей и наблюдателей, только для личного аппаратного журнала."),
                style = MaterialTheme.typography.bodyMedium, color = x.muted,
            )
            OutlinedButton(
                onClick = { uri.openUri(QRZ_API_REQUEST_URL) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Запросить доступ на QRZ.ru"), fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(
                tr("Подробнее: qrz.ru/help/api/xml"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClickLabel = tr("Открыть справку QRZ.ru")) { uri.openUri(QRZ_API_HELP_URL) }.padding(vertical = 4.dp),
            )
        }
    }
}

internal fun settingsTitle(page: SettingsPage) = when (page) {
    SettingsPage.MAIN -> tr("Настройки")
    SettingsPage.HARDWARE -> tr("Оборудование и программы")
    SettingsPage.SOURCES -> tr("Справочники и онлайн-журналы")
}

/** What the equipment page has switched on, for its row on the main page. */
private fun hardwareSummary(vm: AppViewModel): String = listOfNotNull(
    "CAT: " + if (vm.catConfig.enabled) Rigs.title(vm.catConfig.model) else tr("выкл."),
    "WSJT-X/JTDX".takeIf { vm.digiConfig.enabled },
    "CW".takeIf { vm.cwConfig.enabled },
    vm.startApps.count { it.enabled }.takeIf { it > 0 }?.let { tr("автозапуск: %s", it) },
).joinToString(" · ")

/** The callbooks and online logs that are set up, for their row on the main page. */
private fun sourcesSummary(vm: AppViewModel): String {
    val s = vm.settings
    return listOfNotNull(
        "QRZ.ru".takeIf { s.qrzLogin.isNotBlank() || s.qrzSiteEmail.isNotBlank() },
        "QRZ.com".takeIf { s.qrzComLogin.isNotBlank() },
        "HamQTH".takeIf { vm.hamqthEnabled },
    ).joinToString(", ").ifBlank { tr("нет учётных записей") } + " · " + tr("онлайн-журналы: %s", onlineSummary(vm.online, s.myCall, lotwReady = true))
}

/** A row that opens a settings page: icon, title, what is set there, arrow. */
@Composable
internal fun PageRow(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    val x = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, x.line, RoundedCornerShape(16.dp)).background(x.field)
            .clickable(onClickLabel = tr("Открыть"), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

/** A collapsible settings block: title, one-line summary of what is set, chevron. */
@Composable
internal fun SettingsBlock(title: String, summary: String, open: Boolean = false, content: @Composable () -> Unit) {
    val x = LocalExtra.current
    var expanded by rememberSaveable(title) { mutableStateOf(open) }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, x.line, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(x.field)
                .clickable(onClickLabel = if (expanded) tr("Свернуть") else tr("Развернуть")) { expanded = !expanded }
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

/**
 * Sync through the user's Google Sheet: the steps, the Apps Script code to copy into the sheet, the script's address
 * and the button. Sync runs only when the user presses it (here or in the log's menu).
 */
@Composable
private fun SheetSyncSettings(vm: AppViewModel) {
    val x = LocalExtra.current
    val clipboard = LocalClipboardManager.current
    Section(tr("Синхронизация через Google Таблицу"))
    Note(tr("Один журнал на нескольких устройствах — телефонах, планшете, компьютере. Связи собираются в вашей Google Таблице; синхронизация — по кнопке «Синхронизировать»."))
    val steps = listOf(
        tr("Откройте на компьютере свою Google Таблицу — можно новую, пустую."),
        tr("Меню «Расширения → Apps Script». Сотрите всё в окне редактора и вставьте код скрипта: кнопка «Скопировать код скрипта» ниже кладёт его в буфер обмена."),
        tr("Нажмите «Сохранить», затем «Начать развёртывание → Новое развёртывание», тип — «Веб-приложение». Укажите «Запуск от имени: я» и «У кого есть доступ: все», нажмите «Начать развёртывание» и разрешите доступ своему аккаунту. Google предупредит, что приложение не проверено: это ваш скрипт — «Дополнительные настройки → Перейти»."),
        tr("Скопируйте «URL веб-приложения» — он заканчивается на /exec — и вставьте в поле «Адрес скрипта» ниже."),
        tr("Введите тот же адрес в QSO-LOG на других устройствах. Первая синхронизация объединит журналы, одинаковые связи склеятся. Дальше нажимайте «Синхронизировать» на любом устройстве."),
    )
    steps.forEachIndexed { i, s ->
        Row {
            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
            Text(s, style = MaterialTheme.typography.bodyMedium)
        }
    }

    // The code itself: visible, selectable, and one button to copy it whole.
    Box(
        Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(12.dp)).background(x.field)
            .verticalScroll(rememberScrollState()).padding(10.dp),
    ) {
        SelectionContainer {
            // Ligatures off: "===" must read as three characters, as it will be pasted.
            Text(SheetSync.SCRIPT, fontFamily = Mono, fontSize = 11.sp, lineHeight = 14.sp, style = LocalTextStyle.current.copy(fontFeatureSettings = "liga 0, calt 0"))
        }
    }
    ActionButton(tr("Скопировать код скрипта"), Icons.Filled.ContentCopy) {
        clipboard.setText(AnnotatedString(SheetSync.SCRIPT))
        vm.say(tr("Код скрипта скопирован: вставьте его в «Расширения → Apps Script»"))
    }

    var url by remember { mutableStateOf(vm.sheetUrl) }
    val bad = url.isNotBlank() && !SheetSync.isScriptUrl(url)
    OutlinedTextField(
        value = url,
        onValueChange = { url = it.trim(); vm.changeSheetUrl(url) },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        label = { Text(tr("Адрес скрипта (…/exec)")) },
        placeholder = { Text("https://script.google.com/macros/s/…/exec", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = bad,
        supportingText = if (bad) { { Text(tr("Нужен «URL веб-приложения» из развёртывания скрипта, а не ссылка на таблицу")) } } else null,
        trailingIcon = {
            IconButton(onClick = {
                clipboard.getText()?.text?.trim()?.let { url = it; vm.changeSheetUrl(it) }
            }) { Icon(Icons.Filled.ContentPaste, tr("Вставить из буфера")) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrect = false),
        shape = RoundedCornerShape(12.dp),
    )
    Button(
        onClick = vm::syncSheet,
        enabled = SheetSync.isScriptUrl(url) && !vm.sheetSyncing,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        if (vm.sheetSyncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Icon(Icons.Filled.Sync, null)
        Spacer(Modifier.width(8.dp))
        Text(if (vm.sheetSyncing) tr("Синхронизация…") else tr("Синхронизировать"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
    if (vm.sheetStatus.isNotBlank()) {
        Text(
            vm.sheetStatus, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            color = if (vm.sheetOk == false) MaterialTheme.colorScheme.error else if (vm.sheetOk == true) x.ok else x.muted,
        )
    }
    Note(tr("Адрес скрипта — как пароль: кто его знает, может менять журнал. Саму таблицу открывать по ссылке не нужно."))
    Note(tr("В таблице появится лист «QSO-LOG»: в первой строке — названия полей и их описание, дальше по строке на связь. Связи можно править прямо в таблице. Чтобы удалить связь на всех устройствах, поставьте Y в столбце deleted — строку не удаляйте. Голосовые заметки остаются на своём устройстве. «Удалить весь журнал» очищает только это устройство: при следующей синхронизации связи вернутся из таблицы."))
}

@Composable
internal fun Section(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = LocalExtra.current.muted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
internal fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
}

@Composable
internal fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
        if (icon != null) {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SettingField(
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

/**
 * CONTEST MODE: the switch (off at every start of the app) and, when on, what each contact sends (a serial number
 * or the same code) and which keyboard the card uses.
 */
@Composable
private fun ContestModeSettings(vm: AppViewModel) {
    val x = LocalExtra.current
    @Composable
    fun SwitchRow(text: String, on: Boolean, onChange: (Boolean) -> Unit, top: Int = 0) {
        Row(
            Modifier.fillMaxWidth().padding(top = top.dp).clip(RoundedCornerShape(12.dp))
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else x.field)
                .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = on, onCheckedChange = null)
        }
    }
    SwitchRow("CONTEST MODE", vm.contestMode, vm::changeContestMode, top = 8)
    if (vm.contestMode) {
        Text(tr("Передаю"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        // Two options in one row like the theme: a serial number or the same code every time.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to tr("Порядковый номер"), true to tr("Постоянный код")).forEach { (fixed, label) ->
                val on = vm.contestSentFixed == fixed
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else x.field)
                        .selectable(selected = on, role = Role.RadioButton) { vm.changeContestSentFixed(fixed) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (vm.contestSentFixed) {
            OutlinedTextField(
                value = vm.contestSentText,
                onValueChange = { v -> vm.changeContestSentText(v.uppercase().filter { !it.isWhitespace() }.take(12)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr("Передаваемый код")) },
                singleLine = true,
                isError = vm.contestSentText.isBlank(),
                supportingText = { Text(tr("Одинаковый в каждой связи: район, область, зона — MO69, EU, 16")) },
                textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false, keyboardType = KeyboardType.Ascii),
                shape = RoundedCornerShape(12.dp),
            )
        } else {
            // Typed as text so the field may be empty for a moment; a valid number is taken at once.
            var text by remember { mutableStateOf(vm.contestSerial.toString()) }
            LaunchedEffect(vm.contestSerial) { if (text.toIntOrNull() != vm.contestSerial) text = vm.contestSerial.toString() }
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    text = v.filter { it.isDigit() }.take(5)
                    text.toIntOrNull()?.let(vm::changeContestSerial)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr("Следующий передаваемый номер")) },
                singleLine = true,
                isError = (text.toIntOrNull() ?: 0) < 1,
                supportingText = { Text(tr("Уйдёт как %s, дальше +1 с каждой связью", ContestMode.serial(text.toIntOrNull()?.coerceAtLeast(1) ?: vm.contestSerial))) },
                textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
    SwitchRow(tr("Обмен QTH-локаторами (УКВ-соревнования)"), vm.contestGrids, vm::changeContestGrids, top = 8)
    Note(tr("В карточке появятся «Мой локатор» и «Локатор корр.». Свой берётся из «Моей станции»; если работаете с другого места, впишите его в карточке — он останется для следующих связей. Локатор корреспондента подставляется из его прошлой связи (предыдущего тура), QRZ.ru его не меняет. В отчёте ЕРМАК / Cabrillo локаторы идут после контрольных номеров, расстояние считается от вашего локатора."))
    Note(tr("Режим соревнований: «Добавить QSO» открывает упрощённую карточку — позывной, контрольные номера и RST; время ставится при записи. Принятый номер может быть любым: 015, MO69, EU, 16. Свайп справа налево — записать и открыть следующую, слева направо — вернуться к предыдущим связям контеста и поправить их. Такие связи отмечены в журнале флажком. Режим выключается при каждом запуске программы."))
}

/**
 * Contacts from WSJT-X, JTDX, MSHV on a computer in the same network: the UDP port they send to and how to set them
 * up. Each contact logged there appears in the log by itself.
 */
@Composable
private fun DigiSettings(vm: AppViewModel) {
    val x = LocalExtra.current
    val c = vm.digiConfig
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (c.enabled) MaterialTheme.colorScheme.primaryContainer else x.field)
            .toggleable(value = c.enabled, role = Role.Switch, onValueChange = { vm.changeDigi(c.copy(enabled = it)) })
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("Принимать связи из WSJT-X, JTDX, MSHV"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = c.enabled, onCheckedChange = null)
    }
    // Wi-Fi can change while the settings are open: the addresses are read again with the status.
    val ips = remember(vm.digiStatus) { localAddresses() }
    if (c.enabled) {
        val st = vm.digiStatus
        val ok = st is DigiStatus.Listening
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (ok) x.ok else if (st is DigiStatus.Failed) MaterialTheme.colorScheme.error else x.muted))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(st.describe(), style = MaterialTheme.typography.bodyMedium)
                if (ips.isNotEmpty()) Text(tr("Адрес телефона: %s", ips.joinToString(", ")), style = MaterialTheme.typography.bodyMedium, fontFamily = Mono, fontWeight = FontWeight.Bold)
            }
        }
    }
    SettingField(tr("Порт UDP"), c.port.toString(), { v -> v.filter(Char::isDigit).take(5).toIntOrNull()?.takeIf { it in 1..65535 }?.let { vm.changeDigi(c.copy(port = it)) } }, mono = true)
    SettingField(tr("Групповой адрес (multicast), если нужен"), c.group, { vm.changeDigi(c.copy(group = it.trim())) }, mono = true)
    Note(tr("WSJT-X, JTDX или MSHV на компьютере в той же сети Wi-Fi: в Settings → Reporting → UDP Server укажите адрес телефона и порт %s. Связь попадает в журнал, когда вы нажимаете OK в окне «Log QSO».", c.port))
    Note(tr("Связи принимаются, пока QSO-LOG запущен: свёрнутое надолго приложение Android может остановить. Связи, проведённые без телефона, перенесите файлом ADIF из WSJT-X (wsjtx_log.adi)."))
    Note(tr("Если UDP-порт уже слушает другая программа (JTAlert, GridTracker), укажите в WSJT-X групповой адрес, например 224.0.0.1, и тот же адрес здесь — тогда связи получат все программы."))
}

/**
 * CAT: which transceiver, how it is connected (a USB cable, Bluetooth, the network) and what the new QSO card takes
 * from it. The status line shows what the radio answers right now.
 */
@Composable
private fun CatSettings(vm: AppViewModel) {
    val x = LocalExtra.current
    val c = vm.catConfig
    val model = c.model
    @Composable
    fun SwitchRow(text: String, on: Boolean, strong: Boolean = false, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (strong && on) MaterialTheme.colorScheme.primaryContainer else x.field)
                .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = if (strong) FontWeight.Bold else null, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = on, onCheckedChange = null)
        }
    }
    @Composable
    fun <T> Choice(items: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (v, label) ->
                val on = v == selected
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else x.field)
                        .selectable(selected = on, role = Role.RadioButton) { onPick(v) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    SwitchRow(tr("Получать частоту и вид связи от трансивера (CAT)"), c.enabled, strong = true) { vm.changeCat(c.copy(enabled = it)) }
    if (c.enabled) {
        val st = vm.catStatus
        val ok = st is CatStatus.Connected && st.state != null && !st.silent
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (ok) x.ok else if (st is CatStatus.Failed || (st is CatStatus.Connected && st.silent)) MaterialTheme.colorScheme.error else x.muted))
            Spacer(Modifier.width(10.dp))
            Text(st.describe(), style = MaterialTheme.typography.bodyMedium, fontFamily = if (ok) Mono else null, modifier = Modifier.weight(1f))
        }
        ActionButton(tr("Переподключить"), Icons.Filled.Refresh, vm::restartCat)
    }

    Section(tr("Трансивер"))
    var rigMenu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { rigMenu = true }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            Text(Rigs.title(model), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = rigMenu, onDismissRequest = { rigMenu = false }, modifier = Modifier.heightIn(max = 460.dp)) {
            CatProtocol.entries.forEach { p ->
                Text(p.title, style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                Rigs.ALL.filter { it.protocol == p }.forEach { m ->
                    DropdownMenuItem(text = { Text(Rigs.title(m), fontWeight = if (m.name == c.rig) FontWeight.Bold else null) }, onClick = {
                        rigMenu = false
                        val link = when {
                            m.protocol == CatProtocol.RIGCTLD -> CatLinkType.NETWORK
                            c.link == CatLinkType.NETWORK && c.protocol == CatProtocol.RIGCTLD -> CatLinkType.USB
                            else -> c.link
                        }
                        vm.changeCat(c.copy(rig = m.name, civAddress = if (m.protocol == CatProtocol.ICOM) m.civ else c.civAddress, baud = m.baud, link = link,
                            port = if (m.protocol == CatProtocol.RIGCTLD) 4532 else c.port))
                    })
                }
            }
        }
    }
    Note(model.protocol.title)

    if (c.protocol != CatProtocol.RIGCTLD) {
        Section(tr("Подключение"))
        Choice(listOf(CatLinkType.USB to "USB", CatLinkType.BLUETOOTH to "Bluetooth", CatLinkType.NETWORK to tr("Сеть")), c.link) { vm.changeCat(c.copy(link = it)) }
    }
    when {
        c.protocol == CatProtocol.RIGCTLD || c.link == CatLinkType.NETWORK -> {
            SettingField(tr("Адрес (IP или имя)"), c.host, { vm.changeCat(c.copy(host = it.trim())) }, mono = true)
            SettingField(tr("Порт"), c.port.toString(), { v -> v.filter(Char::isDigit).take(5).toIntOrNull()?.takeIf { it in 1..65535 }?.let { vm.changeCat(c.copy(port = it)) } }, mono = true)
            Note(
                if (c.protocol == CatProtocol.RIGCTLD) tr("Hamlib rigctld на компьютере или Raspberry Pi рядом с трансивером (порт 4532), или программы с таким же сервером: wfview (4533), SDR++, flrig. Телефон и компьютер — в одной сети Wi-Fi.")
                else tr("Трансивер за сетевым CAT-адаптером или сервером последовательного порта (ser2net, ESP32, Wi-Fi-адаптер): программа шлёт команды выбранного трансивера прямо в TCP-соединение.")
            )
        }
        c.link == CatLinkType.BLUETOOTH -> {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.restartCat() }
            if (!vm.catBluetoothAllowed()) {
                ActionButton(tr("Разрешить доступ к Bluetooth"), Icons.Filled.Bluetooth) {
                    if (android.os.Build.VERSION.SDK_INT >= 31) launcher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            DeviceList(vm.catBluetoothDevices(), c.device, tr("Нет сопряжённых устройств. Включите Bluetooth на трансивере, сделайте сопряжение в настройках Android и вернитесь сюда.")) { vm.changeCat(c.copy(device = it)) }
            Note(tr("Трансивер или Bluetooth-адаптер CAT должен быть сопряжён с телефоном в настройках Android (профиль последовательного порта, SPP). Например, у трансиверов со встроенным Bluetooth включите в их меню передачу данных CI-V по Bluetooth."))
        }
        else -> {
            var tick by remember { mutableStateOf(0) }
            val devices = remember(tick) { vm.catUsbDevices() }
            DeviceList(devices, c.device, tr("USB-устройств не видно. Подключите трансивер кабелем через переходник OTG и нажмите «Обновить список».")) { vm.changeCat(c.copy(device = it)) }
            ActionButton(tr("Обновить список"), Icons.Filled.Refresh) { tick++ }
        }
    }
    if (c.protocol != CatProtocol.RIGCTLD && c.link == CatLinkType.USB) {
        Section(tr("Скорость порта, бод"))
        Choice(Rigs.BAUDS.take(3).map { it to it.toString() }, c.baud) { vm.changeCat(c.copy(baud = it)) }
        Choice(Rigs.BAUDS.drop(3).map { it to it.toString() }, c.baud) { vm.changeCat(c.copy(baud = it)) }
        Note(tr("Как в меню трансивера (у ICOM: «CI-V USB Baud Rate» или «CI-V Baud Rate»). Если там «Auto» — подойдёт любая."))
    }
    if (c.protocol == CatProtocol.ICOM) {
        var text by remember(c.civAddress) { mutableStateOf("%02X".format(c.civAddress)) }
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.uppercase().filter { it in "0123456789ABCDEF" }.take(2)
                text.toIntOrNull(16)?.takeIf { it in 1..0xDF }?.let { if (it != c.civAddress) vm.changeCat(c.copy(civAddress = it)) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("Адрес CI-V (hex)")) },
            singleLine = true,
            supportingText = { Text(tr("Заводской для %s — %sh. Если трансивер не ответит, программа сама переберёт заводские адреса.", Rigs.title(model), "%02X".format(model.civ))) },
            textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false, keyboardType = KeyboardType.Ascii),
            shape = RoundedCornerShape(12.dp),
        )
    }

    Section(tr("Что брать в новую карточку QSO"))
    SwitchRow(tr("Частоту и диапазон"), c.followFreq) { vm.changeCat(c.copy(followFreq = it)) }
    SwitchRow(tr("Вид связи"), c.followMode) { vm.changeCat(c.copy(followMode = it)) }
    SwitchRow(tr("S-метр — в RST отправленный"), c.sMeterToRst) { vm.changeCat(c.copy(sMeterToRst = it)) }
    SwitchRow(tr("Кнопки диапазона и вида в карточке перестраивают трансивер"), c.control) { vm.changeCat(c.copy(control = it)) }

    Section(tr("Для JTDX, WSJT-X и других программ"))
    SwitchRow(tr("Делиться трансивером: сервер Hamlib rigctld"), c.serve) { vm.changeCat(c.copy(serve = it)) }
    if (c.serve) {
        val st = vm.rigServerStatus
        Text(
            st.describe(), style = MaterialTheme.typography.bodyMedium, fontFamily = Mono,
            color = if (st is ru.r3xed.qsolog.cat.RigServerStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        SettingField(tr("Порт сервера"), c.servePort.toString(), { v -> v.filter(Char::isDigit).take(5).toIntOrNull()?.takeIf { it in 1..65535 }?.let { vm.changeCat(c.copy(servePort = it)) } }, mono = true)
        SwitchRow(tr("Пускать программы с других компьютеров сети"), c.serveLan) { vm.changeCat(c.copy(serveLan = it)) }
        if (c.serveLan) {
            val ips = remember(vm.rigServerStatus) { ru.r3xed.qsolog.digi.localAddresses() }
            if (ips.isNotEmpty()) Note(tr("Адрес для других компьютеров: %s, порт %s", ips.joinToString(", "), c.servePort))
        }
    }
    Note(tr("Порт трансивера держит QSO-LOG, а JTDX, WSJT-X, MSHV, GridTracker подключаются к нему как к Hamlib rigctld — выключать CAT в журнале не нужно. В JTDX: Settings → Radio → Rig «Hamlib NET rigctl», Network Server 127.0.0.1:%s, PTT Method «CAT», Split Operation «None» или «Fake It». Программа ставит частоту и вид, включает передачу, а QSO-LOG по-прежнему показывает частоту, пульт и шкалы.", c.servePort))
    Note(tr("Пока открыта новая карточка, частота, диапазон и вид связи меняются вслед за трансивером, а с включённым S-метром — и цифра S в отправленном RST (59 → 57). Поле, исправленное вручную, трансивер в этой карточке больше не трогает. Сохранённые связи не меняются. Цифровые виды трансивер не различает: в режиме DATA (USB-D) остаётся FT8, FT4 или другой цифровой вид из карточки."))

    Section(tr("Поддерживаемые трансиверы"))
    Note(tr("ICOM (протокол CI-V): IC-705, IC-7300, IC-7610, IC-9700, IC-905, IC-7100, IC-7851, IC-7800, IC-7700, IC-7600, IC-7410, IC-7200, IC-9100, IC-7000, IC-756PRO, IC-746PRO, IC-910H, IC-718, IC-706MKIIG, IC-703, IC-R8600 и другие с CI-V — адрес можно задать вручную. Совместимые с CI-V: Xiegu X6100, X6200, G90, X5105, X108G."))
    Note(tr("Другие стандарты: Kenwood (TS-590, TS-890, TS-990, TS-480, TS-2000), Elecraft (K3, K4, KX2, KX3), FlexRadio через SmartSDR CAT, Yaesu с новым CAT (FT-991, FT-891, FT-710, FTDX10, FTDX101, FTDX3000, FT-950, FT-450) и со старым (FT-817, FT-818, FT-857, FT-897), а также любой трансивер через Hamlib rigctld по сети."))
    Note(tr("Как это сделано: как в N1MM, Log4OM, WSJT-X и FT8CN — программа несколько раз в секунду спрашивает у трансивера частоту, вид связи и S-метр, а частоту, диапазон и вид меняет по вашей команде с пульта (нажмите на строку TRX в карточке) или кнопками карточки. В эфир она сама ничего не передаёт: линии DTR и RTS USB-порта держит выключенными, чтобы трансивер не перешёл на передачу. Подключение: кабель USB от трансивера к телефону через переходник OTG (у ICOM с USB-разъёмом — напрямую, у остальных — кабель CI-V/CAT с адаптером CP210x, FTDI, CH340 или PL2303), Bluetooth или сеть."))
}

@Composable
internal fun DeviceList(devices: List<ru.r3xed.qsolog.data.CatDevice>, chosen: String, empty: String, onPick: (String) -> Unit) {
    val x = LocalExtra.current
    if (devices.isEmpty()) {
        Note(empty)
        return
    }
    // Nothing chosen yet: the first device is the one the app opens.
    val current = devices.firstOrNull { it.key == chosen }?.key ?: devices.first().key
    devices.forEach { d ->
        val on = d.key == current
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else x.field)
                .selectable(selected = on, role = Role.RadioButton) { onPick(d.key) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = on, onClick = null)
            Spacer(Modifier.width(10.dp))
            Text(d.title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
