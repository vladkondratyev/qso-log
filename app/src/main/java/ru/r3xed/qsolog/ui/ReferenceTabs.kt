package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.r3xed.qsolog.data.Cty
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.Reference
import ru.r3xed.qsolog.data.RfCalc
import ru.r3xed.qsolog.data.RussianRegions
import ru.r3xed.qsolog.tr
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Tabs of the reference screen past the first three: band plan and activity, codes, levels, cables, antennas,
// prefixes, propagation and time. The same file serves Android and the desktop.

private fun f(v: Double, digits: Int = 1): String = String.format(Locale.ROOT, "%.${digits}f", v)

@Composable
internal fun RefSection(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted,
        letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
internal fun RefText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
}

/** A rounded panel with thin lines between its rows. */
@Composable
internal fun RefPanel(rows: List<@Composable () -> Unit>) {
    val x = LocalExtra.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.field)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(x.line))
            Box(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) { row() }
        }
    }
}

/** Key in a fixed column, value next to it. */
@Composable
internal fun RefPair(key: String, value: String, keyWidth: Dp = 96.dp, mono: Boolean = true) {
    Row(verticalAlignment = Alignment.Top) {
        Text(key, fontFamily = if (mono) Mono else null, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(keyWidth))
        Text(value, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}

/** A number field for the calculators: digits, comma or dot. */
@Composable
internal fun NumField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier,
        label = { Text(label) }, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
    )
}

/** A calculator result: large value, small label. */
@Composable
internal fun Result(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted)
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
    }
}

