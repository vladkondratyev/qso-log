package ru.r3xed.qsolog.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.r3xed.qsolog.data.MorseTone
import ru.r3xed.qsolog.morse.MorseStore
import ru.r3xed.qsolog.morse.MorseText
import ru.r3xed.qsolog.morse.MorseTextItem
import ru.r3xed.qsolog.tr
import kotlin.math.roundToInt

/**
 * Plays [items] as a stream, character by character, from [from]: the speed can change on the way (the next character
 * takes it), and [position] says which character is sounding now — the one whose samples the speaker is playing.
 */
private class MorseTextSound(private val items: List<MorseTextItem>, from: Int, wpm: Int, private val onEnd: () -> Unit) {
    @Volatile var wpm = wpm
    @Volatile private var running = true
    private val track: AudioTrack? = try {
        val min = AudioTrack.getMinBufferSize(MorseTone.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(MorseTone.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(min, MorseTone.RATE / 5 * 2)) // ~0.2 s ahead: a new speed is heard soon
            .build()
    } catch (e: Exception) {
        null
    }

    /** Where each character starts in the stream, in samples: (sample, index in [items]). */
    private val marks = ArrayList<Pair<Long, Int>>()

    /** False when the sound could not be opened: nothing plays. */
    val ok get() = track != null

    init {
        track?.let { t ->
            t.play()
            kotlin.concurrent.thread(name = "morse-text") { run(t, from) }
        }
    }

    private fun run(t: AudioTrack, from: Int) {
        var written = 0L
        for (i in from until items.size) {
            if (!running) break
            val item = items[i]
            val w = wpm
            val dot = (MorseTone.RATE * 1.2 / w).toInt()
            val tone = item.code?.let { MorseTone.pcm(it, w, tail = false) } ?: ShortArray(0)
            // After a character the three-dot gap; a pause between words is four dots more.
            val pcm = tone.copyOf(tone.size + dot * (if (item.code == null) 4 else 3))
            synchronized(marks) { marks += written to i }
            var off = 0
            while (off < pcm.size && running) {
                val n = t.write(pcm, off, minOf(2048, pcm.size - off))
                if (n <= 0) break
                off += n
            }
            written += pcm.size
        }
        // Let the speaker finish what is written.
        while (running && t.playbackHeadPosition.toLong() and 0xFFFFFFFFL < written) Thread.sleep(20)
        val ended = running
        running = false
        runCatching { t.stop() }
        t.release()
        if (ended) onEnd()
    }

    /** The character sounding now (index in [items]), or null before the first. */
    fun position(): Int? {
        val t = track ?: return null
        val head = runCatching { t.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrNull() ?: return null
        synchronized(marks) { return marks.lastOrNull { it.first <= head }?.second }
    }

    fun pause() { runCatching { track?.pause() } }

    fun resume() { runCatching { track?.play() } }

    fun stop() {
        running = false
        // A paused track would hold the writing thread: playing on, then emptied, lets it see the stop.
        runCatching { track?.play(); track?.pause(); track?.flush() }
    }
}

/**
 * Reference → Морзе → «Текст»: any text pasted or typed, Latin and Cyrillic, played in Morse at the chosen speed. The
 * sounding character is highlighted in the text, which scrolls along, and a tape under it shows each character with its
 * signal. Text and speed are remembered.
 */
@Composable
fun MorseTextPlayer(store: MorseStore) {
    val x = LocalExtra.current
    val c = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(store.get("text_play") ?: "CQ CQ DE UA3DEMO UA3DEMO K") }
    var wpm by remember { mutableIntStateOf(store.get("text_wpm")?.toIntOrNull() ?: 18) }
    val items = remember(text) { MorseText.plan(text) }
    var sound by remember { mutableStateOf<MorseTextSound?>(null) }
    var paused by remember { mutableStateOf(false) }
    // The character sounding now, as an index in [items]; it stays shown after a pause or the end.
    var pos by remember { mutableStateOf<Int?>(null) }
    val playing = sound != null

    fun stop() {
        sound?.stop()
        sound = null
        paused = false
    }
    DisposableEffect(Unit) { onDispose { sound?.stop() } }
    fun play(from: Int) {
        stop()
        if (items.isEmpty()) return
        lateinit var s: MorseTextSound
        s = MorseTextSound(items, from.coerceIn(0, items.lastIndex), wpm) {
            // From the sound thread: the end of the text.
            android.os.Handler(android.os.Looper.getMainLooper()).post { if (sound === s) { sound = null; paused = false; pos = null } }
        }
        if (!s.ok) return
        sound = s
        pos = from
    }
    // Follow the speaker while playing.
    LaunchedEffect(sound, paused) {
        val s = sound ?: return@LaunchedEffect
        while (!paused) {
            s.position()?.let { pos = it }
            delay(30)
        }
    }

    RefText(tr("Вставьте или наберите текст — латиницей, кириллицей или вперемешку — и нажмите «Играть». Звучащий знак подсвечивается, текст прокручивается за ним, внизу — лента со знаками и их кодом. Нажмите на знак в ленте, чтобы играть с него. Знаки без кода Морзе пропускаются."))

    // While it plays the text is shown read-only with the sounding character marked; stopped, it can be edited.
    if (!playing) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; store.put("text_play", it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("Текст")) },
            minLines = 3, maxLines = 8,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono, fontSize = 18.sp),
            shape = RoundedCornerShape(12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                clipboard.getText()?.text?.let { text = it; store.put("text_play", it) }
            }) {
                Icon(Icons.Filled.ContentPaste, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Вставить из буфера"))
            }
            if (text.isNotEmpty()) TextButton(onClick = { text = ""; store.put("text_play", "") }) { Text(tr("Очистить")) }
        }
    } else {
        val at = pos?.let { items.getOrNull(it) }?.takeIf { it.code != null }?.index
        val scroll = rememberScrollState()
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        LaunchedEffect(at, layout) {
            val l = layout ?: return@LaunchedEffect
            val i = at ?: return@LaunchedEffect
            val line = l.getLineForOffset(i)
            // Keep the sounding line in the upper part of the box, with the line before it still in sight.
            scroll.animateScrollTo((l.getLineTop(line) - l.getLineBottom(0)).roundToInt().coerceAtLeast(0))
        }
        Box(
            Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, c.primary, RoundedCornerShape(12.dp))
                .verticalScroll(scroll).padding(12.dp),
        ) {
            Text(
                buildAnnotatedString {
                    append(text)
                    if (at != null) addStyle(SpanStyle(background = c.primary, color = c.onPrimary, fontWeight = FontWeight.Bold), at, at + 1)
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono, fontSize = 20.sp, lineHeight = 30.sp),
                onTextLayout = { layout = it },
            )
        }
    }

    MorseTape(items, pos) { i -> play(i) }

    // Speed: the next character takes it, also while playing.
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Скорость, слов в минуту"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("$wpm", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Slider(
            value = wpm.toFloat(),
            onValueChange = { v ->
                wpm = v.roundToInt()
                sound?.wpm = wpm
                store.put("text_wpm", wpm.toString())
            },
            valueRange = 5f..40f, steps = 34,
        )
        if (items.isNotEmpty()) {
            val secs = (MorseText.durationMs(items, wpm) + 999) / 1000
            Text(tr("Знаков: %s · звучит %s", items.count { it.code != null }, if (secs >= 60) tr("%s мин %s с", secs / 60, secs % 60) else tr("%s с", secs)), style = MaterialTheme.typography.bodyMedium, color = x.muted)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                val s = sound
                when {
                    s == null -> play(0)
                    paused -> { s.resume(); paused = false }
                    else -> { s.pause(); paused = true }
                }
            },
            enabled = items.isNotEmpty(),
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(if (playing && !paused) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (!playing) tr("Играть") else if (paused) tr("Дальше") else tr("Пауза"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = { stop(); pos = null },
            enabled = playing,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Stop, null)
            Spacer(Modifier.width(6.dp))
            Text(tr("Стоп"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The tape: each character with its signal, the sounding one marked and kept in the middle; a tap plays from there. */
@Composable
private fun MorseTape(items: List<MorseTextItem>, pos: Int?, onPick: (Int) -> Unit) {
    if (items.isEmpty()) return
    val x = LocalExtra.current
    val c = MaterialTheme.colorScheme
    val state = rememberLazyListState()
    LaunchedEffect(pos) { if (pos != null) state.animateScrollToItem((pos - 2).coerceAtLeast(0)) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(items) { i, item ->
            val on = i == pos
            Column(
                Modifier.widthIn(min = if (item.code == null) 18.dp else 44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) c.primary else if (item.code == null) c.background else x.field)
                    .clickable(onClickLabel = tr("Играть с этого знака")) { onPick(i) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ink = if (on) c.onPrimary else c.onSurface
                Text(if (item.code == null) " " else item.char.uppercase(), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ink)
                Text(
                    item.code?.map { if (it == '-') '−' else '·' }?.joinToString("") ?: "",
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (on) c.onPrimary else c.primary,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}
