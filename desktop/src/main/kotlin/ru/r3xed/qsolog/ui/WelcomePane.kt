package ru.r3xed.qsolog.ui

import ru.r3xed.qsolog.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.data.Geo

/**
 * First start: callsign and where the station is — two short steps, then the log. Station data sources (QRZ.ru,
 * QRZ.com) are offered later, where they help: the card says so when a station is found by prefix only.
 * Everything is saved as it is typed; "Настроить позже" leaves at any step, the settings have the same fields.
 */
@Composable
fun WelcomePane(vm: AppState) {
    val s = vm.settings
    val x = LocalExtra.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            QsoLogo(fontSize = 34.sp)
            Text(tr("Шаг %s из 2", step + 1), style = MaterialTheme.typography.titleSmall, color = x.muted)
            when (step) {
                0 -> {
                    Text(tr("Ваш позывной"), style = MaterialTheme.typography.headlineSmall)
                    Text(tr("Он попадёт в каждую связь и в файлы ADIF."), style = MaterialTheme.typography.bodyLarge, color = x.muted)
                    SettingField(tr("Мой позывной"), s.myCall, { vm.updateSettings(s.copy(myCall = it.uppercase().trim())) }, mono = true, caps = true)
                }
                else -> {
                    Text(tr("Где вы находитесь"), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        tr("От QTH-локатора считаются расстояние и азимут до абонента. Не знаете локатор — впишите город и нажмите «Определить по городу»."),
                        style = MaterialTheme.typography.bodyLarge, color = x.muted,
                    )
                    val locOk = s.myLocator.isBlank() || Geo.isLocator(s.myLocator)
                    SettingField(
                        tr("QTH-локатор (например KO85ts)"), s.myLocator, { vm.updateSettings(s.copy(myLocator = it.trim())) },
                        mono = true, error = if (locOk) null else tr("Локатор: 2 буквы, 2 цифры, 2 буквы"),
                    )
                    SettingField(tr("Город / адрес QTH"), s.myQth, { vm.updateSettings(s.copy(myQth = it)) })
                    OutlinedButton(onClick = vm::findMyLocator, shape = RoundedCornerShape(12.dp)) { Text(tr("Определить по городу"), fontSize = 17.sp) }
                    Text(
                        tr("Имя и город корреспондента программа может брать с QRZ.ru или бесплатного QRZ.com — подключите их потом, карточка связи подскажет. Пока страну и область даст HamQTH, без регистрации."),
                        style = MaterialTheme.typography.bodyMedium, color = x.muted,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton(onClick = { step-- }) { Text(tr("Назад"), fontSize = 17.sp) }
                Spacer(Modifier.weight(1f))
                if (step < 1) {
                    Button(
                        onClick = { step++ },
                        enabled = step != 0 || s.myCall.length >= 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp),
                    ) { Text(tr("Далее"), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                } else {
                    Button(onClick = vm::finishWelcome, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(52.dp)) {
                        Text(tr("Готово"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            TextButton(onClick = vm::finishWelcome) { Text(tr("Настроить позже"), fontSize = 16.sp, color = x.muted) }
        }
    }
}
