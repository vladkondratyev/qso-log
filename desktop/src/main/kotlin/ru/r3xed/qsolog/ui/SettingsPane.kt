package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.Pane
import ru.r3xed.qsolog.data.Geo

@Composable
fun SettingsPane(vm: AppState, onExport: () -> Unit, onImport: () -> Unit) {
    val s = vm.settings
    val x = LocalExtra.current

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.pane = Pane.Empty }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", Modifier.size(30.dp))
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
            Note("Пароль хранится в системной связке ключей (Связка ключей macOS или Диспетчер учётных данных Windows).")

            Section("Лог QSO · записей: ${vm.total}")
            ActionButton("Экспорт лога в CSV", Icons.Filled.FileUpload, onExport)
            ActionButton("Импорт лога из CSV", Icons.Filled.FileDownload, onImport)
            Note("В файл попадают все поля каждой связи: дата и время UTC, диапазон, частота, вид, RST, имя, QTH, локатор, координаты, расстояние, азимут, мощность, QSL, комментарий. При импорте повторы пропускаются. Разделитель «;», файл открывается в Excel.")
            Spacer(Modifier.height(24.dp))
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