/** Small selectable pills in a scrolling row. */
@Composable
internal fun Chips(items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val x = LocalExtra.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.height(38.dp).clip(RoundedCornerShape(19.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else x.field)
                    .selectable(selected = on, role = Role.RadioButton) { onPick(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Seconds since the epoch, ticking once a second while shown. */
@Composable
private fun nowSeconds(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    return now
}

// ---------- band plan, activity, beacons ----------

@Composable
internal fun PlanTab() {
    RefText(tr("План диапазонов IARU Region 1 (упрощённо): где работают телеграфом, цифровыми видами и голосом. Границы разрешённых в РФ полос — на вкладке «Частоты РФ»."))
    Reference.BAND_PLAN.forEach { (band, segments) ->
        RefSection(tr(band))
        RefPanel(segments.map { s -> { RefPair("${f(s.from, 0)}–${f(s.to, 0)}", tr(s.use), keyWidth = 130.dp) } })
    }
    RefSection(tr("Частоты активности, МГц"))
    RefPanel(Reference.ACTIVITY.map { (mode, freqs) -> { Column { Text(tr(mode), fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(tr(freqs), fontFamily = Mono, fontSize = 15.sp) } } })
    RefText(tr("Для цифровых видов — частота настройки трансивера (USB). 60 м и 6 м в РФ радиолюбителям не выделены."))

    RefSection(tr("Маяки NCDXF / IARU — сейчас в эфире"))
    val now = nowSeconds()
    val t = Instant.ofEpochSecond(now).atOffset(ZoneOffset.UTC)
    RefText(tr("Каждый из 18 маяков передаёт 10 секунд на каждой частоте, цикл 3 минуты. Время UTC: %s", DateTimeFormatter.ofPattern("HH:mm:ss").format(t)))
    RefPanel(Reference.NCDXF_FREQS.mapIndexed { band, fr ->
        {
            val (call, place) = Reference.NCDXF[Reference.ncdxfBeacon(now, band)]
            RefPair(f(fr, 3), "$call — ${tr(place)}", keyWidth = 90.dp)
        }
    })
    RefSection(tr("Порядок маяков"))
    RefPanel(Reference.NCDXF.chunked(2).map { pair -> { Text(pair.joinToString("   ·   ") { "${it.first} ${tr(it.second)}" }, fontSize = 15.sp) } })
}

// ---------- codes ----------

@Composable
internal fun CodesTab() {
    RefSection(tr("Q-коды"))
    RefPanel(Reference.Q_CODES.map { (c, m) -> { RefPair(c, tr(m), keyWidth = 64.dp) } })
    RefSection(tr("Сокращения CW"))
    RefPanel(Reference.CW_ABBR.map { (c, m) -> { RefPair(c, tr(m), keyWidth = 118.dp) } })
    RefSection(tr("RST: R — разборчивость"))
    RefPanel(Reference.RST_R.map { (c, m) -> { RefPair(c, tr(m), keyWidth = 36.dp) } })
    RefSection(tr("RST: S — сила сигнала"))
    RefPanel(Reference.RST_S.map { (c, m) -> { RefPair(c, tr(m), keyWidth = 36.dp) } })
    RefSection(tr("RST: T — тон (только CW и цифровые)"))
    RefPanel(Reference.RST_T.map { (c, m) -> { RefPair(c, tr(m), keyWidth = 36.dp) } })
}

// ---------- levels: W, dBm, dBi, EIRP, S-meter ----------

@Composable
internal fun LevelsTab() {
    RefSection(tr("Мощность ↔ dBm"))
    var watts by rememberSaveable { mutableStateOf("100") }
    var dbm by rememberSaveable { mutableStateOf("37") }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumField(tr("Мощность, Вт"), watts, Modifier.weight(1f)) { watts = it }
        NumField("dBm", dbm, Modifier.weight(1f)) { dbm = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        RfCalc.num(watts)?.takeIf { it > 0 }?.let {
            Result(tr("%s Вт", watts.trim()), "${f(RfCalc.wattsToDbm(it))} dBm · ${f(RfCalc.wattsToDbw(it))} dBW")
        }
    }
    RfCalc.num(dbm)?.let { Result(tr("%s dBm", dbm.trim()), tr("%s Вт", fmtWatts(RfCalc.dbmToWatts(it)))) }

    RefSection(tr("ЭИИМ (EIRP)"))
    var p by rememberSaveable { mutableStateOf("100") }
    var gain by rememberSaveable { mutableStateOf("6") }
    var loss by rememberSaveable { mutableStateOf("1.5") }
    var dbd by rememberSaveable { mutableStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumField(tr("Мощность, Вт"), p, Modifier.weight(1f)) { p = it }
        NumField(tr("Усиление"), gain, Modifier.weight(1f)) { gain = it }
        NumField(tr("Потери, дБ"), loss, Modifier.weight(1f)) { loss = it }
    }
    Chips(listOf("dBi", "dBd"), dbd) { dbd = it }
    val pw = RfCalc.num(p); val g = RfCalc.num(gain); val l = RfCalc.num(loss) ?: 0.0
    if (pw != null && pw > 0 && g != null) {
        val gi = if (dbd == 1) g + RfCalc.DBD_TO_DBI else g
        val e = RfCalc.eirpWatts(pw, gi, l)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Result(tr("ЭИИМ"), tr("%s Вт", fmtWatts(e)) + " · ${f(RfCalc.wattsToDbm(e))} dBm")
            Result(tr("ЭИМ (ERP)"), tr("%s Вт", fmtWatts(e / Math.pow(10.0, RfCalc.DBD_TO_DBI / 10))))
        }
    }
    RefText(tr("dBi — относительно изотропного излучателя, dBd — относительно полуволнового диполя: dBi = dBd + 2,15. ЭИИМ = мощность + усиление антенны − потери в кабеле; ЭИМ — то же относительно диполя (на 2,15 дБ меньше)."))

    RefSection(tr("Мощность — dBm — dBW"))
    RefPanel(listOf(0.1, 1.0, 5.0, 10.0, 25.0, 50.0, 100.0, 200.0, 500.0, 1000.0).map { w ->
        { RefPair(tr("%s Вт", fmtWatts(w)), "${f(RfCalc.wattsToDbm(w))} dBm   ${f(RfCalc.wattsToDbw(w))} dBW", keyWidth = 96.dp) }
    })

    RefSection(tr("S-метр"))
    RefText(tr("По рекомендации IARU: одна S-единица — 6 дБ; S9 = −73 dBm (50 мкВ на 50 Ом) ниже 30 МГц и −93 dBm (5 мкВ) выше."))
    val levels = (1..9).map { "S$it" to (it - 9) * 6.0 } + listOf("S9+10" to 10.0, "S9+20" to 20.0, "S9+40" to 40.0, "S9+60" to 60.0)
    // Two aligned columns: HF and VHF, each dBm and µV.
    @Composable
    fun sRow(name: String, a: String, b: String, head: Boolean = false) {
        val style = if (head) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge
        Row {
            Text(name, Modifier.width(64.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, style = style)
            Text(a, Modifier.weight(1f), fontFamily = if (head) null else Mono, style = style)
            Text(b, Modifier.weight(1f), fontFamily = if (head) null else Mono, style = style)
        }
    }
    RefPanel(listOf<@Composable () -> Unit>({ sRow("", tr("КВ: dBm · мкВ"), tr("УКВ: dBm · мкВ"), head = true) }) +
        levels.map { (name, rel) ->
            {
                val hf = -73.0 + rel; val vhf = -93.0 + rel
                sRow(name, "${f(hf, 0)} · ${fmtUv(RfCalc.dbmToMicrovolts(hf))}", "${f(vhf, 0)} · ${fmtUv(RfCalc.dbmToMicrovolts(vhf))}")
            }
        })
}

/** Watts without trailing zeros: 1000, 25, 5.01, 0.1, 0.0032. */
private fun fmtWatts(w: Double): String {
    if (w < 0.0001) return String.format(Locale.ROOT, "%.2e", w)
    val digits = when { w >= 100 -> 0; w >= 10 -> 1; w >= 1 -> 2; else -> 4 }
    return java.math.BigDecimal(w).setScale(digits, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
private fun fmtUv(uv: Double): String = when { uv >= 100 -> f(uv, 0); uv >= 1 -> f(uv, 1); else -> f(uv, 2) }

// ---------- cables and SWR ----------

@Composable
internal fun CablesTab() {
    RefSection(tr("Потери в кабеле"))
    val names = Reference.CABLES.map { it.name } + tr("Свой")
    var cable by rememberSaveable { mutableStateOf(Reference.CABLES.indexOfFirst { it.name == "RG-213" }) }
    var mhz by rememberSaveable { mutableStateOf("14.2") }
    var len by rememberSaveable { mutableStateOf("20") }
    var pw by rememberSaveable { mutableStateOf("100") }
    var own by rememberSaveable { mutableStateOf("5") }
    Chips(names, cable) { cable = it }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumField(tr("Частота, МГц"), mhz, Modifier.weight(1f)) { mhz = it }
        NumField(tr("Длина, м"), len, Modifier.weight(1f)) { len = it }
        NumField(tr("Мощность, Вт"), pw, Modifier.weight(1f)) { pw = it }
    }
    val custom = cable == Reference.CABLES.size
    if (custom) NumField(tr("Затухание вашего кабеля на этой частоте, дБ/100 м (из паспорта)"), own, Modifier.fillMaxWidth()) { own = it }
    val fr = RfCalc.num(mhz); val m = RfCalc.num(len); val w = RfCalc.num(pw)
    if (fr != null && fr > 0 && m != null && m >= 0) {
        val per100 = if (custom) RfCalc.num(own) else RfCalc.attenuationPer100m(Reference.CABLES[cable], fr)
        if (per100 != null) {
            val lossDb = per100 * m / 100
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Result(tr("Потери"), "${f(lossDb, 2)} " + tr("дБ"))
                if (w != null && w > 0) {
                    val out = RfCalc.powerAfterLoss(w, lossDb)
                    Result(tr("До антенны"), tr("%s Вт", fmtWatts(out)) + " (${f(100 * (1 - out / w), 0)} % " + tr("теряется") + ")")
                }
            }
        }
    }
    RefText(tr("Данные — сводная таблица DD1US (dd1us.de, апрель 2024) по паспортам производителей; между частотами — интерполяция. Реальный кабель может отличаться; потери растут от влаги и старения, а при КСВ выше 1 — ещё больше."))

    RefSection(tr("Затухание, дБ/100 м"))
    val x = LocalExtra.current
    // A wide table: it scrolls sideways inside its own box.
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.field).horizontalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row { Text(tr("Кабель"), Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium, color = x.muted)
                Text("v", Modifier.width(44.dp), style = MaterialTheme.typography.labelMedium, color = x.muted)
                Reference.CABLE_FREQS.forEach { Text(if (it < 100) f(it, if (it < 10) 1 else 0) else f(it, 0), Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium, color = x.muted) } }
            Reference.CABLES.forEach { c ->
                Row(Modifier.padding(top = 6.dp)) {
                    Text(c.name, Modifier.width(96.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(f(c.velocity, 2), Modifier.width(44.dp), fontFamily = Mono, fontSize = 15.sp)
                    Reference.CABLE_FREQS.forEach { fr2 -> Text(f(RfCalc.attenuationPer100m(c, fr2)), Modifier.width(56.dp), fontFamily = Mono, fontSize = 15.sp) }
                }
            }
        }
    }
    RefText(tr("Частоты в МГц; v — коэффициент укорочения (скорость волны в кабеле относительно света)."))

    RefSection(tr("КСВ"))
    var swr by rememberSaveable { mutableStateOf("1.5") }
    NumField(tr("КСВ"), swr, Modifier.fillMaxWidth()) { swr = it }
    RfCalc.num(swr)?.takeIf { it >= 1 }?.let {
        val r = RfCalc.swr(it)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Result(tr("Отражается"), "${f(r.reflected * 100)} %")
            Result(tr("Обратные потери"), if (r.returnLossDb.isInfinite()) "∞" else "${f(r.returnLossDb)} " + tr("дБ"))
            Result(tr("Потери рассогласования"), "${f(r.mismatchLossDb, 2)} " + tr("дБ"))
        }
    }
    RefPanel(Reference.SWR_ROWS.map { s ->
        { val r = RfCalc.swr(s); RefPair(f(s, 1), "${f(r.reflected * 100)} %   RL ${f(r.returnLossDb)} " + tr("дБ") + "   ${f(r.mismatchLossDb, 2)} " + tr("дБ"), keyWidth = 56.dp) }
    })
    RefText(tr("Отражается — доля мощности, вернувшейся к передатчику; RL — обратные потери; последнее число — потери рассогласования (без учёта потерь в кабеле)."))
}

// ---------- antennas ----------

@Composable
internal fun AntennasTab() {
    val bands = listOf("160" to "1.85", "80" to "3.65", "40" to "7.1", "30" to "10.12", "20" to "14.2", "17" to "18.12", "15" to "21.2", "12" to "24.94", "10" to "28.5", "2" to "145", "70" to "435")
    var mhz by rememberSaveable { mutableStateOf("14.2") }
    var k by rememberSaveable { mutableStateOf("0.95") }
    var vf by rememberSaveable { mutableStateOf("0.66") }
    Chips(bands.map { tr(if (it.first == "70") "70 см" else "${it.first} м") }, bands.indexOfFirst { it.second == mhz }) { mhz = bands[it].second }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumField(tr("Частота, МГц"), mhz, Modifier.weight(1f)) { mhz = it }
        NumField(tr("Укорочение провода"), k, Modifier.weight(1f)) { k = it }
    }
    val fr = RfCalc.num(mhz); val kk = RfCalc.num(k)
    if (fr != null && fr > 0 && kk != null && kk > 0) {
        val a = RfCalc.antenna(fr, kk)
        RefPanel(listOf(
            { RefPair("λ", "${f(a.wavelength, 2)} " + tr("м"), keyWidth = 150.dp, mono = false) },
            { RefPair(tr("Диполь λ/2"), "${f(a.dipole, 2)} " + tr("м") + " (" + tr("плечо") + " ${f(a.dipoleLeg, 2)} " + tr("м") + ")", keyWidth = 150.dp, mono = false) },
            { RefPair(tr("GP λ/4"), tr("штырь и противовесы по %s м", f(a.quarter, 2)), keyWidth = 150.dp, mono = false) },
            { RefPair(tr("Рамка 1λ"), "${f(a.loop, 2)} " + tr("м по периметру"), keyWidth = 150.dp, mono = false) },
        ))
        RefSection(tr("Четвертьволновый отрезок кабеля"))
        NumField(tr("Коэффициент укорочения кабеля"), vf, Modifier.fillMaxWidth()) { vf = it }
        Chips(listOf("0.66", "0.78", "0.81", "0.83", "0.85", "0.86"), listOf("0.66", "0.78", "0.81", "0.83", "0.85", "0.86").indexOf(vf)) { vf = listOf("0.66", "0.78", "0.81", "0.83", "0.85", "0.86")[it] }
        RfCalc.num(vf)?.takeIf { it in 0.3..1.0 }?.let { v ->
            Result(tr("λ/4 кабеля"), "${f(RfCalc.coaxQuarter(fr, v), 3)} " + tr("м") + "   (λ/2 — ${f(RfCalc.coaxQuarter(fr, v) * 2, 3)} " + tr("м") + ")")
        }
    }
    RefText(tr("λ = 299,79 / f. Укорочение провода 0,95 — типичная поправка на концевой эффект и толщину; точную длину подгоняют по КСВ. v кабеля: сплошной полиэтилен 0,66, вспененный 0,78–0,86 (см. вкладку «Кабели»)."))
}

// ---------- prefixes ----------

@Composable
internal fun PrefixTab(myPosition: LatLon?) {
    var q by rememberSaveable { mutableStateOf("") }
    // The country file is read while the callsign is being typed, not on the first letter on the main thread.
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Cty.load() } }
    OutlinedTextField(
        value = q, onValueChange = { q = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '/' } },
        modifier = Modifier.fillMaxWidth(), label = { Text(tr("Позывной или префикс")) }, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false, keyboardType = KeyboardType.Ascii),
        shape = RoundedCornerShape(12.dp),
    )
    val hit = remember(q) { if (q.isBlank()) null else Cty.lookup(q) }
    val region = remember(q) { if (q.isBlank()) null else RussianRegions.of(q) }
    if (q.isNotBlank()) {
        if (hit == null) RefText(tr("Страна по этому префиксу не найдена")) else {
            val e = hit.entity
            val rows = mutableListOf<@Composable () -> Unit>(
                { RefPair(tr("Страна"), e.name, mono = false) },
                { RefPair(tr("Префикс"), "${e.prefix}  (" + tr("совпало") + ": ${hit.matched})", mono = false) },
                { RefPair(tr("Континент"), e.continent, mono = false) },
                { RefPair(tr("Зоны"), "CQ ${region?.cqZone ?: hit.cqZone} · ITU ${region?.ituZone ?: hit.ituZone}", mono = false) },
            )
            if (myPosition != null) {
                val there = LatLon(e.lat, e.lon)
                rows += { RefPair(tr("От вас"), "${formatKm(Geo.distanceKm(myPosition, there))}, " + tr("азимут %s°", Geo.bearing(myPosition, there).toInt()) + " " + tr("(до центра страны)"), mono = false) }
            }
            if (region != null) {
                rows += { RefPair(tr("Регион"), region.name, mono = false) }
                rows += { RefPair("RDA", "${region.rda} · ${region.district}", mono = false) }
            }
            RefPanel(rows)
        }
    }
    RefText(tr("Страны DXCC — по файлу cty.dat (Jim Reisert AD1C, country-files.com); работает без интернета."))

    RefSection(tr("Регионы России в позывных"))
    RefText(tr("Цифра — округ, буква после цифры — субъект: RA3X… — Калужская область. По таблице СРР (srr.ru)."))
    val list = RussianRegions.ALL.filter { r ->
        q.isBlank() || r.name.contains(q, ignoreCase = true) || r.rda == q || r.blocks.any { it.startsWith(q.take(3)) } || RussianRegions.of(q) == r
    }
    RefPanel(list.map { r -> { RefPair(r.blocks.joinToString(" "), "${r.name} · ${r.rda}", keyWidth = 150.dp) } })
}

