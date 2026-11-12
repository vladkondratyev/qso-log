package ru.r3xed.qsolog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.Screen
import ru.r3xed.qsolog.data.Beacons
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.Propagation
import ru.r3xed.qsolog.data.PropagationData
import ru.r3xed.qsolog.data.Sun
import ru.r3xed.qsolog.tr

/** "Прохождение": the conditions from free services, all seen from the QTH of the station in work. */
@Composable
fun PropagationScreen(vm: AppViewModel) {
    BackHandler { vm.screen = Screen.Dashboard }
    LaunchedEffect(Unit) { vm.refreshPropagation() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen = Screen.Dashboard }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), Modifier.size(30.dp))
            }
            Text(tr("Прохождение"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (vm.propLoading) CircularProgressIndicator(Modifier.padding(end = 12.dp).size(24.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { vm.refreshPropagation(force = true) }) { Icon(Icons.Filled.Refresh, tr("Обновить")) }
        }
        PropagationBody(
            vm.prop, vm.settings.myLocator, vm.settings.myCall,
            onTune = if (vm.rigControllable) { khz -> vm.tuneBeacon(khz) } else null,
            onMap = { vm.openMap() },
            onSettings = { vm.openSettings(stations = true) },
        )
    }
}

/** The bands as the card knows them, with a frequency in each, for "below the MUF". */
private val BAND_MHZ = listOf("160m" to 1.8, "80m" to 3.5, "60m" to 5.3, "40m" to 7.0, "30m" to 10.1, "20m" to 14.0, "17m" to 18.1, "15m" to 21.0, "12m" to 24.9, "10m" to 28.0, "6m" to 50.0)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PropagationBody(d: PropagationData, myLocator: String, myCall: String, onTune: ((Double) -> Unit)?, onMap: () -> Unit, onSettings: () -> Unit) {
    val x = LocalExtra.current
    val qth = Geo.locatorToLatLon(myLocator)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // --- the QTH first: everything below is seen from it ---
        item {
            if (qth == null) {
                ChartCard(tr("QTH станции не указан"), null) {
                    Text(tr("Прохождение считается от QTH-локатора станции в работе: день или ночь у вас, ближайший ионозонд, поглощение, что слышно в вашем квадрате, направления на маяки."), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onSettings) { Text(tr("Указать QTH-локатор")) }
                }
            } else {
                val a = remember(d, qth, now / 60_000) { Propagation.atQth(d, qth, now) }
                ChartCard(tr("У вас: %s", myLocator.uppercase().take(4) + myLocator.drop(4).lowercase()) + (myCall.ifBlank { null }?.let { " · $it" } ?: ""), null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val change = a.changeInMin?.let { m -> tr("%s ч %s мин", m / 60, m % 60) }
                        Line(
                            if (a.night) "☾ " + tr("Ночь") else "☀ " + tr("День"),
                            change?.let { if (a.night) tr("восход через %s", it) else tr("закат через %s", it) } ?: if (a.night) tr("полярная ночь") else tr("полярный день"),
                        )
                        a.ionosonde?.let { io ->
                            val open = BAND_MHZ.filter { it.second < io.mufMhz }.map { it.first }
                            Line(
                                "MUF " + "%.1f".format(io.mufMhz) + " " + tr("МГц"),
                                tr("ионозонд %s, %s от вас", io.name, formatKm(a.ionosondeKm ?: 0.0)) + (open.lastOrNull()?.let { " · " + tr("дальние трассы — до %s", it) } ?: ""),
                            )
                        } ?: Line("MUF", tr("ионозондов рядом нет"))
                        if (a.absorptionMhz > 0) Line(tr("Поглощение"), tr("до %s МГц: нижние диапазоны глохнут", "%.0f".format(a.absorptionMhz)), warn = a.absorptionMhz >= 10)
                        if (a.auroraPct >= 10) Line(tr("Сияние"), tr("вероятность %s%% рядом с вами: трассы через север ослаблены, на УКВ возможна аврора", a.auroraPct), warn = true)
                        val strong = d.flares.firstOrNull { it.strong && now - it.time < 3_600_000L }
                        if (strong != null && !a.night) Line(tr("Вспышка"), tr("%s в %s UTC: возможен провал КВ на дневной стороне", strong.cls, utcHm(strong.time)), warn = true)
                    }
                }
            }
        }
        // --- N0NBH's verdict, the column of the QTH's day or night outlined ---
        if (d.bands.isNotEmpty()) item {
            val night = qth?.let { Sun.isNight(it, now) }
            ChartCard(tr("Диапазоны"), tr("оценка N0NBH (hamqsl.com); выделено то, что у вас сейчас")) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row { Text("", Modifier.weight(1.2f)); Text(tr("день"), Modifier.weight(1f), color = x.muted); Text(tr("ночь"), Modifier.weight(1f), color = x.muted) }
                    d.bands.forEach { b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.bands, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1.2f))
                            Verdict(b.day, night == false, Modifier.weight(1f).padding(end = 6.dp))
                            Verdict(b.night, night == true, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        // --- the numbers ---
        d.solar?.let { s ->
            item {
                ChartCard(tr("Солнце и магнитное поле"), s.updated.ifBlank { null }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Line("SFI", s.flux + " · " + tr("пятен: %s", s.sunspots))
                        val k = s.kIndex.trim().toDoubleOrNull()
                        Line("K / A", s.kIndex.trim() + " / " + s.aIndex.trim() + " · " + when {
                            k == null -> ""
                            k >= 5 -> tr("магнитная буря")
                            k >= 4 -> tr("возмущено")
                            else -> tr("спокойно")
                        }, warn = (k ?: 0.0) >= 5)
                        if (s.xray.isNotBlank()) Line(tr("Рентген"), s.xray)
                    }
                }
            }
        }
        if (d.kp.isNotEmpty()) item {
            ChartCard(tr("Kp: сутки назад и прогноз на 3 дня"), tr("NOAA SWPC; бледные столбцы — прогноз, 5 и выше — буря")) {
                KpBars(d.kp.filter { it.time > now - 86_400_000L }, now)
            }
        }
        if (d.flux.size > 2) item {
            val last = d.flux.takeLast(30)
            ChartCard(tr("Поток F10.7 за 30 дней"), tr("сейчас %s · от %s до %s", last.last().second.toInt(), last.minOf { it.second }.toInt(), last.maxOf { it.second }.toInt())) {
                Spark(last.map { it.second })
            }
        }
        val dayFlares = d.flares.filter { now - it.time < 86_400_000L }
        if (dayFlares.isNotEmpty()) item {
            ChartCard(tr("Вспышки за сутки"), tr("классы M и X глушат КВ на дневной стороне Земли")) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayFlares.take(30).forEach { f ->
                        Text(
                            utcHm(f.time) + " " + f.cls, fontFamily = Mono, fontSize = 14.sp,
                            color = if (f.strong) x.hlInk else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (f.strong) x.hl else x.field).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        // --- what is really heard: PSK Reporter ---
        if (qth != null) item {
            val act = remember(d.heardHere, qth) { Propagation.activity(d.heardHere, qth) }
            ChartCard(tr("Что слышно у вас"), tr("приёмники в квадрате %s за 15 мин (PSK Reporter): сколько станций с каждого направления", myLocator.take(2).uppercase())) {
                if (act.isEmpty()) Text(tr("Данных нет — PSK Reporter ещё не ответил или в квадрате нет приёмников."), color = x.muted)
                else Heat(act)
            }
        }
        if (qth != null) item {
            val heard = remember(d.heardMe, qth) { Propagation.heardMeByBand(d.heardMe, qth) }
            ChartCard(tr("Кто вас слышит"), tr("ваш позывной за час (PSK Reporter: FT8, FT4, CW, RTTY…)")) {
                if (heard.isEmpty()) Text(tr("Вас пока никто не отметил: передайте CQ в цифре или телеграфом и обновите через 5 минут."), color = x.muted)
                else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    heard.forEach { (band, n, far) -> Line(band, tr("приёмников: %s · дальше всех — %s", n, formatKm(far))) }
                    TextButton(onClick = onMap) { Icon(Icons.Filled.Map, null); Text(tr("Показать на карте"), Modifier.padding(start = 6.dp)) }
                }
            }
        }
        // --- the beacons, now ---
        item {
            ChartCard(tr("Маяки NCDXF сейчас"), tr("каждый маяк — 10 с на диапазоне, слышно — трасса открыта") + if (onTune != null) " · " + tr("нажмите, чтобы настроить трансивер") else "") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Beacons.now(now).forEach { (khz, b, left) ->
                        val p = Geo.locatorToLatLon(b.locator)
                        val info = listOfNotNull(
                            b.place,
                            if (qth != null && p != null) formatKm(Geo.distanceKm(qth, p)) + " · " + "%.0f°".format(Geo.bearing(qth, p)) else null,
                            p?.let { if (Sun.isNight(it, now)) tr("там ночь") else tr("там день") },
                        ).joinToString(" · ")
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).then(if (onTune != null) Modifier.clickable { onTune(khz) } else Modifier).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("%.3f".format(khz / 1000), fontFamily = Mono, fontSize = 15.sp, modifier = Modifier.width(72.dp))
                            Text(b.call, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(80.dp))
                            Text(info, style = MaterialTheme.typography.bodyMedium, color = x.muted, modifier = Modifier.weight(1f), maxLines = 2)
                            Text("${left}s", fontFamily = Mono, fontSize = 13.sp, color = x.muted)
                        }
                    }
                }
            }
        }
        if (d.vhf.isNotEmpty()) item {
            ChartCard(tr("УКВ"), tr("аврора и спорадический E (N0NBH)")) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { d.vhf.forEach { (what, v) -> val (t, text) = Propagation.vhfText(what, v); Line(t, text, warn = !v.contains("Closed", true)) } }
            }
        }
        item {
            Text(
                tr("Источники: hamqsl.com (N0NBH), NOAA SWPC, prop.kc2g.com (ионозонды GIRO), PSK Reporter, расписание NCDXF/IARU. Данные обновляются сами: солнце раз в 30 минут, ионозонды и поглощение раз в 15, PSK Reporter не чаще раза в 5 минут.") +
                    (if (d.problems.isNotEmpty()) "\n" + tr("Не ответили: %s", d.problems.keys.joinToString(", ")) else ""),
                style = MaterialTheme.typography.bodySmall, color = x.muted,
            )
        }
    }
}

