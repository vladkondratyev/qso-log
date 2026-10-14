package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.Screen
import ru.r3xed.qsolog.cat.CatStatus
import ru.r3xed.qsolog.cat.short
import ru.r3xed.qsolog.tr

/**
 * The transceiver in the QSO card, when CAT is on: a green dot and "TRX 14 074.000 · USB · S7" while it answers,
 * a grey or red one with the reason when it does not. A tap opens the CAT settings.
 */
@Composable
fun RigBadge(vm: AppViewModel, modifier: Modifier = Modifier) {
    if (!vm.catConfig.enabled) return
    val x = LocalExtra.current
    val st = vm.catStatus
    val rig = vm.rig
    val bad = st is CatStatus.Failed || (st is CatStatus.Connected && st.silent)
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(x.field)
            .clickable(onClickLabel = tr("Настройки CAT")) { vm.openSettings(vm.screen.takeIf { it == Screen.Edit || it == Screen.Contest } ?: Screen.Log) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(9.dp).clip(CircleShape).background(if (rig != null) x.ok else if (bad) MaterialTheme.colorScheme.error else x.muted))
        Spacer(Modifier.width(7.dp))
        Text("TRX ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = x.muted)
        Text(
            rig?.short()?.ifBlank { null } ?: when {
                bad -> tr("нет связи")
                else -> tr("подключаюсь…")
            },
            fontFamily = if (rig != null) Mono else null, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
