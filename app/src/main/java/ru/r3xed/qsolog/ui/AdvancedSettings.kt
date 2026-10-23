package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.cw.CwConfig
import ru.r3xed.qsolog.cw.CwLine
import ru.r3xed.qsolog.cw.CwVia
import ru.r3xed.qsolog.cw.CwMacros
import ru.r3xed.qsolog.cw.CwState
import ru.r3xed.qsolog.data.StartApp
import ru.r3xed.qsolog.keys.Chord
import ru.r3xed.qsolog.keys.KeyAction
import ru.r3xed.qsolog.keys.KeyMap
import ru.r3xed.qsolog.keys.KeyScope
import ru.r3xed.qsolog.tr
import androidx.compose.ui.text.style.TextAlign

@Composable
internal fun SwitchLine(text: String, on: Boolean, strong: Boolean = false, onChange: (Boolean) -> Unit) {
    val x = LocalExtra.current
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

/** The summary line of the telegraph block. */
fun cwSummary(vm: AppViewModel): String =
    if (!vm.cwConfig.enabled) tr("выключено")
    else (if (vm.cwConfig.via == CwVia.CWDAEMON) tr("cwdaemon %s · %s WPM", vm.cwConfig.daemon.ifBlank { "?" }, vm.cwConfig.wpm)
    else tr("ключ на %s · %s WPM", vm.cwConfig.line.name, vm.cwConfig.wpm)) +
        ((vm.cwState as? CwState.Failed)?.let { " · " + it.message } ?: "")

/**
 * Telegraph from the log, as in N1MM: the USB adapter and its line, the speed, the eight messages and what they may
 * hold, and how to wire the key of a Xiegu G90.
 */
@Composable
fun CwSettings(vm: AppViewModel) {
    val c = vm.cwConfig
    SwitchLine(tr("Передавать телеграфом с клавиш F1–F8"), c.enabled, strong = true) { vm.changeCw(c.copy(enabled = it)) }
    (vm.cwState as? CwState.Failed)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    Section(tr("Чем замыкать ключ"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(CwVia.SERIAL to tr("Линия адаптера"), CwVia.CWDAEMON to tr("cwdaemon по сети")).forEach { (v, title) ->
            val on = c.via == v
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else LocalExtra.current.field)
                    .selectable(selected = on, role = Role.RadioButton) { vm.changeCw(c.copy(via = v)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    if (c.via == CwVia.CWDAEMON) {
        SettingField(tr("Адрес cwdaemon: IP или имя, порт через «:» (по умолчанию 6789)"), c.daemon, { vm.changeCw(c.copy(daemon = it.trim())) }, mono = true)
        Note(tr("cwdaemon работает на компьютере, к которому подключён трансивер (Linux, Raspberry Pi: sudo apt install cwdaemon), и манипулирует ключом через свой COM- или LPT-порт. QSO-LOG передаёт ему текст сообщений и скорость по UDP, Esc останавливает передачу. Пример запуска: cwdaemon -d ttyUSB0 -p 6789. Если ключ не работает, проверьте, что порт 6789/UDP открыт в брандмауэре."))
    } else {
        Section(tr("USB-адаптер ключа"))
        var tick by remember { mutableStateOf(0) }
        val devices = remember(tick) { vm.catUsbDevices() }
        DeviceList(devices, c.device, tr("USB-адаптеров не видно. Подключите адаптер (CP2102, CH340, FTDI) через OTG и нажмите «Обновить список».")) { vm.changeCw(c.copy(device = it)) }
        ActionButton(tr("Обновить список"), Icons.Filled.Refresh) { tick++ }
        Section(tr("Линия ключа"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CwLine.entries.forEach { l ->
                val on = c.line == l
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else LocalExtra.current.field)
                        .selectable(selected = on, role = Role.RadioButton) { vm.changeCw(c.copy(line = l)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(l.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        SwitchLine(tr("Вторая линия — PTT на время передачи"), c.ptt) { vm.changeCw(c.copy(ptt = it)) }
        SwitchLine(tr("Инвертировать линию ключа"), c.invert) { vm.changeCw(c.copy(invert = it)) }
    }
    Section(tr("Скорость: %s WPM", c.wpm))
    Slider(
        value = c.wpm.toFloat(), onValueChange = { vm.changeCw(c.copy(wpm = it.toInt())) },
        valueRange = CwConfig.MIN_WPM.toFloat()..CwConfig.MAX_WPM.toFloat(), steps = CwConfig.MAX_WPM - CwConfig.MIN_WPM - 1,
    )
    Section(tr("Ключ на внешней клавиатуре"))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ru.r3xed.qsolog.cw.CwKeyMode.entries.forEach { m ->
            val on = m == c.keyMode
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else LocalExtra.current.field)
                    .selectable(on, role = Role.RadioButton) { vm.changeCw(c.copy(keyMode = m)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(m.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 15.sp, maxLines = 2,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    if (c.keyMode != ru.r3xed.qsolog.cw.CwKeyMode.OFF) {
        val roles = if (c.keyMode == ru.r3xed.qsolog.cw.CwKeyMode.STRAIGHT) listOf(0 to tr("Прямой ключ"))
        else listOf(1 to tr("Точка"), 2 to tr("Тире"))
        roles.forEach { (role, name) ->
            val keys = when (role) {
                0 -> vm.cwKeys.straight
                1 -> vm.cwKeys.dot
                else -> vm.cwKeys.dah
            }
            val learning = vm.cwLearn == role
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LocalExtra.current.field)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (learning) tr("Нажмите клавишу на внешней клавиатуре…")
                        else keys.joinToString(", ") { morseKeyLabel(it) }.ifEmpty { tr("не назначено") },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (learning) MaterialTheme.colorScheme.primary else LocalExtra.current.muted,
                    )
                }
                if (learning) TextButton(onClick = { vm.cwLearn = null }) { Text(tr("Отмена")) }
                else {
                    TextButton(onClick = { vm.cwLearn = role }) { Text(tr("Назначить")) }
                    if (keys.isNotEmpty()) TextButton(onClick = { vm.clearCwKeys(role) }) { Text(tr("Очистить")) }
                }
            }
        }
        TextButton(onClick = vm::resetCwKeys) { Text(tr("Клавиши по умолчанию")) }
    }
    Section(tr("Ключ на экране"))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ru.r3xed.qsolog.cw.CwKeyMode.entries.forEach { m ->
            val on = m == c.screenKey
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else LocalExtra.current.field)
                    .selectable(on, role = Role.RadioButton) { vm.changeCw(c.copy(screenKey = m)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(m.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 15.sp, maxLines = 2,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    Note(tr("Кнопки ключа появляются на своей клавиатуре в карточке QSO с видом CW, под рядом F1–F8: «·» и «—» для ямбического ключа или одна кнопка «КЛЮЧ». Ключ замкнут, пока палец на кнопке; на ямбическом можно нажать обе кнопки сразу."))
    SwitchLine(tr("Звук самоконтроля через динамик"), c.sidetone) { vm.changeCw(c.copy(sidetone = it)) }
    Note(tr("Ключ можно не носить с собой: пока карточка QSO в виде CW, клавиши внешней клавиатуры работают ключом. «Прямой ключ» — ключ замкнут, пока клавиша нажата. «Ямбический A/B» — электронный ключ: по умолчанию левый Ctrl или [ — точка, правый Ctrl или ] — тире (так шлют и USB-адаптеры манипуляторов), прямой ключ — любой Ctrl; в режиме B ключ дописывает знак, начатый при сжатых манипуляторах. Клавиши можно переназначить: «Назначить» и нажмите нужную клавишу. Скорость — та же, что у сообщений. Когда в карточке начинают работать ключом, под кнопками сообщений появляется лента переданных знаков. Звук через динамик телефона или компьютера — когда не слышно самоконтроля трансивера; cwdaemon принимает только текст: ему уходят готовые буквы."))
    Section(tr("Сообщения"))
    c.messages.forEachIndexed { i, m ->
        OutlinedTextField(
            value = m,
            onValueChange = { v -> vm.changeCw(c.copy(messages = c.messages.toMutableList().also { it[i] = v.uppercase() })) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(KeyMap.label(KeyAction.entries.first { it.cwMessage == i }, false).ifEmpty { tr("Сообщение %s", i + 1) }) },
            textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            shape = RoundedCornerShape(12.dp),
        )
    }
    Note(CwMacros.HELP)
    ActionButton(tr("Проверить: передать «VVV»"), Icons.Filled.PlayArrow) { vm.testCw() }
    Note(tr("Сообщения передаются, когда в карточке QSO выбран вид CW: клавиши F1–F8 (их можно переназначить в «Дополнительных настройках»), кнопки под частотой, Esc — остановить, PageUp и PageDown (или Alt+↑ и Alt+↓) — скорость. Так работает N1MM: программа замыкает ключ линией DTR или RTS USB-адаптера."))
    if (c.via == CwVia.SERIAL) {
        Note(tr("Xiegu G90 и другие трансиверы, у которых ключ замыкается на массу: у адаптера CP2102 выводы DTR и RTS при передаче опускаются в 0 В. Соедините вывод с «точкой» (или прямым ключом) гнезда KEY через транзистор или оптрон (PC817), массу — с массой. В меню трансивера выберите прямой ключ (Straight). Если ключ замкнут без передачи, включите «Инвертировать»."))
        Note(tr("Если CAT идёт через тот же адаптер (один кабель CP2102 для CI-V и ключа), выберите его и здесь: порт будет общим."))
    }
}

/** The keys of an external keyboard: any shortcut can be reassigned. */
@Composable
fun KeysSettings(vm: AppViewModel) {
    Section(tr("Клавиши внешней клавиатуры"))
    Note(tr("Любое сочетание можно заменить своим: например, F9 вместо Ctrl+N, или клавиши, к которым вы привыкли в другом логгере. Список клавиш (F1) показывает уже ваши."))
    ActionButton(tr("Назначить клавиши"), Icons.Filled.Keyboard) { vm.showKeysEditor = true }
    if (vm.keymap.isNotEmpty()) Note(tr("Изменено действий: %s", vm.keymap.size))
}

/** The apps started together with QSO-LOG. */
@Composable
fun StartAppsSettings(vm: AppViewModel, onLaunchApps: () -> Unit) {
    var pick by remember { mutableStateOf(false) }
    vm.startApps.forEach { a ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SwitchLine(a.title, a.enabled) { on -> vm.changeStartApps(vm.startApps.map { if (it.target == a.target) it.copy(enabled = on) else it }) }
            }
            IconButton(onClick = { vm.changeStartApps(vm.startApps.filter { it.target != a.target }) }) { Icon(Icons.Filled.Delete, tr("Убрать")) }
        }
    }
    ActionButton(tr("Добавить приложение"), Icons.Filled.Add) { pick = true }
    if (vm.startApps.any { it.enabled }) ActionButton(tr("Запустить сейчас"), Icons.Filled.PlayArrow, onLaunchApps)
    if (vm.startApps.isNotEmpty()) SwitchLine(tr("Сразу возвращаться в QSO-LOG"), vm.startAppsReturn, onChange = vm::changeStartAppsReturn)
    Note(tr("При запуске QSO-LOG откроет отмеченные приложения (например, FT8CN, программу для SDR или Bluetooth-адаптера) и сразу вернётся на свой экран, а они продолжат работать в фоне — их видно в списке недавних приложений. Приложения запускаются один раз — при открытии QSO-LOG, а не при каждом повороте экрана. Если телефон спрашивает разрешение на запуск или приложение не успевает открыться, выключите «Сразу возвращаться»: тогда последнее приложение останется на экране, а в QSO-LOG вы вернётесь кнопкой «Назад» или из недавних."))
    // Xiaomi, Redmi, Poco: the start of other apps needs the system's own permission.
    if (ru.r3xed.qsolog.data.Miui.isMiui) {
        val context = androidx.compose.ui.platform.LocalContext.current
        // Checked again when the user comes back from the system's settings.
        var allowed by remember { mutableStateOf(ru.r3xed.qsolog.data.Miui.backgroundStartAllowed(context)) }
        val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(owner) {
            val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = ru.r3xed.qsolog.data.Miui.backgroundStartAllowed(context)
            }
            owner.lifecycle.addObserver(o)
            onDispose { owner.lifecycle.removeObserver(o) }
        }
        if (allowed != true) {
            Note(
                (if (allowed == false) tr("Xiaomi не даёт QSO-LOG открывать другие приложения. ") else tr("На Xiaomi, Redmi и Poco нужно разрешение системы. ")) +
                    tr("Откройте разрешения QSO-LOG («Другие разрешения») и включите «Отображать всплывающие окна, когда запущено в фоновом режиме» (и «Открывать новые окна в фоновом режиме», если есть). Для запускаемых приложений в их настройках включите «Автозапуск»."),
            )
            ActionButton(tr("Открыть разрешения QSO-LOG"), Icons.Filled.Settings) { ru.r3xed.qsolog.data.Miui.openPermissions(context) }
        }
    }

    if (pick) AppPicker(vm, onPick = { a ->
        pick = false
        if (vm.startApps.none { it.target == a.target }) vm.changeStartApps(vm.startApps + a)
    }, onDismiss = { pick = false })
}

@Composable
private fun AppPicker(vm: AppViewModel, onPick: (StartApp) -> Unit, onDismiss: () -> Unit) {
    val apps = remember { vm.launchableApps() }
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Какое приложение запускать")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("Поиск")) })
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps.filter { filter.isBlank() || it.title.contains(filter, ignoreCase = true) }, key = { it.target }) { a ->
                        Text(
                            a.title, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

/** Every action with its keys, by screen; a tap on one asks for the new key. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeysEditor(vm: AppViewModel, onClose: () -> Unit) {
    val x = LocalExtra.current
    var editing by remember { mutableStateOf<KeyAction?>(null) }
    // A screen of its own over the settings (drawn by MainActivity), Back closes it.
    androidx.activity.compose.BackHandler(onBack = onClose)
    run {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад")) }
                    Text(tr("Клавиши"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (vm.keymap.isNotEmpty()) TextButton(onClick = { vm.changeKeymap(emptyMap()) }) { Text(tr("Сбросить все")) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Note(tr("Нажмите на действие и затем новое сочетание на внешней клавиатуре. Стрелки, Tab, Enter и Пробел в списках, Alt+1…9 работают как прежде."))
                    KeyScope.entries.forEach { scope ->
                        Section(scope.title)
                        KeyAction.entries.filter { it.scope == scope }.forEach { a ->
                            val chords = KeyMap.chords(a)
                            val changed = a in vm.keymap
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field)
                                    .clickable { editing = a }.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(a.title, style = MaterialTheme.typography.bodyLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    if (chords.isEmpty()) Text(tr("нет клавиши"), color = x.muted)
                                    chords.forEach { ch ->
                                        Text(
                                            ch.label(false), fontFamily = Mono, fontWeight = FontWeight.Bold,
                                            color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.border(1.dp, x.line, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    editing?.let { a -> KeyCapture(vm, a) { editing = null } }
}

/** Waits for a key of the external keyboard; then replace, add, or take it off; or back to the defaults. */
@Composable
private fun KeyCapture(vm: AppViewModel, a: KeyAction, onClose: () -> Unit) {
    var chord by remember { mutableStateOf<Chord?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val refusal = chord?.let { KeyMap.refusal(a, it) }
    val taken = chord?.let { KeyMap.conflicts(a, it) }.orEmpty()
    fun set(list: List<Chord>) {
        vm.changeKeymap(KeyMap.assign(vm.keymap, a, list))
        onClose()
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(a.title) },
        text = {
            Column(
                Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    e.chord()?.let { chord = it }
                    true
                },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(tr("Сейчас: %s", KeyMap.label(a, false).ifEmpty { tr("нет клавиши") }), style = MaterialTheme.typography.bodyLarge)
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(LocalExtra.current.field),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(chord?.label(false) ?: tr("нажмите сочетание…"), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                refusal?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (refusal == null && taken.isNotEmpty())
                    Text(tr("Сейчас эта клавиша — «%s»: там она будет снята.", taken.joinToString("», «") { it.title }), color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { set(emptyList()) }) { Text(tr("Без клавиши")) }
                    TextButton(onClick = { set(a.defaults) }) { Text(tr("Как было")) }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { chord?.let { set(KeyMap.chords(a) + it) } }, enabled = chord != null && refusal == null) { Text(tr("Добавить")) }
                TextButton(onClick = { chord?.let { set(listOf(it)) } }, enabled = chord != null && refusal == null) { Text(tr("Заменить")) }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Отмена")) } },
    )
}
