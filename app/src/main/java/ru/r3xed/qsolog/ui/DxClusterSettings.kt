package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.r3xed.qsolog.data.DxCluster
import ru.r3xed.qsolog.data.DxClusterConfig
import ru.r3xed.qsolog.tr

/** The settings block's header line: the node and the callsign it logs in with. */
fun dxSummary(c: DxClusterConfig, myCall: String): String =
    c.server + " · " + c.login.ifBlank { myCall }.ifBlank { tr("позывной не указан") }

/** The DX cluster: a node from the list or one's own, the login, tuning the radio from a spot, how long spots stay. */
@Composable
internal fun DxClusterSettings(c: DxClusterConfig, myCall: String, onChange: (DxClusterConfig) -> Unit) {
    val x = LocalExtra.current
    Section(tr("Сервер"))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field)) {
        DxCluster.SERVERS.forEach { (server, what) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = c.server == server, role = Role.RadioButton) { onChange(c.copy(server = server)) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = c.server == server, onClick = null, modifier = Modifier.padding(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(server, fontFamily = Mono, style = MaterialTheme.typography.bodyLarge)
                    Text(tr(what), style = MaterialTheme.typography.bodyMedium, color = x.muted)
                }
            }
        }
    }
    SettingField(tr("Свой сервер (адрес:порт)"), c.server, { onChange(c.copy(server = it.trim())) }, mono = true)
    SettingField(tr("Позывной для входа (пусто — мой позывной %s)", myCall.ifBlank { "—" }), c.login, { onChange(c.copy(login = it.uppercase().trim())) }, mono = true, caps = true)
    SwitchLine(tr("Перестраивать трансивер при нажатии на спот"), c.tune) { onChange(c.copy(tune = it)) }
    Section(tr("Хранить споты"))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(15, 30, 60, 120).forEach { m ->
            val on = c.keepMinutes == m
            Text(
                if (m < 60) tr("%s мин", m) else tr("%s ч", m / 60),
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) MaterialTheme.colorScheme.primary else x.field)
                    .selectable(selected = on, role = Role.RadioButton) { onChange(c.copy(keepMinutes = m)) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    Note(tr("Кластер — сеть серверов, куда радиолюбители сообщают о станциях в эфире. Программа подключается к выбранному серверу, только пока открыт экран «DX-кластер» (меню ⋮), и ещё несколько минут после — чтобы, проведя связь со станцией из списка, вернуться к нему без переподключения. Вход — по позывному, пароль не нужен. Reverse Beacon Network присылает споты автоматических приёмников (скиммеров): только CW и RTTY, зато сразу и много."))
    Note(tr("Отметки в списке сверяются с журналом: «новая страна» — с этой страной (DXCC) связей ещё не было, «новый на 20m» — не было на этом диапазоне, «новый вид» — не было этим видом (CW, телефон, цифра), «✓ в журнале» — с этим позывным на этом диапазоне связь уже есть. Нажатие на спот открывает карточку QSO с позывным, частотой и видом связи и, если включено и подключён CAT, перестраивает трансивер; долгое нажатие — только карточка."))
}
