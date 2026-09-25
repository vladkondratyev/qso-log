package ru.r3xed.qsolog.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.TooltipArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.Pane
import ru.r3xed.qsolog.TIME_FMT
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.utc
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogPane(state: AppState, shortcut: String, modifier: Modifier = Modifier) {
    val x = LocalExtra.current
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Лог QSO", style = MaterialTheme.typography.headlineMedium)
                val me = state.settings.myCall
                Text((if (me.isNotBlank()) "$me · " else "") + "записей: ${state.total}", style = MaterialTheme.typography.bodyMedium, color = x.muted)
            }
            FilledTonalIconButton(onClick = { state.pane = Pane.Settings }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки", modifier = Modifier.size(28.dp))
            }
        }

        Button(
            onClick = state::newQso,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("Новая связь", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(shortcut, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = state::search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text("Поиск: позывной, имя, город", style = MaterialTheme.typography.bodyLarge) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { state.search("") }) { Icon(Icons.Filled.Clear, "Очистить поиск") }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = x.field, focusedContainerColor = x.field,
                unfocusedBorderColor = Color.Transparent,
            ),
        )

        if (state.qsos.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.query.isNotBlank()) {
                    Text("Ничего не найдено", style = MaterialTheme.typography.titleLarge)
                    Text("Проверьте написание или очистите поиск.", style = MaterialTheme.typography.bodyLarge, color = x.muted)
                } else {
                    Text("Лог пока пуст", style = MaterialTheme.typography.titleLarge)
                    Text("Нажмите «Новая связь», чтобы записать первое QSO. Старый лог можно загрузить из CSV в меню «Файл».", style = MaterialTheme.typography.bodyLarge, color = x.muted)
                }
            }
        } else {
            val grouped = remember(state.qsos) { state.qsos.groupBy { utc(it.timeUtc).toLocalDate() } }
            val listState = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(end = 8.dp),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grouped.forEach { (day, list) ->
                        stickyHeader(key = "d$day") { DayHeader(day, list.size) }
                        items(list, key = { it.id }) { qso ->
                            QsoRow(qso, selected = state.pane == Pane.Edit && state.form.id == qso.id, onClick = { state.edit(qso) }, onDelete = { state.delete(qso) })
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, count: Int) {
    val today = LocalDate.now(ZoneOffset.UTC)
    val label = when (day) {
        today -> "Сегодня, ${DATE_FMT.format(day)}"
        today.minusDays(1) -> "Вчера, ${DATE_FMT.format(day)}"
        else -> DATE_FMT.format(day)
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        Text("$count QSO", style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QsoRow(qso: Qso, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else x.line, shape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(qso.call, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(TIME_FMT.format(utc(qso.timeUtc)) + " UTC", fontFamily = Mono, fontSize = 16.sp, color = x.muted)
            }
            val meta = listOfNotNull(
                qso.freqMhz.ifBlank { null } ?: qso.band.ifBlank { null },
                qso.mode.ifBlank { null },
                if (qso.rstSent.isNotBlank() || qso.rstRcvd.isNotBlank()) "${qso.rstSent} / ${qso.rstRcvd}" else null,
                qso.distanceKm?.let { formatKm(it) },
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            val who = listOf(qso.name, qso.qth).filter { it.isNotBlank() }.joinToString(", ")
            if (who.isNotEmpty()) Text(who, style = MaterialTheme.typography.bodyLarge, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TooltipArea(tooltip = {
            Text("Удалить", Modifier.background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(6.dp)).padding(8.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
        }) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, "Удалить связь с ${qso.call}", tint = x.muted) }
        }
    }
}