// ---------- propagation and time ----------

@Composable
internal fun PropagationTab() {
    RefSection(tr("SFI — индекс солнечного потока"))
    RefPanel(Reference.SFI.map { (k, v) -> { RefPair(k, tr(v), keyWidth = 80.dp) } })
    RefSection(tr("A-индекс (за сутки)"))
    RefPanel(Reference.A_INDEX.map { (k, v) -> { RefPair(k, tr(v), keyWidth = 80.dp) } })
    RefSection(tr("K-индекс (за 3 часа)"))
    RefPanel(Reference.K_INDEX.map { (k, v) -> { RefPair(k, tr(v), keyWidth = 80.dp) } })
    RefText(tr("Чем выше SFI и ниже A и K, тем лучше прохождение на верхних КВ. Индексы можно вписать в поля группы «Прохождение» карточки связи."))

    RefSection(tr("Время"))
    val now = nowSeconds()
    val t = Instant.ofEpochSecond(now)
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    Result("UTC", DateTimeFormatter.ofPattern("HH:mm:ss").format(t.atOffset(ZoneOffset.UTC)))
    RefPanel(Reference.RU_ZONES.map { (h, places) ->
        {
            Row(verticalAlignment = Alignment.Top) {
                Text("UTC+$h", Modifier.width(78.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(hm.format(t.atOffset(ZoneOffset.ofHours(h))), Modifier.width(62.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                Text(tr(places), Modifier.weight(1f), fontSize = 16.sp)
            }
        }
    })
    RefText(tr("В журнале всегда пишется время UTC. Летнего времени в России нет."))
}
