package ru.r3xed.qsolog.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import ru.r3xed.qsolog.data.LatLon
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.data.Reference
import ru.r3xed.qsolog.tr

/**
 * Reference: amateur bands of the Russian Federation, Morse code (Latin and Cyrillic) and the spelling alphabets for
 * reading callsigns. Three tabs; the tables come from [Reference].
 */
@Composable
fun ReferencePane(onClose: () -> Unit, myPosition: LatLon? = null) {
    val x = LocalExtra.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад (Esc)"), Modifier.size(30.dp))
            }
            Text(tr("Справка"), style = MaterialTheme.typography.headlineSmall)
        }
        // Three tabs as a row of pills; the chosen one is filled.
        // Ten tabs: a row of pills that scrolls sideways.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                tr("Частоты РФ"), tr("План и активность"), tr("Морзе"), tr("Позывные"), tr("Коды"),
                tr("Уровни"), tr("Кабели"), tr("Антенны"), tr("Префиксы"), tr("Прохождение"),
            ).forEachIndexed { i, title ->
                val on = tab == i
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).padding(horizontal = 0.dp)
                        .background(if (on) MaterialTheme.colorScheme.primary else x.field)
                        .selectable(selected = on, role = Role.Tab) { tab = i }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Column(
            // Each tab starts at its top.
            Modifier.weight(1f).verticalScroll(remember(tab) { ScrollState(0) }).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tab) {
                0 -> BandsTable()
                1 -> PlanTab()
                2 -> MorseTables()
                3 -> PhoneticTable()
                4 -> CodesTab()
                5 -> LevelsTab()
                6 -> CablesTab()
                7 -> AntennasTab()
                8 -> PrefixTab(myPosition)
                else -> PropagationTab()
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RefHeading(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted,
        letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun RefNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
}

@Composable
private fun BandsTable() {
    val x = LocalExtra.current
    RefNote(tr("Полосы частот любительской службы в Российской Федерации по решению ГКРЧ от 15.07.2010 № 10-07-01 (с изменениями)."))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.field)) {
        Reference.BANDS_RU.forEachIndexed { i, b ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(x.line))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                Text(tr(b.name), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.width(76.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(b.range), fontFamily = Mono, fontSize = 17.sp)
                    if (b.note.isNotEmpty()) Text(tr(b.note), style = MaterialTheme.typography.bodyMedium, color = x.muted)
                }
            }
        }
    }
    RefNote(
        tr("Мощность и разрешённые участки зависят от категории радиостанции (1–4). Диапазоны 630 м, 60 м и 6 м этим решением радиолюбителям РФ не выделены (по 6 м, 50,08–50,35 МГц, был проект СРР 2022 года). Проверяйте актуальную редакцию и условия для своей категории на srr.ru.")
    )
}

/** Dots and dashes drawn to proportion: a dot is a round mark, a dash three times as long. */
@Composable
private fun Signal(code: String) {
    val c = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        code.forEach { s ->
            Box(Modifier.size(width = if (s == '.') 9.dp else 27.dp, height = 9.dp).clip(RoundedCornerShape(5.dp)).background(c))
        }
    }
}

/**
 * Morse signals in cells; a tap plays the signal. [chants]: the Russian learning chant under the letter, in grey;
 * [twins]: the Latin letter with the same signal, shown before it as "A/А".
 */
@Composable
private fun MorseGrid(items: List<Pair<String, String>>, perRow: Int = 2, chants: Map<String, String> = emptyMap(), twins: Map<String, String> = emptyMap()) {
    val x = LocalExtra.current
    var playing by remember { mutableStateOf<String?>(null) }
    items.chunked(perRow).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (ch, code) ->
                val chant = chants[ch]
                val twin = twins[ch]
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        // The cell lights up while its signal sounds.
                        .background(if (playing == ch) MaterialTheme.colorScheme.primaryContainer else x.field)
                        .clickable(onClickLabel = tr("Послушать")) { playing = ch; MorseSound.play(code) { if (playing == ch) playing = null } }
                        .padding(horizontal = 12.dp, vertical = if (chant != null) 8.dp else 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (twin.isNullOrEmpty()) Text(ch, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.width(44.dp))
                        else Text(
                            // The Latin twin first, in grey: "A/А".
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = x.muted)) { append("$twin/") }
                                append(ch)
                            },
                            fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(56.dp),
                        )
                        Signal(code)
                    }
                    // The chant under the whole cell: long ones fit.
                    if (chant != null) Text(
                        chant, style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1, softWrap = false,
                        // The longest (Ъ "ТВЁР-ДЫЙ-не-МЯГ-КИЙ") a little smaller, so it stays on one line on a phone.
                        fontSize = if (chant.length > 16) 11.sp else MaterialTheme.typography.bodySmall.fontSize,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MorseTables() {
    RefNote(tr("Точка — короткий сигнал, тире — втрое длиннее. Пауза между знаками буквы — одна точка, между буквами — три, между словами — семь. Нажмите на знак, чтобы услышать его (тон 700 Гц, 18 слов в минуту)."))
    RefHeading(tr("Латиница (международная)"))
    MorseGrid(Reference.MORSE_LATIN)
    RefHeading(tr("Кириллица (русская)"))
    MorseGrid(Reference.MORSE_CYRILLIC, chants = Reference.MORSE_CHANTS, twins = Reference.MORSE_LATIN_TWIN)
    RefNote(tr("Перед русской буквой — латинская с тем же сигналом (W/В, Q/Щ). Для Ч, Ш, Ъ, Э, Ю, Я пары в латинице нет, указаны расширенные знаки. Под буквой — напев, по которому её учат: слог на каждый знак, долгие слоги (тире) заглавными. Нажмите на букву, чтобы услышать."))
    RefHeading(tr("Цифры"))
    // Five or six elements: one per row, so a phone shows them whole.
    MorseGrid(Reference.MORSE_DIGITS, perRow = 1)
    RefHeading(tr("Знаки и служебные сигналы"))
    MorseGrid(Reference.MORSE_SIGNS, perRow = 1)
    RefNote(tr("AR — конец передачи, SK — конец связи, KN — ответ только вызванной станции, = (BT) — раздел."))
}

@Composable
private fun PhoneticTable() {
    val x = LocalExtra.current
    RefNote(tr("Как читать позывной голосом: по международному (ICAO) алфавиту или по русской таблице, которой пользуются радиолюбители. Например, RA3AB: Роман, Анна, Тройка, Анна, Борис."))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.field)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("", Modifier.width(44.dp))
            Text("ICAO", style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.weight(1f))
            Text(tr("По-русски"), style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.weight(1f))
        }
        (Reference.PHONETIC + Reference.PHONETIC_DIGITS).forEach { (ch, icao, ru) ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(x.line))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ch, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.width(44.dp))
                Text(icao, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(ru, fontSize = 17.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
