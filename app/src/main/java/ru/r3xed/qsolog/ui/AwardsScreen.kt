package ru.r3xed.qsolog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.Screen
import ru.r3xed.qsolog.data.Award
import ru.r3xed.qsolog.data.AwardStat
import ru.r3xed.qsolog.data.Awards
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.tr

/** "Дипломы": DXCC, WAZ, WAS, regions of Russia and RDA counted from the log, worked and confirmed, by band. */
@Composable
fun AwardsScreen(vm: AppViewModel) {
    BackHandler { vm.screen = Screen.Dashboard }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen = Screen.Dashboard }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), Modifier.size(30.dp))
            }
            Text(tr("Дипломы"), style = MaterialTheme.typography.headlineSmall)
        }
        AwardsBody(vm.allQsos, onCluster = vm::openDxCluster)
    }
}

/** The award pills, the counts, the bands and what is missing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AwardsBody(all: List<Qso>, onCluster: () -> Unit) {
    val x = LocalExtra.current
    var award by rememberSaveable { mutableStateOf(Award.DXCC) }
    var showAll by remember(award) { mutableStateOf(false) }
    val stat by produceState<AwardStat?>(null, all, award) { value = withContext(Dispatchers.Default) { Awards.stat(award, all) } }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Award.entries.forEach { a -> Pill(a.title, selected = a == award) { award = a } }
    }
    val s = stat
    if (s == null || s.award != award) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(tr("Сработано"), s.worked.size, award.total, Modifier.weight(1f))
                Tile(tr("Подтверждено"), s.confirmed.size, award.total, Modifier.weight(1f))
            }
        }
        item {
            Text(
                tr("Подтверждение — бумажная QSL, LoTW или eQSL. Считается по журналу, без интернета."),
                style = MaterialTheme.typography.bodyMedium, color = x.muted,
            )
        }
        if (s.byBand.isNotEmpty()) item {
            ChartCard(tr("По диапазонам"), tr("сработано / подтверждено")) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val max = s.byBand.maxOf { it.second }.coerceAtLeast(1)
                    s.byBand.forEach { (band, w, c) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(band, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(64.dp))
                            Box(Modifier.weight(1f).height(18.dp)) {
                                Box(Modifier.fillMaxWidth(w.toFloat() / max).height(18.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primaryContainer))
                                Box(Modifier.fillMaxWidth(c.toFloat() / max).height(18.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                            }
                            Text("$w / $c", fontFamily = Mono, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp).width(80.dp))
                        }
                    }
                }
            }
        }
        if (award.total != null) item {
            ChartCard(tr("Не хватает: %s", s.missing.size), if (award == Award.DXCC) tr("страны, которых ещё нет в журнале") else null) {
                if (s.missing.isEmpty()) Text(tr("Все сработаны!"), style = MaterialTheme.typography.bodyLarge, color = x.ok)
                else {
                    val shown = if (showAll || s.missing.size <= 60) s.missing else s.missing.take(60)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        shown.forEach { k ->
                            Text(
                                Awards.label(award, k), fontSize = 14.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(x.field).padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (shown.size < s.missing.size) TextButton(onClick = { showAll = true }) { Text(tr("Показать все (%s)", s.missing.size)) }
                    TextButton(onClick = onCluster) { Text(tr("Искать в DX-кластере (отметки «новая страна», «новая область»)")) }
                }
            }
        }
        if (s.worked.isNotEmpty()) item {
            ChartCard(tr("Сработано: %s", s.worked.size), null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.worked.sorted().forEach { k ->
                        val ok = k in s.confirmed
                        Text(
                            (if (ok) "✓ " else "") + Awards.label(award, k), fontSize = 14.sp,
                            color = if (ok) x.ok else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (ok) x.okBg else x.field).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(title: String, value: Int, total: Int?, modifier: Modifier) {
    val x = LocalExtra.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(x.field).padding(14.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = x.muted)
        Text(if (total != null) "$value / $total" else "$value", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    }
}