private fun utcHm(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).let { "%02d:%02d".format(it.hour, it.minute) }

@Composable
private fun Line(label: String, value: String, warn: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(130.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

/** Poor / Fair / Good as a coloured chip; the column of the QTH's day or night is outlined. */
@Composable
private fun Verdict(v: String, now: Boolean, modifier: Modifier) {
    val x = LocalExtra.current
    val (bg, fg, text) = when (v.lowercase()) {
        "good" -> Triple(x.okBg, x.ok, tr("хорошо"))
        "fair" -> Triple(x.hl, x.hlInk, tr("средне"))
        "poor" -> Triple(x.field, x.muted, tr("плохо"))
        else -> Triple(x.field, x.muted, v)
    }
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .then(if (now) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Kp by 3 hours: green below 4, amber 4–5, red from 5; the forecast paler. */
@Composable
private fun KpBars(kp: List<ru.r3xed.qsolog.data.KpPoint>, now: Long) {
    val x = LocalExtra.current
    val ok = x.ok; val mid = x.hlBorder; val bad = MaterialTheme.colorScheme.error; val line = x.line
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        if (kp.isEmpty()) return@Canvas
        val w = size.width / kp.size
        kp.forEachIndexed { i, p ->
            val h = (p.kp / 9.0 * size.height).toFloat().coerceAtLeast(2f)
            val c = when { p.kp >= 5 -> bad; p.kp >= 4 -> mid; else -> ok }
            drawRect(if (p.observed || p.time <= now) c else c.copy(alpha = 0.4f), Offset(i * w + 1, size.height - h), Size(w - 2, h))
        }
        val y5 = (size.height - 5 / 9.0 * size.height).toFloat()
        drawLine(line, Offset(0f, y5), Offset(size.width, y5), strokeWidth = 1f)
    }
}

@Composable
private fun Spark(values: List<Double>) {
    val c = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(60.dp)) {
        val min = values.min(); val max = values.max().coerceAtLeast(min + 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val px = i * size.width / (values.size - 1)
            val py = (size.height - (v - min) / (max - min) * size.height).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, c, style = Stroke(width = 3f))
    }
}

/** Bands × eight directions from the QTH; the deeper the colour, the more stations heard from there. */
@Composable
private fun Heat(rows: List<Pair<String, IntArray>>) {
    val x = LocalExtra.current
    val max = rows.maxOf { it.second.max() }.coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Text("", Modifier.width(48.dp))
            Propagation.SECTORS.forEach { Text(it, Modifier.weight(1f), fontSize = 12.sp, color = x.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        rows.forEach { (band, cells) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(band, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(48.dp))
                cells.forEach { n ->
                    Box(
                        Modifier.weight(1f).height(26.dp).padding(1.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (n == 0) x.field else accent.copy(alpha = (0.2f + 0.8f * n / max).coerceIn(0f, 1f))),
                        contentAlignment = Alignment.Center,
                    ) { if (n > 0) Text("$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (n * 2 > max) Color.White else MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}
