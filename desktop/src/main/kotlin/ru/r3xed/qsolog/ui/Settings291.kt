package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.SettingsIndex
import ru.r3xed.qsolog.cat.CatStatus
import ru.r3xed.qsolog.data.ActivationProgram
import ru.r3xed.qsolog.data.BackupPeriod
import ru.r3xed.qsolog.tr

/** The radio does not answer or the link failed: the CAT block and its page get the red dot. */
internal fun catAlert(vm: AppState): Boolean = vm.catConfig.enabled &&
    vm.catStatus.let { it is CatStatus.Failed || (it is CatStatus.Connected && it.silent) }

/** A callbook login was refused: the sources block and its page get the red dot. */
internal fun sourcesAlert(vm: AppState): Boolean = vm.qrzOk == false || vm.qrzSiteOk == false || vm.qrzComOk == false

/** "Поиск по настройкам" at the top of the settings: the matching blocks, a tap opens one. */
@Composable
internal fun SettingsSearch(vm: AppState) {
    val x = LocalExtra.current
    OutlinedTextField(
        value = vm.settingsQuery, onValueChange = { vm.settingsQuery = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text(tr("Поиск по настройкам: масштаб, CAT, LoTW…")) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = if (vm.settingsQuery.isNotEmpty()) { { IconButton(onClick = { vm.settingsQuery = "" }) { Icon(Icons.Filled.Close, tr("Очистить")) } } } else null,
        singleLine = true, shape = RoundedCornerShape(14.dp),
    )
    if (vm.settingsQuery.isNotBlank()) {
        val hits = SettingsIndex.search(vm.settingsQuery)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(x.field)) {
            if (hits.isEmpty()) Text(tr("Ничего не найдено"), color = x.muted, modifier = Modifier.padding(14.dp))
            hits.forEach { e ->
                Column(Modifier.fillMaxWidth().clickable { vm.openSettingsBlock(e) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(tr(e.block), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        when (e.page) {
                            ru.r3xed.qsolog.SettingsPage.MAIN -> tr("Настройки")
                            ru.r3xed.qsolog.SettingsPage.HARDWARE -> tr("Оборудование и программы")
                            ru.r3xed.qsolog.SettingsPage.SOURCES -> tr("Справочники и онлайн-журналы")
                        },
                        style = MaterialTheme.typography.bodyMedium, color = x.muted,
                    )
                }
            }
        }
    }
}

/** A row of options, the chosen one filled (as the theme and the scale). */
@Composable
internal fun <T> OptionRow(options: List<T>, chosen: T, label: (T) -> String, onPick: (T) -> Unit) {
    val x = LocalExtra.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == chosen
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else x.field)
                    .selectable(selected = on, role = Role.RadioButton) { onPick(o) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** "Автокопия журнала": the period, the last copy, "Сделать копию сейчас". */
@Composable
internal fun BackupSettings(vm: AppState) {
    Section(tr("Автокопия журнала"))
    OptionRow(BackupPeriod.entries, vm.backupPeriod, { it.title }, vm::changeBackupPeriod)
    ActionButton(tr("Сделать копию сейчас"), Icons.Filled.Backup) { vm.backupNow() }
    ActionButton(tr("Открыть папку копий"), null) { vm.openBackupFolder() }
    val last = vm.backupLast.takeIf { it > 0 }?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it)) }
    Note(
        (last?.let { tr("Последняя копия: %s.", it) + " " } ?: "") +
            tr("Весь журнал в ADIF (UTF-8) сохраняется при запуске программы, если прошлая копия старше выбранного срока. Хранятся последние %s копий в папке данных программы (backups). Восстановить: «Файл» → «Импорт лога из ADIF».", ru.r3xed.qsolog.data.Backups.KEEP),
    )
}

/** "Выезд и активация": POTA, WWFF, SOTA, IOTA, RDA — the program, the reference, start and end. */
@Composable
internal fun ActivationSettings(vm: AppState) {
    val a = vm.activation
    if (a != null) {
        Text(tr("Идёт активация %s · связей: %s", a.title, vm.activationCount), style = MaterialTheme.typography.titleSmall, color = LocalExtra.current.ok)
        ActionButton(tr("Завершить и выгрузить ADIF"), Icons.Filled.Flag) { vm.stopActivation(export = true) }
        ActionButton(tr("Завершить без выгрузки"), null) { vm.stopActivation(export = false) }
        Note(tr("Каждая новая связь получает поля MY_SIG, MY_SIG_INFO и поле своей программы (MY_POTA_REF, MY_WWFF_REF, MY_SOTA_REF, MY_IOTA, MY_CNTY). Над журналом — строка активации со счётчиком."))
        return
    }
    var program by remember { mutableStateOf(ActivationProgram.POTA) }
    var ref by remember { mutableStateOf("") }
    var sig by remember { mutableStateOf("") }
    OptionRow(ActivationProgram.entries.take(3), program, { it.title }) { program = it }
    OptionRow(ActivationProgram.entries.drop(3), program, { if (it == ActivationProgram.OTHER) tr("Другое") else it.title }) { program = it }
    if (program == ActivationProgram.OTHER) SettingField(tr("Программа (MY_SIG), например RAFA"), sig, { sig = it.uppercase() }, mono = true, caps = true)
    SettingField(
        when (program) {
            ActivationProgram.POTA -> tr("Парк, например RU-0001")
            ActivationProgram.WWFF -> tr("Территория, например RFF-0001")
            ActivationProgram.SOTA -> tr("Вершина, например R3/KG-001")
            ActivationProgram.IOTA -> tr("Остров, например EU-133")
            ActivationProgram.RDA -> tr("Район RDA, например KG-15")
            ActivationProgram.OTHER -> tr("Референс")
        },
        ref, { ref = it.uppercase() }, mono = true, caps = true,
    )
    ActionButton(tr("Начать активацию"), Icons.Filled.Flag) { if (ref.isNotBlank()) vm.startActivation(program, ref, sig) }
    Note(tr("Выезд с активацией парка, вершины, острова или района: новые связи получают ваш референс, над журналом видно число связей, а «Завершить» выгружает связи активации в ADIF для загрузки на сайт программы."))
}

/** DX cluster notifications: the rules and the watch list; shown by the system (Notification Center, the Windows toast). */
@Composable
internal fun DxAlertSettings(vm: AppState) {
    val c = vm.dxAlerts
    Section(tr("Уведомления о спотах"))
    SwitchRow(tr("Уведомлять о спотах"), c.enabled) { vm.changeDxAlerts(c.copy(enabled = it)) }
    if (c.enabled) {
        SwitchRow(tr("Новая страна"), c.newCountry) { vm.changeDxAlerts(c.copy(newCountry = it)) }
        SwitchRow(tr("Новая область России"), c.newRegion) { vm.changeDxAlerts(c.copy(newRegion = it)) }
        SwitchRow(tr("Новый диапазон для страны"), c.newBand) { vm.changeDxAlerts(c.copy(newBand = it)) }
        var watch by remember { mutableStateOf(c.watch.joinToString(" ")) }
        SettingField(tr("Список ожидания: позывные через пробел, * — любые буквы"), watch, { watch = it.uppercase(); vm.changeDxAlerts(c.copy(watch = ru.r3xed.qsolog.data.DxAlerts.parseWatch(it))) }, mono = true)
        Note(tr("Пока уведомления включены, кластер остаётся на связи, даже когда его окно закрыто. Уведомление показывает система; один и тот же позывной на диапазоне — не чаще раза в 30 минут."))
    }
}

@Composable
private fun SwitchRow(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LocalExtra.current.field)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = on, onCheckedChange = null)
    }
}
